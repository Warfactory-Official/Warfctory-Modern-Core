package com.norwood.wfcore.diagnostics.server;

import com.mojang.brigadier.context.CommandContext;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.diagnostics.DiagNet;
import com.norwood.wfcore.diagnostics.ModListRequestMessage;
import com.norwood.wfcore.diagnostics.ModReportMessage;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class ModAuditService {

    public static final ModAuditService INSTANCE = new ModAuditService();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ExecutorService WEBHOOK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wfcore-modaudit-webhook");
        t.setDaemon(true);
        return t;
    });

    private record Pending(UUID playerId, long deadlineTick) {}

    private final Long2ObjectMap<Pending> pending = new Long2ObjectOpenHashMap<>();
    private final Random random = new Random();

    /** fileName -> lower-case sha-256 hex, scanned lazily from mods/ + client_mods/ and reloadable via command. */
    private volatile Map<String, String> manifest;

    private ModAuditService() {}

    // ---------------------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_modaudit")
                .requires(source -> source.hasPermission(3))
                .executes(this::commandAudit)
                .then(Commands.literal("reload").executes(this::commandReload)));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WFCoreConfig.isModAuditEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        requestReport(player);
    }

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
            ServerPlayer player = server.getPlayerList().getPlayer(p.playerId());
            if (player != null) {
                flag(server, player.getGameProfile().getName(),
                        List.of("did not return a mod report within the timeout"));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Request / verify
    // ---------------------------------------------------------------------------------------------------------

    public void requestReport(ServerPlayer player) {
        if (!WFCoreConfig.isModAuditEnabled() || player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        long timeoutTicks = (long) WFCoreConfig.getModAuditTimeoutSeconds() * 20L;
        long nonce;
        do {
            nonce = random.nextLong();
        } while (nonce == 0L || pending.containsKey(nonce));
        pending.put(nonce, new Pending(player.getUUID(), server.getTickCount() + timeoutTicks));
        DiagNet.sendModListRequest(player, new ModListRequestMessage(nonce));
    }

    public void onReport(ServerPlayer sender, ModReportMessage msg) {
        if (sender == null || !WFCoreConfig.isModAuditEnabled()) {
            return;
        }
        MinecraftServer server = sender.getServer();
        String name = sender.getGameProfile().getName();

        Pending expected = pending.remove(msg.nonce());
        if (expected == null || !expected.playerId().equals(sender.getUUID())) {
            flag(server, name, List.of("sent an unsolicited or mismatched mod report"));
            return;
        }

        Map<String, String> mf = manifest();
        if (mf.isEmpty()) {
            WFCore.LOGGER.warn("[wfcore-modaudit] reference hash table is empty; mod report from {} not checked.", name);
            return;
        }

        // Client-reported fileName -> hex.
        Map<String, String> reported = new LinkedHashMap<>();
        for (ModReportMessage.Entry entry : msg.entries()) {
            if (entry.fileName() != null && entry.sha256() != null && entry.sha256().length == 32) {
                reported.put(entry.fileName(), toHex(entry.sha256()));
            }
        }

        if (reported.isEmpty()) {
            flag(server, name, List.of("returned no usable mod entries"));
            return;
        }

        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, String> e : reported.entrySet()) {
            String expectedHex = mf.get(e.getKey());
            if (expectedHex == null) {
                findings.add("UNKNOWN " + e.getKey() + " (not in manifest; added or newer build)");
            } else if (!expectedHex.equalsIgnoreCase(e.getValue())) {
                findings.add("MODIFIED " + e.getKey() + " (hash mismatch)");
            }
        }
        if (WFCoreConfig.isModAuditFlagMissing()) {
            for (String expectedName : mf.keySet()) {
                if (!reported.containsKey(expectedName)) {
                    findings.add("MISSING " + expectedName + " (in manifest, not on client)");
                }
            }
        }

        if (findings.isEmpty()) {
            WFCore.LOGGER.info("[wfcore-modaudit] {} passed mod audit ({} jars checked).", name, reported.size());
            return;
        }
        flag(server, name, findings);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Flagging (soft only - log + operators + optional Discord webhook, never a kick)
    // ---------------------------------------------------------------------------------------------------------

    private void flag(MinecraftServer server, String username, List<String> findings) {
        String header = "mod audit flagged " + username + ": " + findings.size() + " item(s)";
        WFCore.LOGGER.warn("[wfcore-modaudit] {}", header);
        for (String finding : findings) {
            WFCore.LOGGER.warn("[wfcore-modaudit]   - {}", finding);
        }

        if (server != null) {
            Component line = Component.literal("[wfcore] " + header + " (see server log)")
                    .withStyle(ChatFormatting.RED);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.hasPermissions(3)) {
                    player.sendSystemMessage(line);
                }
            }
        }

        postWebhook(username, header, findings);
    }

    private void postWebhook(String username, String header, List<String> findings) {
        String url = WFCoreConfig.getModAuditWebhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        WEBHOOK.submit(() -> {
            try {
                StringBuilder body = new StringBuilder();
                body.append("**").append(escape(header)).append("**");
                int shown = 0;
                for (String finding : findings) {
                    if (body.length() > 1800) {
                        body.append("\n… (+").append(findings.size() - shown).append(" more)");
                        break;
                    }
                    body.append("\n• ").append(escape(finding));
                    shown++;
                }
                String payload = "{\"content\":" + jsonString(body.toString()) + "}";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() / 100 != 2) {
                    WFCore.LOGGER.warn("[wfcore-modaudit] webhook returned HTTP {} for {}", response.statusCode(), username);
                }
            } catch (Throwable t) {
                WFCore.LOGGER.warn("[wfcore-modaudit] failed to post webhook for {}: {}", username, t.toString());
            }
        });
    }

    // ---------------------------------------------------------------------------------------------------------
    // Manifest
    // ---------------------------------------------------------------------------------------------------------

    private Map<String, String> manifest() {
        Map<String, String> cached = manifest;
        if (cached == null) {
            cached = loadManifest();
            manifest = cached;
        }
        return cached;
    }

    private Map<String, String> loadManifest() {
        Map<String, String> out = new HashMap<>();
        Path gameDir = FMLPaths.GAMEDIR.get();
        int shared = scanJarDir(gameDir.resolve("mods"), out);
        int clientOnly = scanJarDir(gameDir.resolve("client_mods"), out);
        WFCore.LOGGER.info("[wfcore-modaudit] hashed {} shared + {} client-only jar(s) as reference",
                shared, clientOnly);
        return out;
    }

    private static int scanJarDir(Path dir, Map<String, String> out) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String hex = sha256hex(p);
                if (hex != null) {
                    out.put(p.getFileName().toString(), hex);
                    count++;
                }
            }
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore-modaudit] failed to scan {}: {}", dir, t.toString());
        }
        return count;
    }

    private static String sha256hex(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            int read;
            while ((read = in.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
            return toHex(md.digest());
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore-modaudit] could not hash {}: {}", file, t.toString());
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------------------

    private int commandAudit(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            requestReport(player);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Requested mod reports from " + players.size() + " player(s)."), true);
        return players.size();
    }

    private int commandReload(CommandContext<CommandSourceStack> ctx) {
        Map<String, String> reloaded = loadManifest();
        manifest = reloaded;
        ctx.getSource().sendSuccess(() -> Component.literal("Rescanned mods/ + client_mods/: " + reloaded.size() + " jar(s)."), true);
        return reloaded.size();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replaceAll("[`*_~@|<>]", "");
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }
}
