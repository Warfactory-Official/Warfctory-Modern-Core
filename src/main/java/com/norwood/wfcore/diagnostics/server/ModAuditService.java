package com.norwood.wfcore.diagnostics.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * Server half of the soft client-mod integrity audit. On join it asks the client (via {@link DiagNet}) to hash
 * its mods-folder jars, then matches the reply against {@code config/wfcore-modmanifest.json}. Unknown or modified
 * jars are FLAGGED - logged, shown to online operators, and posted to a Discord webhook if configured - but the
 * player is never kicked (a newer legitimate build is plausible; an admin decides). Hard mod validation stays with
 * the anticheat; this layer covers the client-only mods the anticheat is told to ignore.
 */
public final class ModAuditService {

    public static final ModAuditService INSTANCE = new ModAuditService();

    private static final String MANIFEST_FILE = "wfcore-modmanifest.json";

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

    /** fileName -> lower-case sha-256 hex, loaded lazily and reloadable via command; empty = audit inactive. */
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
            // No manifest on disk -> nothing to compare against. Fail open (do not flag) but note it once.
            WFCore.LOGGER.warn("[wfcore-modaudit] no {} on the server; mod report from {} not checked.",
                    MANIFEST_FILE, name);
            return;
        }

        // Client-reported fileName -> hex.
        Map<String, String> reported = new LinkedHashMap<>();
        for (ModReportMessage.Entry entry : msg.entries()) {
            if (entry.fileName() != null && entry.sha256() != null && entry.sha256().length == 32) {
                reported.put(entry.fileName(), toHex(entry.sha256()));
            }
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
                JsonObject payload = new JsonObject();
                payload.addProperty("content", body.toString());
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
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
        Path file = FMLPaths.CONFIGDIR.get().resolve(MANIFEST_FILE);
        Map<String, String> out = new HashMap<>();
        try {
            if (!Files.isRegularFile(file)) {
                return out;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return out;
            }
            JsonObject obj = root.getAsJsonObject();
            JsonObject mods = obj.has("mods") && obj.get("mods").isJsonObject()
                    ? obj.getAsJsonObject("mods")
                    : obj;
            for (Map.Entry<String, JsonElement> e : mods.entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    out.put(e.getKey(), e.getValue().getAsString().trim().toLowerCase());
                }
            }
            WFCore.LOGGER.info("[wfcore-modaudit] loaded {} manifest entries from {}", out.size(), MANIFEST_FILE);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore-modaudit] failed to read {}: {}", MANIFEST_FILE, t.toString());
        }
        return out;
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
        ctx.getSource().sendSuccess(() -> Component.literal("Reloaded mod manifest: " + reloaded.size() + " entr(ies)."), true);
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

    /** Neutralise Discord markdown / mention control chars in untrusted file names. */
    private static String escape(String s) {
        return s.replaceAll("[`*_~@|<>]", "");
    }
}
