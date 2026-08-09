package com.norwood.wfcore.common.rank;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

/**
 * Client-side registry of {@link PlayerRank}s keyed by player UUID, populated from a remote JSON manifest.
 *
 * <p>The manifest is fetched once on {@linkplain #init() construction} (client setup) and then re-fetched on a
 * fixed interval (see {@link WFCoreConfig#getRankRefreshIntervalMinutes()}, default 4 min) on a single daemon
 * thread. Results are cached to {@code config/wfcore-ranks-cache.json} so tags survive a start-up with no
 * network. All lookups are read-only and safe to call from the render thread.
 *
 * <p>Manifest shape (UUIDs may be dashed or undashed; a value may be one rank or an array of ranks):
 * <pre>{@code
 * {
 *   "ranks": {
 *     "069a79f4-44e9-4726-a5be-fca90e38aaf5": "ADMIN",
 *     "853c80ef3c3749fdaa49938b674adae6": "PRESS",
 *     "61699b2ed32741019f1e0ea8c3f06bc6": ["ARTIST", "SUPEROBAMA"]
 *   }
 * }
 * }</pre>
 * The top-level {@code "ranks"} wrapper is optional - a bare UUID→rank object is also accepted.
 */
public final class PlayerRankUtil {

    private static final String CACHE_FILE_NAME = "wfcore-ranks-cache.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * UUID → ranks, replaced wholesale (never mutated in place) by the manifest thread and read by the
     * render/chat/mixin threads. {@code volatile} makes each swap atomically visible; the value sets are built
     * once at parse time and never modified afterwards, so readers iterate a stable snapshot.
     */
    private static volatile Map<UUID, EnumSet<PlayerRank>> ranks = Collections.emptyMap();

    private static ScheduledExecutorService scheduler;
    private static boolean started = false;

    private PlayerRankUtil() {}


