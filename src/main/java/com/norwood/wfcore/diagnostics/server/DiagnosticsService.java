package com.norwood.wfcore.diagnostics.server;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.diagnostics.DiagHeaderMessage;
import com.norwood.wfcore.diagnostics.DiagChunkMessage;
import com.norwood.wfcore.diagnostics.DiagNet;
import com.norwood.wfcore.diagnostics.DiagRequestMessage;

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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class DiagnosticsService {

    public static final DiagnosticsService INSTANCE = new DiagnosticsService();

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private record Pending(UUID playerId, long deadlineTick, int maxEdge, int quality, int maxBytes, int chunkSize) {}

    private final Long2ObjectMap<Pending> pending = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<FrameSession> sessions = new Long2ObjectOpenHashMap<>();
    private final Random random = new Random();

    private int intervalCounter;

    private DiagnosticsService() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_diag")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("capture")
                        .executes(this::captureAll)
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(this::captureTargets))));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WFCoreConfig.isDiagEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.sendSystemMessage(Component.literal(
                "This server employs fair-play OpenGL frame capture for anti-cheat. Only the in-game view is captured.")
                .withStyle(ChatFormatting.BLUE));
    }

    private int captureAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            requestCapture(server, player);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Requested " + players.size() + " frame(s)."), true);
        return players.size();
    }

    private int captureTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "target");
        MinecraftServer server = ctx.getSource().getServer();
        for (ServerPlayer player : players) {
            requestCapture(server, player);
        }
        int count = players.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Requested " + count + " frame(s)."), true);
        return count;
    }

    public void requestCapture(MinecraftServer server, ServerPlayer player) {
        if (!WFCoreConfig.isDiagEnabled()) {
            return;
        }
        int maxEdge = WFCoreConfig.getDiagMaxImageEdge();
        int quality = WFCoreConfig.getDiagJpegQuality();
        int maxBytes = WFCoreConfig.getDiagMaxImageBytes();
        int chunkSize = WFCoreConfig.getDiagChunkSize();
        long timeoutTicks = WFCoreConfig.getDiagCaptureTimeoutSeconds() * 20L;

        long nonce;
        do {
            nonce = random.nextLong();
        } while (nonce == 0L || pending.containsKey(nonce) || sessions.containsKey(nonce));

        pending.put(nonce, new Pending(player.getUUID(), server.getTickCount() + timeoutTicks, maxEdge, quality, maxBytes, chunkSize));
        DiagNet.sendToClient(player, new DiagRequestMessage(nonce, maxEdge, quality, maxBytes, chunkSize));
    }

    public void onHeader(ServerPlayer sender, DiagHeaderMessage msg) {
        if (sender == null) {
            return;
        }
        MinecraftServer server = sender.getServer();
        String name = sender.getGameProfile().getName();

        Pending expected = pending.remove(msg.nonce());
        if (expected == null || !expected.playerId().equals(sender.getUUID())) {
            notifyOperator(server, "unsolicited or mismatched frame header from " + name);
            return;
        }
        if (msg.totalLen() <= 0 || msg.totalLen() > expected.maxBytes()) {
            notifyOperator(server, "rejected frame from " + name + ": bad length " + msg.totalLen());
            return;
        }
        if (msg.chunkSize() != expected.chunkSize() || msg.chunkSize() <= 0) {
            notifyOperator(server, "rejected frame from " + name + ": chunk size " + msg.chunkSize() + " != requested " + expected.chunkSize());
            return;
        }
        if (msg.chunkCount() <= 0 || msg.chunkCount() > 1 + msg.totalLen() / Math.max(1, msg.chunkSize())) {
            notifyOperator(server, "rejected frame from " + name + ": bad chunk count " + msg.chunkCount());
            return;
        }
        long expectedChunks = ((long) msg.totalLen() + msg.chunkSize() - 1) / msg.chunkSize();
        if (expectedChunks != msg.chunkCount()) {
            notifyOperator(server, "rejected frame from " + name + ": chunk count " + msg.chunkCount() + " != expected " + expectedChunks);
            return;
        }
        if (msg.digest() == null || msg.digest().length != 32) {
            notifyOperator(server, "rejected frame from " + name + ": bad digest length");
            return;
        }

        FrameSession session = new FrameSession(sender.getUUID(), name,
                msg.fbWidth(), msg.fbHeight(), msg.imgWidth(), msg.imgHeight(),
                msg.totalLen(), msg.chunkSize(), msg.chunkCount(), msg.digest(),
                server.getTickCount() + WFCoreConfig.getDiagCaptureTimeoutSeconds() * 20L);
        sessions.put(msg.nonce(), session);
    }

    public void onChunk(ServerPlayer sender, DiagChunkMessage msg) {
        if (sender == null) {
            return;
        }
        FrameSession session = sessions.get(msg.nonce());
        if (session == null) {
            return;
        }
        MinecraftServer server = sender.getServer();
        if (!session.playerId.equals(sender.getUUID())) {
            sessions.remove(msg.nonce());
            notifyOperator(server, "rejected frame from " + sender.getGameProfile().getName() + ": chunk sender mismatch");
            return;
        }
        if (!session.accept(msg.index(), msg.data())) {
            sessions.remove(msg.nonce());
            notifyOperator(server, "rejected frame from " + session.username + ": malformed chunk " + msg.index());
            return;
        }
        if (session.complete()) {
            sessions.remove(msg.nonce());
            finalizeSession(server, session);
        }
    }

    private void finalizeSession(MinecraftServer server, FrameSession session) {
        byte[] jpeg = session.data();
        FrameVerifier.Result result = FrameVerifier.verify(session, jpeg);
        if (!result.ok()) {
            notifyOperator(server, "frame from " + session.username + " failed verification: " + result.reason());
            writeImage(server, "flagged", session.username, jpeg);
            return;
        }
        Path saved = writeImage(server, null, session.username, jpeg);
        if (saved == null) {
            notifyOperator(server, "frame from " + session.username + " verified but could not be written to disk");
        }
    }

    private Path writeImage(MinecraftServer server, String subdir, String username, byte[] jpeg) {
        try {
            Path base = server.getServerDirectory().toPath().resolve("screenshots");
            if (subdir != null) {
                base = base.resolve(subdir);
            }
            Files.createDirectories(base);
            String stamp = LocalDateTime.now().format(STAMP);
            String file = sanitize(username) + "_" + stamp + ".jpg";
            Path out = base.resolve(file);
            Files.write(out, jpeg);
            return out;
        } catch (IOException e) {
            WFCore.LOGGER.warn("[wfcore-diag] failed to write frame for {}: {}", username, e.toString());
            return null;
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        expire(server);
        scheduleAuto(server);
    }

    private void expire(MinecraftServer server) {
        long now = server.getTickCount();

        if (!pending.isEmpty()) {
            List<Long> due = new ArrayList<>();
            for (Long2ObjectMap.Entry<Pending> entry : pending.long2ObjectEntrySet()) {
                if (now > entry.getValue().deadlineTick()) {
                    due.add(entry.getLongKey());
                }
            }
            for (long key : due) {
                Pending p = pending.remove(key);
                ServerPlayer player = server.getPlayerList().getPlayer(p.playerId());
                if (player != null) {
                    notifyOperator(server, "player " + player.getGameProfile().getName() + " did not return a requested frame in time");
                }
            }
        }

        if (!sessions.isEmpty()) {
            List<Long> due = new ArrayList<>();
            for (Long2ObjectMap.Entry<FrameSession> entry : sessions.long2ObjectEntrySet()) {
                if (now > entry.getValue().deadlineTick) {
                    due.add(entry.getLongKey());
                }
            }
            for (long key : due) {
                FrameSession s = sessions.remove(key);
                notifyOperator(server, "frame from " + s.username + " arrived incomplete before timeout");
            }
        }
    }

    private void scheduleAuto(MinecraftServer server) {
        if (!WFCoreConfig.isDiagEnabled()) {
            return;
        }
        int intervalSeconds = WFCoreConfig.getDiagAutoIntervalSeconds();
        if (intervalSeconds <= 0) {
            return;
        }
        if (++intervalCounter < intervalSeconds * 20) {
            return;
        }
        intervalCounter = 0;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        ServerPlayer target = players.get(random.nextInt(players.size()));
        requestCapture(server, target);
    }

    private void notifyOperator(MinecraftServer server, String message) {
        WFCore.LOGGER.warn("[wfcore-diag] {}", message);
        if (server == null) {
            return;
        }
        Component line = Component.literal("[wfcore] " + message).withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(3)) {
                player.sendSystemMessage(line);
            }
        }
    }
}
