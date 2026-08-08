package com.norwood.wfcore.diagnostics.server;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.diagnostics.ClassDumpCatalogMessage;
import com.norwood.wfcore.diagnostics.ClassDumpChunkMessage;
import com.norwood.wfcore.diagnostics.ClassDumpRequestMessage;
import com.norwood.wfcore.diagnostics.ClassDumpViewChunkMessage;
import com.norwood.wfcore.diagnostics.ClassDumpViewRequestMessage;
import com.norwood.wfcore.diagnostics.DiagNet;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


public final class ClassDumpService {

    public static final ClassDumpService INSTANCE = new ClassDumpService();

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int PERMISSION = 3;

    private static final int MAX_INFLATED_BYTES = 128 * 1024 * 1024;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wfcore-classdump-io");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private record Pending(UUID playerId, String username, long deadlineTick) {}

    private static final class Assembler {
        final byte[][] slices;
        int remaining;
        Assembler(int parts) {
            this.slices = new byte[parts][];
            this.remaining = parts;
        }
    }

    private final Long2ObjectMap<Pending> pending = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<Assembler> assemblers = new Long2ObjectOpenHashMap<>();
    private final Random random = new Random();

    private ClassDumpService() {}
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_classdump")
                .requires(source -> source.hasPermission(PERMISSION))
                .then(Commands.literal("capture")
                        .executes(this::captureAll)
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(this::captureTargets)))
                .then(Commands.literal("view")
                        .executes(this::openViewer)));
    }

    private int captureAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            requestDump(server, player);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Requested class dumps from " + players.size() + " player(s)."), true);
        return players.size();
    }

    private int captureTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "target");
        MinecraftServer server = ctx.getSource().getServer();
        for (ServerPlayer player : players) {
            requestDump(server, player);
        }
        int count = players.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Requested " + count + " class dump(s)."), true);
        return count;
    }

    private int openViewer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        sendCatalog(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Opening the class-dump viewer..."), false);
        return 1;
    }
    public void requestDump(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) {
            return;
        }
        int maxBytes = WFCoreConfig.getClassDumpMaxUploadBytes();
        boolean includePlatform = WFCoreConfig.isClassDumpIncludePlatformModules();
        boolean includeDefaultPackage = WFCoreConfig.isClassDumpIncludeDefaultPackage();
        long timeoutTicks = WFCoreConfig.getClassDumpTimeoutSeconds() * 20L;
        long nonce;
        do {
            nonce = random.nextLong();
        } while (nonce == 0L || pending.containsKey(nonce) || assemblers.containsKey(nonce));
        pending.put(nonce, new Pending(player.getUUID(), player.getGameProfile().getName(),
                server.getTickCount() + timeoutTicks));
        DiagNet.sendClassDumpRequest(player,
                new ClassDumpRequestMessage(nonce, maxBytes, includePlatform, includeDefaultPackage));
    }

    public void onUploadChunk(ServerPlayer sender, ClassDumpChunkMessage msg) {
        if (sender == null) {
            return;
        }
        MinecraftServer server = sender.getServer();
        String name = sender.getGameProfile().getName();
        long nonce = msg.nonce();

        if (msg.parts() <= 0) {
            pending.remove(nonce);
            assemblers.remove(nonce);
            notifyOperator(server, "class dump from " + name + " was malformed");
            return;
        }

        Pending expected = pending.get(nonce);
        boolean known = expected != null || assemblers.containsKey(nonce);
        if (!known) {
            notifyOperator(server, "unsolicited class dump from " + name);
            return;
        }
        if (expected != null && !expected.playerId().equals(sender.getUUID())) {
            pending.remove(nonce);
            assemblers.remove(nonce);
            notifyOperator(server, "class dump sender mismatch from " + name);
            return;
        }

        Assembler asm = assemblers.get(nonce);
        if (asm == null) {
            asm = new Assembler(msg.parts());
            assemblers.put(nonce, asm);
        }
        if (asm.slices.length != msg.parts()) {
            pending.remove(nonce);
            assemblers.remove(nonce);
            notifyOperator(server, "inconsistent class dump from " + name);
            return;
        }
        long total = 0;
        for (byte[] s : asm.slices) {
            total += s == null ? 0 : s.length;
        }
        if (total + msg.blob().length > WFCoreConfig.getClassDumpMaxUploadBytes()) {
            pending.remove(nonce);
            assemblers.remove(nonce);
            notifyOperator(server, "class dump from " + name + " exceeded the size cap");
            return;
        }
        if (asm.slices[msg.part()] == null) {
            asm.slices[msg.part()] = msg.blob();
            asm.remaining--;
        }
        if (asm.remaining > 0) {
            return;
        }

        pending.remove(nonce);
        assemblers.remove(nonce);
        byte[] gz = concat(asm.slices);
        saveAsync(server, name, gz);
    }

    private void saveAsync(MinecraftServer server, String username, byte[] gz) {
        if (server == null) {
            return;
        }
        IO.submit(() -> {
            byte[] text = inflate(gz, MAX_INFLATED_BYTES);
            if (text == null) {
                server.execute(() -> notifyOperator(server, "class dump from " + username + " was corrupt or oversized"));
                return;
            }
            Path saved = write(server, username, text);
            int classes = countClasses(text);
            server.execute(() -> {
                if (saved == null) {
                    notifyOperator(server, "class dump from " + username + " could not be written to disk");
                } else {
                    notifyOperator(server, "saved class dump for " + username + " (" + classes
                            + " classes, " + (text.length / 1024) + " KiB) -> classloader/" + saved.getFileName());
                }
            });
        });
    }

    private Path write(MinecraftServer server, String username, byte[] text) {
        try {
            Path dir = ClassDumpCatalog.directory(server);
            Files.createDirectories(dir);
            String file = sanitize(username) + "_" + LocalDateTime.now().format(STAMP) + ".txt";
            Path out = dir.resolve(file);
            Files.write(out, text);
            return out;
        } catch (Exception e) {
            WFCore.LOGGER.warn("[wfcore-classdump] failed to write dump for {}: {}", username, e.toString());
            return null;
        }
    }
    public void onListRequest(ServerPlayer sender) {
        if (sender == null || !sender.hasPermissions(PERMISSION)) {
            return;
        }
        sendCatalog(sender);
    }

    private void sendCatalog(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        IO.submit(() -> {
            ClassDumpCatalog.Scan scan = ClassDumpCatalog.scan(server);
            server.execute(() -> {
                if (player.hasDisconnected()) {
                    return;
                }
                DiagNet.sendClassDumpCatalog(player, new ClassDumpCatalogMessage(scan.entries(), scan.truncated()));
            });
        });
    }

    public void onViewRequest(ServerPlayer sender, ClassDumpViewRequestMessage msg) {
        if (sender == null || !sender.hasPermissions(PERMISSION)) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return;
        }
        int maxBytes = WFCoreConfig.getClassDumpMaxUploadBytes();
        IO.submit(() -> {
            byte[] text = ClassDumpCatalog.read(server, msg.fileName(), maxBytes);
            if (text == null) {
                WFCore.LOGGER.warn("[wfcore-classdump] {} requested an unreadable dump: {}",
                        sender.getGameProfile().getName(), msg.fileName());
                return;
            }
            byte[] gz = deflate(text);
            if (gz == null) {
                return;
            }
            int chunk = ClassDumpChunkMessage.MAX_BLOB;
            int parts = Math.max(1, (gz.length + chunk - 1) / chunk);
            if (parts > ClassDumpChunkMessage.MAX_PARTS) {
                WFCore.LOGGER.warn("[wfcore-classdump] dump {} too large to stream ({} parts)", msg.fileName(), parts);
                return;
            }
            final int fparts = parts;
            final byte[] fgz = gz;
            server.execute(() -> {
                if (sender.hasDisconnected()) {
                    return;
                }
                for (int i = 0; i < fparts; i++) {
                    int from = i * chunk;
                    int to = Math.min(from + chunk, fgz.length);
                    byte[] slice = new byte[to - from];
                    System.arraycopy(fgz, from, slice, 0, slice.length);
                    DiagNet.sendClassDumpViewChunk(sender, new ClassDumpViewChunkMessage(msg.requestId(), i, fparts, slice));
                }
            });
        });
    }

    // ------------------------------------------------------------------------------------------------------
    // Timeouts
    // ------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long now = server.getTickCount();
        List<Long> due = new ArrayList<>();
        for (Long2ObjectMap.Entry<Pending> entry : pending.long2ObjectEntrySet()) {
            if (now > entry.getValue().deadlineTick()) {
                due.add(entry.getLongKey());
            }
        }
        for (long key : due) {
            Pending p = pending.remove(key);
            assemblers.remove(key);
            notifyOperator(server, "player " + p.username() + " did not return a class dump in time");
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------

    private static byte[] concat(byte[][] slices) {
        int total = 0;
        for (byte[] s : slices) {
            total += s == null ? 0 : s.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] s : slices) {
            if (s != null) {
                System.arraycopy(s, 0, out, pos, s.length);
                pos += s.length;
            }
        }
        return out;
    }

    /** Inflates untrusted client gzip, refusing anything that decompresses past {@code maxOut} (zip-bomb guard). */
    private static byte[] inflate(byte[] gz, int maxOut) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxOut, gz.length * 4));
            byte[] buf = new byte[1 << 15];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > maxOut) {
                    return null;
                }
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] deflate(byte[] text) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, text.length / 6));
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                gz.write(text);
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int countClasses(byte[] text) {
        int count = 0;
        for (String line : new String(text, StandardCharsets.UTF_8).split("\n")) {
            if (!line.isEmpty() && line.charAt(0) != '#') {
                count++;
            }
        }
        return count;
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private void notifyOperator(MinecraftServer server, String message) {
        WFCore.LOGGER.info("[wfcore-classdump] {}", message);
        if (server == null) {
            return;
        }
        Component line = Component.literal("[wfcore] " + message).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(PERMISSION)) {
                player.sendSystemMessage(line);
            }
        }
    }
}