    public static synchronized void init() {
        if (started) {
            return;
        }
        started = true;
        if (!WFCoreConfig.isPlayerRanksEnabled()) {
            WFCore.LOGGER.info("wfcore: player ranks disabled in config; manifest not loaded.");
            return;
        }

        loadFromCache();

        long minutes = Math.max(1L, WFCoreConfig.getRankRefreshIntervalMinutes());
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "wfcore-rank-manifest");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(PlayerRankUtil::refresh, 0L, minutes, TimeUnit.MINUTES);
    }

    private static void refresh() {
        String url = WFCoreConfig.getRankManifestUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                WFCore.LOGGER.warn("wfcore: player-rank manifest returned HTTP {} from {}", response.statusCode(), url);
                return;
            }
            Map<UUID, EnumSet<PlayerRank>> parsed = parse(response.body());
            apply(parsed);
            writeCache(response.body());
            WFCore.LOGGER.info("wfcore: loaded {} player-rank entries from manifest.", parsed.size());
        } catch (Throwable t) {
            WFCore.LOGGER.warn("wfcore: failed to refresh player-rank manifest ({}): {}", url, t.toString());
        }
    }

    private static Map<UUID, EnumSet<PlayerRank>> parse(String body) {
        Map<UUID, EnumSet<PlayerRank>> out = new HashMap<>();
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonObject()) {
            return out;
        }
        JsonObject obj = root.getAsJsonObject();
        JsonObject entries = obj.has("ranks") && obj.get("ranks").isJsonObject()
                ? obj.getAsJsonObject("ranks")
                : obj;

        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            UUID id = parseUuid(entry.getKey());
            if (id == null) {
                WFCore.LOGGER.warn("wfcore: skipping player-rank entry with invalid UUID '{}'", entry.getKey());
                continue;
            }
            EnumSet<PlayerRank> ranks = EnumSet.noneOf(PlayerRank.class);
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    addRank(ranks, element);
                }
            } else {
                addRank(ranks, value);
            }
            if (!ranks.isEmpty()) {
                out.put(id, ranks);
            }
        }
        return out;
    }

    private static void addRank(EnumSet<PlayerRank> into, JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }
        String name = element.getAsString();
        PlayerRank rank = PlayerRank.byId(name);
        if (rank == null) {
            WFCore.LOGGER.warn("wfcore: unknown player rank '{}' in manifest (ignored)", name);
            return;
        }
        into.add(rank);
    }

    private static void apply(Map<UUID, EnumSet<PlayerRank>> parsed) {
        ranks = parsed;
    }

    // ------------------------------------------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------------------------------------------

    private static Path cacheFile() {
        return FMLPaths.CONFIGDIR.get().resolve(CACHE_FILE_NAME);
    }

    private static void loadFromCache() {
        Path file = cacheFile();
        try {
            if (Files.isRegularFile(file)) {
                Map<UUID, EnumSet<PlayerRank>> parsed = parse(Files.readString(file, StandardCharsets.UTF_8));
                apply(parsed);
                WFCore.LOGGER.info("wfcore: loaded {} cached player-rank entries.", parsed.size());
            }
        } catch (Throwable t) {
            WFCore.LOGGER.warn("wfcore: failed to read player-rank cache: {}", t.toString());
        }
    }

    private static void writeCache(String body) {
        try {
            Files.writeString(cacheFile(), body, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("wfcore: failed to write player-rank cache: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // Lookups (read-only, thread-safe)
    // ------------------------------------------------------------------------------------------------------

    /** The ranks held by {@code id}; an empty set when unknown. The returned set must not be mutated. */
    public static EnumSet<PlayerRank> getRanks(@Nullable UUID id) {
        if (id == null) {
            return EnumSet.noneOf(PlayerRank.class);
        }
        EnumSet<PlayerRank> held = ranks.get(id);
        return held == null ? EnumSet.noneOf(PlayerRank.class) : held;
    }

    /** True if any of {@code id}'s ranks unlocks replay playback. */
    public static boolean canReplay(@Nullable UUID id) {
        for (PlayerRank rank : getRanks(id)) {
            if (rank.grantsReplay()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the replay gate is enforced. <b>Fail-closed:</b> when active, a player is allowed only if the
     * loaded manifest grants them a replay rank - an unknown player (not in the manifest, or the manifest hasn't
     * downloaded yet) is blocked. This matches the core intent ("normal players cannot replay") and is why the
     * disk cache is loaded synchronously in {@link #init()} before anything can open a replay, so returning
     * Press users keep access even offline. To unlock replay for everyone, set {@code replayRequiresRank=false}
     * (or {@code enabled=false}) in config.
     */
    public static boolean isReplayGateActive() {
        return WFCoreConfig.isPlayerRanksEnabled()
                && WFCoreConfig.isReplayRankRequired();
    }

    /** The visible {@code [Label]} for {@code id}'s highest-priority vanity rank, or {@code null}. */
    @Nullable
    public static Component chatTag(@Nullable UUID id) {
        PlayerRank best = null;
        for (PlayerRank rank : getRanks(id)) {
            if (rank.hasVanityTag() && (best == null || rank.ordinal() > best.ordinal())) {
                best = rank;
            }
        }
        return best == null ? null : best.tag();
    }

    /** True if any of {@code id}'s ranks rewrites their chat text (e.g. mtns / superobama). */
    public static boolean hasChatContentTransform(@Nullable UUID id) {
        for (PlayerRank rank : getRanks(id)) {
            if (rank.contentTransform() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies every chat-content transform held by {@code id} to {@code content}, chained in rank declaration
     * order. Returns {@code content} unchanged when the player has no transform ranks.
     */
    public static String transformChatContent(@Nullable UUID id, String content) {
        String result = content;
        for (PlayerRank rank : getRanks(id)) {
            java.util.function.UnaryOperator<String> transform = rank.contentTransform();
            if (transform != null) {
                result = transform.apply(result);
            }
        }
        return result;
    }

    /** Parses a dashed or undashed (Mojang-profile-style) 32-hex UUID; {@code null} if neither. */
    @Nullable
    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        try {
            if (text.length() == 32 && text.indexOf('-') < 0) {
                text = text.substring(0, 8) + "-" + text.substring(8, 12) + "-" + text.substring(12, 16)
                        + "-" + text.substring(16, 20) + "-" + text.substring(20, 32);
            }
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
