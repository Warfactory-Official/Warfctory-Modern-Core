package com.norwood.wfcore.diagnostics.server;

import com.mojang.brigadier.context.CommandContext;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.diagnostics.DiagNet;
import com.norwood.wfcore.diagnostics.ModInventory;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;


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

    /** Fixed multipart boundary; the report text never contains it. */
    private static final String BOUNDARY = "----wfcoreModAuditBoundaryqp8Vz2Lf";

    private record Pending(UUID playerId, long deadlineTick) {}

    /** Partial gzipped report being reassembled across chunks for one nonce. */
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
            assemblers.remove(key);
            ServerPlayer player = server.getPlayerList().getPlayer(p.playerId());
            if (player != null) {
                flag(server, player.getGameProfile().getName(),
                        List.of("did not return a mod report within the timeout"), null, null);
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
        DiagNet.sendModListRequest(player, new ModListRequestMessage(nonce, WFCoreConfig.getModAuditCheatClasses()));
    }

    /** Accumulates a report's gzip slices; verifies once the last slice for the nonce arrives. */
    public void onReportChunk(ServerPlayer sender, ModReportMessage msg) {
        if (sender == null || !WFCoreConfig.isModAuditEnabled()) {
            return;
        }
        MinecraftServer server = sender.getServer();
        String name = sender.getGameProfile().getName();
        long nonce = msg.nonce();

        if (msg.parts() <= 0) {
            pending.remove(nonce);
            assemblers.remove(nonce);
            flag(server, name, List.of("sent a malformed mod report"), null, null);
            return;
        }

        Pending expected = pending.get(nonce);
        boolean known = expected != null || assemblers.containsKey(nonce);
        if (!known) {
            flag(server, name, List.of("sent an unsolicited or mismatched mod report"), null, null);
            return;
        }
        if (expected != null && !expected.playerId().equals(sender.getUUID())) {
            pending.remove(nonce);
            assemblers.remove(nonce);
            flag(server, name, List.of("sent a mod report for another player's request"), null, null);
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
            flag(server, name, List.of("sent an inconsistent mod report"), null, null);
            return;
        }
        if (asm.slices[msg.part()] == null) {
            asm.slices[msg.part()] = msg.blob();
            asm.remaining--;
        }
        if (asm.remaining > 0) {
            return; // wait for the rest
        }

        pending.remove(nonce);
        assemblers.remove(nonce);
        String text = inflate(asm.slices);
        if (text == null) {
            flag(server, name, List.of("sent an unreadable (corrupt) mod report"), null, null);
            return;
        }
        verify(server, sender, name, text);
    }

    private void verify(MinecraftServer server, ServerPlayer sender, String name, String text) {
        List<String[]> clientJars = new ArrayList<>();   // {hex, fileName, mods}
        Map<String, String> reported = new LinkedHashMap<>(); // fileName -> hex (H lines only)
        List<String> findings = new ArrayList<>();

        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "H" -> {
                    if (f.length >= 4) {
                        clientJars.add(new String[]{f[1], f[2], f[3]});
                        reported.put(f[2], f[1]);
                    }
                }
                case "X" -> {
                    if (f.length >= 4) {
                        clientJars.add(new String[]{f[1], f[2], f[3] + " [loaded outside mods/]"});
                        findings.add("OUTSIDE " + f[2] + " (mod loaded from outside mods/: " + f[3] + ")");
                    }
                }
                case "C" -> {
                    if (f.length >= 2) {
                        findings.add("CHEAT-CLASS " + f[1] + " (signature present on client)");
                    }
                }
                default -> { /* forward-compat: ignore unknown record types */ }
            }
        }

        Map<String, String> mf = manifest();
        if (reported.isEmpty()) {
            findings.add("returned no usable mod entries");
        } else if (mf.isEmpty()) {
            WFCore.LOGGER.warn("[wfcore-modaudit] reference hash table is empty; integrity of {}'s report not checked.", name);
        } else {
            for (Map.Entry<String, String> e : reported.entrySet()) {
                String expectedHex = mf.get(e.getKey());
                if (expectedHex == null) {
                    findings.add("UNKNOWN " + e.getKey() + " (not in server manifest; added or newer build)");
                } else if (!expectedHex.equalsIgnoreCase(e.getValue())) {
                    findings.add("MODIFIED " + e.getKey() + " (hash mismatch)");
                }
            }
            if (WFCoreConfig.isModAuditFlagMissing()) {
                for (String expectedName : mf.keySet()) {
                    if (!reported.containsKey(expectedName)) {
                        findings.add("MISSING " + expectedName + " (in server manifest, not on client)");
                    }
                }
            }
        }

        List<String> serverInv = serverInventoryLines();

        if (findings.isEmpty()) {
            WFCore.LOGGER.info("[wfcore-modaudit] {} passed mod audit ({} jars checked).", name, reported.size());
        }
        String uuid = sender != null ? sender.getUUID().toString() : "?";
        report(server, name, uuid, findings, clientJars, serverInv);
    }

    private void flag(MinecraftServer server, String username, List<String> findings,
                      List<String[]> clientJars, List<String> serverInv) {
        report(server, username, "?", findings, clientJars, serverInv);
    }

    private void report(MinecraftServer server, String username, String uuid, List<String> findings,
                        List<String[]> clientJars, List<String> serverInv) {
        boolean hasFindings = !findings.isEmpty();

        if (hasFindings) {
            WFCore.LOGGER.warn("[wfcore-modaudit] flagged {}: {} item(s)", username, findings.size());
            for (String finding : findings) {
                WFCore.LOGGER.warn("[wfcore-modaudit]   - {}", finding);
            }
        }

        if (hasFindings && server != null && WFCoreConfig.isModAuditNotifyOperators()) {
            Component line = Component.literal(
                    "[wfcore] mod audit: " + username + " flagged " + findings.size()
                            + " item(s) - see webhook/log").withStyle(ChatFormatting.RED);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.hasPermissions(3)) {
                    player.sendSystemMessage(line);
                }
            }
        }

        postWebhook(username, uuid, findings, clientJars, serverInv);
    }

    private void postWebhook(String username, String uuid, List<String> findings,
                             List<String[]> clientJars, List<String> serverInv) {
        String url = WFCoreConfig.getModAuditWebhookUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        boolean fullList = WFCoreConfig.isModAuditWebhookFullList();
        if (!fullList && findings.isEmpty()) {
            return;
        }

        WEBHOOK.submit(() -> {
            try {
                String summary = buildSummary(username, findings, clientJars);
                if (fullList && clientJars != null) {
                    String file = buildInventoryFile(username, uuid, findings, clientJars, serverInv);
                    postMultipart(url, summary, "modaudit-" + safeFileTag(username) + ".txt", file);
                } else {
                    postJson(url, summary);
                }
            } catch (Throwable t) {
                WFCore.LOGGER.warn("[wfcore-modaudit] failed to post webhook for {}: {}", username, t.toString());
            }
        });
    }

    private static String buildSummary(String username, List<String> findings, List<String[]> clientJars) {
        StringBuilder body = new StringBuilder();
        int jarCount = clientJars != null ? clientJars.size() : 0;
        if (findings.isEmpty()) {
            body.append("**Mod audit - ").append(escape(username)).append("**: clean (")
                    .append(jarCount).append(" jars). Full inventory attached.");
            return "{\"content\":" + jsonString(body.toString()) + "}";
        }
        body.append("**Mod audit - ").append(escape(username)).append("**: ")
                .append(findings.size()).append(" finding(s)");
        if (jarCount > 0) {
            body.append(", ").append(jarCount).append(" jars (full inventory attached)");
        }
        int shown = 0;
        for (String finding : findings) {
            if (body.length() > 1800 || shown >= 15) {
                body.append("\n… (+").append(findings.size() - shown).append(" more - see attachment)");
                break;
            }
            body.append("\n• ").append(escape(finding));
            shown++;
        }
        return "{\"content\":" + jsonString(body.toString()) + "}";
    }

    private static String buildInventoryFile(String username, String uuid, List<String> findings,
                                             List<String[]> clientJars, List<String> serverInv) {
        StringBuilder sb = new StringBuilder(1 << 14);
        sb.append("WFCore mod audit\n");
        sb.append("player : ").append(username).append("  (").append(uuid).append(")\n");
        sb.append("findings: ").append(findings.size()).append('\n');
        sb.append('\n');

        sb.append("== FINDINGS ==\n");
        if (findings.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (String finding : findings) {
                sb.append("  - ").append(finding).append('\n');
            }
        }
        sb.append('\n');

        int clientCount = clientJars != null ? clientJars.size() : 0;
        sb.append("== CLIENT LOADED MODS (").append(clientCount).append(") ==\n");
        if (clientJars != null) {
            List<String[]> sorted = new ArrayList<>(clientJars);
            sorted.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
            for (String[] jar : sorted) {
                sb.append(jar[0]).append("  ").append(jar[1]);
                if (jar[2] != null && !jar[2].isEmpty()) {
                    sb.append("  [").append(jar[2]).append(']');
                }
                sb.append('\n');
            }
        }
        sb.append('\n');

        int serverCount = serverInv != null ? serverInv.size() : 0;
        sb.append("== SERVER MODS (").append(serverCount).append(") ==\n");
        if (serverInv != null) {
            for (String line : serverInv) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private List<String> serverInventoryLines() {
        Map<String, String> hashes = manifest();               // fileName -> hex
        Map<String, String> mods = ModInventory.modsByFileName(); // fileName -> mods (server-loaded only)
        List<String> out = new ArrayList<>(hashes.size());
        for (Map.Entry<String, String> e : new TreeMap<>(hashes).entrySet()) {
            String mod = mods.getOrDefault(e.getKey(), "(not loaded server-side / client-only)");
            out.add(e.getValue() + "  " + e.getKey() + (mod.isEmpty() ? "" : "  [" + mod + "]"));
        }
        return out;
    }
    private Map<String, String> manifest() {
        Map<String, String> cached = manifest;
        if (cached == null) {
            cached = loadManifest();
            manifest = cached;
        }
        return cached;
    }

    private Map<String, String> loadManifest() {
        Map<String, String> out = new LinkedHashMap<>();
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
                String hex = ModInventory.sha256hex(p);
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

    private static String inflate(byte[][] slices) {
        try {
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            for (byte[] slice : slices) {
                if (slice == null) {
                    return null;
                }
                joined.write(slice);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(joined.toByteArray()))) {
                byte[] buf = new byte[1 << 14];
                int read;
                while ((read = gz.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void postJson(String url, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            WFCore.LOGGER.warn("[wfcore-modaudit] webhook returned HTTP {}", response.statusCode());
        }
    }

    private static void postMultipart(String url, String payloadJson, String fileName, String fileText) throws Exception {
        String crlf = "\r\n";
        String head = "--" + BOUNDARY + crlf
                + "Content-Disposition: form-data; name=\"payload_json\"" + crlf
                + "Content-Type: application/json" + crlf + crlf
                + payloadJson + crlf
                + "--" + BOUNDARY + crlf
                + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + fileName + "\"" + crlf
                + "Content-Type: text/plain; charset=utf-8" + crlf + crlf;
        String tail = crlf + "--" + BOUNDARY + "--" + crlf;

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(fileText.getBytes(StandardCharsets.UTF_8));
        body.write(tail.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            WFCore.LOGGER.warn("[wfcore-modaudit] webhook returned HTTP {}", response.statusCode());
        }
    }

    private static String safeFileTag(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.length() == 0 ? "player" : sb.toString();
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
