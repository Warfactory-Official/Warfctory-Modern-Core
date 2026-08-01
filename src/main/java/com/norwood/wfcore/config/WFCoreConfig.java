package com.norwood.wfcore.config;

import net.minecraftforge.common.ForgeConfigSpec;

import com.norwood.wfcore.WFCore;

public final class WFCoreConfig {

    // -------------------------------------------------------------------------
    // Default constants (kept so callers that reference DEFAULT_* still compile,
    // and so the volatile fields have sane pre-load values).
    // -------------------------------------------------------------------------
    private static final int DEFAULT_ENERGY_TO_FLUID_RATIO = 10;
    private static final int DEFAULT_REFUEL_INTERVAL_TICKS = 20;
    private static final boolean DEFAULT_CLEAR_STRUCTURE_LOOT = true;
    private static final boolean DEFAULT_DISABLE_NETHER = true;
    private static final int DEFAULT_DEPOSIT_YIELD_MIN = 2000;
    private static final int DEFAULT_DEPOSIT_YIELD_MAX = 8000;
    private static final boolean DEFAULT_DEPOSIT_WORLDGEN_ENABLED = true;
    private static final boolean DEFAULT_DEPOSIT_SCATTER = true;
    private static final int DEFAULT_DEPOSIT_WORLDGEN_RARITY = 24;
    private static final boolean DEFAULT_DEPOSIT_LOG_PLACEMENTS = false;
    private static final boolean DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED = false;
    private static final boolean DEFAULT_BALLISTICS_ENABLED = true;
    private static final boolean DEFAULT_BALLISTICS_DEBUG_LOGGING = false;
    private static final boolean DEFAULT_PLAYER_RANKS_ENABLED = true;
    private static final String DEFAULT_RANK_MANIFEST_URL = "";
    private static final int DEFAULT_RANK_REFRESH_INTERVAL_MINUTES = 4;
    private static final boolean DEFAULT_REPLAY_RANK_REQUIRED = true;
    private static final boolean DEFAULT_SHOW_CHAT_TAGS = true;
    private static final boolean DEFAULT_SHOW_TAB_TAGS = true;
    private static final boolean DEFAULT_DIAG_ENABLED = false;
    private static final int DEFAULT_DIAG_AUTO_INTERVAL_SECONDS = 0;
    private static final int DEFAULT_DIAG_MAX_IMAGE_EDGE = 2560;
    private static final int DEFAULT_DIAG_JPEG_QUALITY = 70;
    private static final int DEFAULT_DIAG_MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final int DEFAULT_DIAG_CAPTURE_TIMEOUT_SECONDS = 15;
    private static final int DEFAULT_DIAG_CHUNK_SIZE = 262144;
    private static final double DEFAULT_DIAG_MIN_VARIANCE = 6.0;
    private static final boolean DEFAULT_MOD_AUDIT_ENABLED = false;
    private static final String DEFAULT_MOD_AUDIT_WEBHOOK_URL = "";
    private static final int DEFAULT_MOD_AUDIT_TIMEOUT_SECONDS = 20;
    private static final boolean DEFAULT_MOD_AUDIT_FLAG_MISSING = false;

    // -------------------------------------------------------------------------
    // Volatile cache fields — pre-initialised to defaults so getters are safe
    // even before the config file is loaded.
    // -------------------------------------------------------------------------
    private static volatile int energyToFluidRatio = DEFAULT_ENERGY_TO_FLUID_RATIO;
    private static volatile int refuelIntervalTicks = DEFAULT_REFUEL_INTERVAL_TICKS;
    private static volatile boolean clearStructureLoot = DEFAULT_CLEAR_STRUCTURE_LOOT;
    private static volatile boolean disableNether = DEFAULT_DISABLE_NETHER;
    private static volatile int depositYieldMin = DEFAULT_DEPOSIT_YIELD_MIN;
    private static volatile int depositYieldMax = DEFAULT_DEPOSIT_YIELD_MAX;
    private static volatile boolean depositWorldgenEnabled = DEFAULT_DEPOSIT_WORLDGEN_ENABLED;
    private static volatile boolean depositScatter = DEFAULT_DEPOSIT_SCATTER;
    private static volatile int depositWorldgenRarity = DEFAULT_DEPOSIT_WORLDGEN_RARITY;
    private static volatile boolean depositLogPlacements = DEFAULT_DEPOSIT_LOG_PLACEMENTS;
    private static volatile boolean modelTransformDebugEnabled = DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED;
    private static volatile boolean ballisticsEnabled = DEFAULT_BALLISTICS_ENABLED;
    private static volatile boolean ballisticsDebugLogging = DEFAULT_BALLISTICS_DEBUG_LOGGING;
    private static volatile boolean playerRanksEnabled = DEFAULT_PLAYER_RANKS_ENABLED;
    private static volatile String rankManifestUrl = DEFAULT_RANK_MANIFEST_URL;
    private static volatile int rankRefreshIntervalMinutes = DEFAULT_RANK_REFRESH_INTERVAL_MINUTES;
    private static volatile boolean replayRankRequired = DEFAULT_REPLAY_RANK_REQUIRED;
    private static volatile boolean showChatTags = DEFAULT_SHOW_CHAT_TAGS;
    private static volatile boolean showTabTags = DEFAULT_SHOW_TAB_TAGS;
    private static volatile boolean diagEnabled = DEFAULT_DIAG_ENABLED;
    private static volatile int diagAutoIntervalSeconds = DEFAULT_DIAG_AUTO_INTERVAL_SECONDS;
    private static volatile int diagMaxImageEdge = DEFAULT_DIAG_MAX_IMAGE_EDGE;
    private static volatile int diagJpegQuality = DEFAULT_DIAG_JPEG_QUALITY;
    private static volatile int diagMaxImageBytes = DEFAULT_DIAG_MAX_IMAGE_BYTES;
    private static volatile int diagCaptureTimeoutSeconds = DEFAULT_DIAG_CAPTURE_TIMEOUT_SECONDS;
    private static volatile int diagChunkSize = DEFAULT_DIAG_CHUNK_SIZE;
    private static volatile double diagMinVariance = DEFAULT_DIAG_MIN_VARIANCE;
    private static volatile boolean modAuditEnabled = DEFAULT_MOD_AUDIT_ENABLED;
    private static volatile String modAuditWebhookUrl = DEFAULT_MOD_AUDIT_WEBHOOK_URL;
    private static volatile int modAuditTimeoutSeconds = DEFAULT_MOD_AUDIT_TIMEOUT_SECONDS;
    private static volatile boolean modAuditFlagMissing = DEFAULT_MOD_AUDIT_FLAG_MISSING;

    // -------------------------------------------------------------------------
    // ForgeConfigSpec handles
    // -------------------------------------------------------------------------
    private static final ForgeConfigSpec.IntValue ENERGY_TO_FLUID_RATIO;
    private static final ForgeConfigSpec.IntValue REFUEL_INTERVAL_TICKS;
    private static final ForgeConfigSpec.BooleanValue CLEAR_STRUCTURE_LOOT;
    private static final ForgeConfigSpec.BooleanValue DISABLE_NETHER;
    private static final ForgeConfigSpec.BooleanValue MODEL_TRANSFORM_DEBUG_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BALLISTICS_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BALLISTICS_DEBUG_LOGGING;
    private static final ForgeConfigSpec.BooleanValue PLAYER_RANKS_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> RANK_MANIFEST_URL;
    private static final ForgeConfigSpec.IntValue RANK_REFRESH_INTERVAL_MINUTES;
    private static final ForgeConfigSpec.BooleanValue REPLAY_RANK_REQUIRED;
    private static final ForgeConfigSpec.BooleanValue SHOW_CHAT_TAGS;
    private static final ForgeConfigSpec.BooleanValue SHOW_TAB_TAGS;
    private static final ForgeConfigSpec.BooleanValue DIAG_ENABLED;
    private static final ForgeConfigSpec.IntValue DIAG_AUTO_INTERVAL_SECONDS;
    private static final ForgeConfigSpec.IntValue DIAG_MAX_IMAGE_EDGE;
    private static final ForgeConfigSpec.IntValue DIAG_JPEG_QUALITY;
    private static final ForgeConfigSpec.IntValue DIAG_MAX_IMAGE_BYTES;
    private static final ForgeConfigSpec.IntValue DIAG_CAPTURE_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.IntValue DIAG_CHUNK_SIZE;
    private static final ForgeConfigSpec.DoubleValue DIAG_MIN_VARIANCE;
    private static final ForgeConfigSpec.BooleanValue MOD_AUDIT_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> MOD_AUDIT_WEBHOOK_URL;
    private static final ForgeConfigSpec.IntValue MOD_AUDIT_TIMEOUT_SECONDS;
    private static final ForgeConfigSpec.BooleanValue MOD_AUDIT_FLAG_MISSING;
    private static final ForgeConfigSpec.IntValue DEPOSIT_YIELD_MIN;
    private static final ForgeConfigSpec.IntValue DEPOSIT_YIELD_MAX;
    private static final ForgeConfigSpec.BooleanValue DEPOSIT_WORLDGEN_ENABLED;
    private static final ForgeConfigSpec.BooleanValue DEPOSIT_SCATTER;
    private static final ForgeConfigSpec.IntValue DEPOSIT_WORLDGEN_RARITY;
    private static final ForgeConfigSpec.BooleanValue DEPOSIT_LOG_PLACEMENTS;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        ENERGY_TO_FLUID_RATIO = builder
                .comment("Forge energy produced per millibucket of fluid fuel consumed.")
                .defineInRange("energyToFluidRatio", DEFAULT_ENERGY_TO_FLUID_RATIO, 1, Integer.MAX_VALUE);

        REFUEL_INTERVAL_TICKS = builder
                .comment("How often (in ticks) a fuelled vehicle tops up from its fluid tank.")
                .defineInRange("refuelIntervalTicks", DEFAULT_REFUEL_INTERVAL_TICKS, 1, Integer.MAX_VALUE);

        CLEAR_STRUCTURE_LOOT = builder
                .comment(
                        "Empty every chest and fishing loot table by default; repopulate or whitelist via KubeJS (WFLoot).")
                .define("clearStructureLoot", DEFAULT_CLEAR_STRUCTURE_LOOT);

        DISABLE_NETHER = builder
                .comment(
                        "Make the Nether inaccessible: nether portals never form and any travel to the_nether (portals, commands) is blocked.")
                .define("disableNether", DEFAULT_DISABLE_NETHER);

        MODEL_TRANSFORM_DEBUG_ENABLED = builder
                .comment(
                        "Dev tool: numpad-driven live editor for animated-machine model offsets (see IAnimatedMachine). Off by default so the numpad bindings and HUD stay inert for normal players.")
                .define("modelTransformDebugEnabled", DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED);

        builder.comment(
                "Drillable bedrock deposits. defaultYield is the per-block yield range used when a deposit type (built-in or KubeJS) does not specify its own.")
                .push("deposits");

        DEPOSIT_YIELD_MIN = builder
                .defineInRange("defaultYieldMin", DEFAULT_DEPOSIT_YIELD_MIN, 1, Integer.MAX_VALUE);

        DEPOSIT_YIELD_MAX = builder
                .defineInRange("defaultYieldMax", DEFAULT_DEPOSIT_YIELD_MAX, 1, Integer.MAX_VALUE);

        builder.comment(
                "Ambient weighted scatter across the world. Turn scatter off to rely only on KubeJS nodes/regions. rarity is \"1 in N chunks\".")
                .push("worldgen");

        DEPOSIT_WORLDGEN_ENABLED = builder
                .define("enabled", DEFAULT_DEPOSIT_WORLDGEN_ENABLED);

        DEPOSIT_SCATTER = builder
                .define("scatter", DEFAULT_DEPOSIT_SCATTER);

        DEPOSIT_WORLDGEN_RARITY = builder
                .defineInRange("rarity", DEFAULT_DEPOSIT_WORLDGEN_RARITY, 1, Integer.MAX_VALUE);

        DEPOSIT_LOG_PLACEMENTS = builder
                .comment("Debug: log every deposit cluster placed by worldgen (type, size, position). Testing aid.")
                .define("logPlacements", DEFAULT_DEPOSIT_LOG_PLACEMENTS);

        builder.pop(2);

        builder.comment("Off-thread long-range ballistics for TACZ bullets and Superb Warfare projectiles.")
                .push("ballistics");

        BALLISTICS_ENABLED = builder
                .comment(
                        "Master switch. When off, projectiles are never handed to the off-thread ballistics engine and behave exactly as their own mod ships them.")
                .define("enabled", DEFAULT_BALLISTICS_ENABLED);

        BALLISTICS_DEBUG_LOGGING = builder
                .comment(
                        "Log each virtual shell's lifecycle to the server log: leaving loaded chunks, impact position (and whether that chunk was loaded), deferred detonations, and expiries.")
                .define("debugLogging", DEFAULT_BALLISTICS_DEBUG_LOGGING);

        builder.pop();

        builder.comment(
                "Client-side player ranks / vanity tags, downloaded from a remote JSON manifest (uuid -> rank).",
                "Ranks: ARTIST, SCRIPTER (chat/tab tag), PRESS (may play back replays), MODERATOR, ADMIN (both",
                "of the above), plus the fun tags MTNS and SUPEROBAMA (chat-text effects only).")
                .push("playerRanks");

        PLAYER_RANKS_ENABLED = builder
                .comment("Master switch. When off, no manifest is downloaded and no tags or replay gate apply.")
                .define("enabled", DEFAULT_PLAYER_RANKS_ENABLED);

        RANK_MANIFEST_URL = builder
                .comment(
                        "URL of the JSON rank manifest. Leave blank to disable downloads (cached data, if any, still loads).",
                        "Shape: { \"ranks\": { \"<uuid>\": \"ADMIN\", \"<uuid>\": [\"ARTIST\", \"SUPEROBAMA\"] } }.",
                        "UUIDs may be dashed or undashed; a value may be one rank or an array of ranks.")
                .define("manifestUrl", DEFAULT_RANK_MANIFEST_URL);

        RANK_REFRESH_INTERVAL_MINUTES = builder
                .comment("How often (minutes) the manifest is re-downloaded after the initial fetch. 3-5 recommended.")
                .defineInRange("refreshIntervalMinutes", DEFAULT_RANK_REFRESH_INTERVAL_MINUTES, 1, 1440);

        REPLAY_RANK_REQUIRED = builder
                .comment(
                        "When true, replay playback requires a replay-granting rank (Press/Moderator/Admin).",
                        "Recording is never restricted. Fails open until a manifest has loaded at least once.")
                .define("replayRequiresRank", DEFAULT_REPLAY_RANK_REQUIRED);

        SHOW_CHAT_TAGS = builder
                .comment("Show the vanity [Tag] before ranked players' chat messages.")
                .define("showChatTags", DEFAULT_SHOW_CHAT_TAGS);

        SHOW_TAB_TAGS = builder
                .comment("Show the vanity [Tag] before ranked players' names in the tab list.")
                .define("showTabTags", DEFAULT_SHOW_TAB_TAGS);

        builder.pop();

        builder.comment("Server-directed client render-target sampling used for integrity review.")
                .push("diagnostics");

        DIAG_ENABLED = builder
                .comment("Master switch. When off, no samples are requested and inbound samples are ignored.")
                .define("enabled", DEFAULT_DIAG_ENABLED);

        DIAG_AUTO_INTERVAL_SECONDS = builder
                .comment("Automatically request a sample from a random online player every N seconds. 0 disables automatic sampling (manual /wfcore_diag capture still works).")
                .defineInRange("autoIntervalSeconds", DEFAULT_DIAG_AUTO_INTERVAL_SECONDS, 0, Integer.MAX_VALUE);

        DIAG_MAX_IMAGE_EDGE = builder
                .comment("Longest edge (pixels) of the returned image; larger frames are downscaled to fit. Also the server-side rejection cap.")
                .defineInRange("maxImageEdge", DEFAULT_DIAG_MAX_IMAGE_EDGE, 256, 8192);

        DIAG_JPEG_QUALITY = builder
                .comment("JPEG quality (1-100). Lower is smaller.")
                .defineInRange("jpegQuality", DEFAULT_DIAG_JPEG_QUALITY, 1, 100);

        DIAG_MAX_IMAGE_BYTES = builder
                .comment("Hard ceiling (bytes) for a returned image. Anything larger is rejected.")
                .defineInRange("maxImageBytes", DEFAULT_DIAG_MAX_IMAGE_BYTES, 64 * 1024, 20 * 1024 * 1024);

        DIAG_CAPTURE_TIMEOUT_SECONDS = builder
                .comment("How long (seconds) to wait for a client to return a requested sample before flagging it.")
                .defineInRange("captureTimeoutSeconds", DEFAULT_DIAG_CAPTURE_TIMEOUT_SECONDS, 1, 300);

        DIAG_CHUNK_SIZE = builder
                .comment("Transfer chunk size (bytes) for the returned image.")
                .defineInRange("chunkSize", DEFAULT_DIAG_CHUNK_SIZE, 4096, 1048576);

        DIAG_MIN_VARIANCE = builder
                .comment("Minimum luminance variance a returned image must have; near-uniform frames are rejected.")
                .defineInRange("minVariance", DEFAULT_DIAG_MIN_VARIANCE, 0.0, 65025.0);

        builder.pop();

        builder.comment(
                "Soft client-mod integrity audit. On join the server asks the client to hash its mods-folder jars and",
                "compares them to config/wfcore-modmanifest.json (fileName -> sha256, regenerated per pack build).",
                "Anything unknown or modified is FLAGGED (log + operator message + optional Discord webhook) but NEVER",
                "kicked - a newer build is plausible, so an admin reviews. Hard mod validation stays with the anticheat.")
                .push("clientModAudit");

        MOD_AUDIT_ENABLED = builder
                .comment("Master switch. When off, no request is sent on join and inbound reports are ignored.")
                .define("enabled", DEFAULT_MOD_AUDIT_ENABLED);

        MOD_AUDIT_WEBHOOK_URL = builder
                .comment("Discord webhook URL for flag notifications. Leave blank to log + notify in-game operators only.")
                .define("webhookUrl", DEFAULT_MOD_AUDIT_WEBHOOK_URL);

        MOD_AUDIT_TIMEOUT_SECONDS = builder
                .comment("How long (seconds) to wait for a client's mod report before flagging that it never arrived.")
                .defineInRange("reportTimeoutSeconds", DEFAULT_MOD_AUDIT_TIMEOUT_SECONDS, 1, 300);

        MOD_AUDIT_FLAG_MISSING = builder
                .comment("Also flag manifest jars a client did NOT report (stripped mods). Off by default to reduce noise.")
                .define("flagMissing", DEFAULT_MOD_AUDIT_FLAG_MISSING);

        builder.pop();

        SPEC = builder.build();
    }

    private WFCoreConfig() {}

    // -------------------------------------------------------------------------
    // Public API — unchanged signatures
    // -------------------------------------------------------------------------

    public static int getEnergyToFluidRatio() {
        return energyToFluidRatio;
    }

    public static int getRefuelIntervalTicks() {
        return refuelIntervalTicks;
    }

    /** When true, WFCore empties every chest/fishing loot table on load unless KubeJS overrides or keeps it. */
    public static boolean isClearStructureLoot() {
        return clearStructureLoot;
    }

    /** When true, the Nether is disabled: portals never form and travel to {@code minecraft:the_nether} is blocked. */
    public static boolean isNetherDisabled() {
        return disableNether;
    }

    /** Default per-block deposit yield range, used when a deposit type does not set its own. */
    public static int getDefaultYieldMin() {
        return depositYieldMin;
    }

    public static int getDefaultYieldMax() {
        return depositYieldMax;
    }

    public static boolean isDepositWorldgenEnabled() {
        return depositWorldgenEnabled;
    }

    /** When true, deposits also scatter randomly across the world (in addition to KubeJS nodes/regions). */
    public static boolean isDepositScatterEnabled() {
        return depositScatter;
    }

    /** Deposit worldgen rarity, as "1 in N chunks". */
    public static int getDepositWorldgenRarity() {
        return depositWorldgenRarity;
    }

    /** Debug: when true, worldgen logs every deposit cluster it places (type, size, position). */
    public static boolean isDepositLogPlacements() {
        return depositLogPlacements;
    }

    /** Dev tool gate: the numpad model-transform debugger (see IAnimatedMachine) only arms when this is true. */
    public static boolean isModelTransformDebugEnabled() {
        return modelTransformDebugEnabled;
    }

    /** Master switch for the off-thread long-range ballistics engine (TACZ bullets + SBW projectiles). */
    public static boolean isBallisticsEnabled() {
        return ballisticsEnabled;
    }

    /** When true, the ballistics engine logs each virtual shell's lifecycle (demote / impact / defer / expiry). */
    public static boolean isBallisticsDebugLogging() {
        return ballisticsDebugLogging;
    }

    /** Master switch for the client-side player-rank / vanity-tag system. */
    public static boolean isPlayerRanksEnabled() {
        return playerRanksEnabled;
    }

    /** URL of the JSON rank manifest, or blank to disable downloads. */
    public static String getRankManifestUrl() {
        return rankManifestUrl;
    }

    /** How often (minutes) the rank manifest is re-downloaded after the initial fetch. */
    public static int getRankRefreshIntervalMinutes() {
        return rankRefreshIntervalMinutes;
    }

    /** When true, replay playback requires a replay-granting rank (Press/Moderator/Admin). */
    public static boolean isReplayRankRequired() {
        return replayRankRequired;
    }

    /** When true, ranked players' chat messages are prefixed with their vanity {@code [Tag]}. */
    public static boolean isShowChatTags() {
        return showChatTags;
    }

    /** When true, ranked players' tab-list names are prefixed with their vanity {@code [Tag]}. */
    public static boolean isShowTabTags() {
        return showTabTags;
    }

    public static boolean isDiagEnabled() {
        return diagEnabled;
    }

    public static int getDiagAutoIntervalSeconds() {
        return diagAutoIntervalSeconds;
    }

    public static int getDiagMaxImageEdge() {
        return diagMaxImageEdge;
    }

    public static int getDiagJpegQuality() {
        return diagJpegQuality;
    }

    public static int getDiagMaxImageBytes() {
        return diagMaxImageBytes;
    }

    public static int getDiagCaptureTimeoutSeconds() {
        return diagCaptureTimeoutSeconds;
    }

    public static int getDiagChunkSize() {
        return diagChunkSize;
    }

    public static double getDiagMinVariance() {
        return diagMinVariance;
    }

    /** Master switch for the soft client-mod integrity audit (hash report vs manifest, flag-only). */
    public static boolean isModAuditEnabled() {
        return modAuditEnabled;
    }

    /** Discord webhook URL for mod-audit flags, or blank to log + notify in-game operators only. */
    public static String getModAuditWebhookUrl() {
        return modAuditWebhookUrl;
    }

    /** How long (seconds) to wait for a client's mod report before flagging its absence. */
    public static int getModAuditTimeoutSeconds() {
        return modAuditTimeoutSeconds;
    }

    /** When true, also flag manifest jars a client did not report (stripped mods). */
    public static boolean isModAuditFlagMissing() {
        return modAuditFlagMissing;
    }

    // -------------------------------------------------------------------------
    // Bake — called by WFCore on ModConfigEvent
    // -------------------------------------------------------------------------

    public static void bake() {
        energyToFluidRatio = ENERGY_TO_FLUID_RATIO.get();
        refuelIntervalTicks = REFUEL_INTERVAL_TICKS.get();
        clearStructureLoot = CLEAR_STRUCTURE_LOOT.get();
        disableNether = DISABLE_NETHER.get();
        modelTransformDebugEnabled = MODEL_TRANSFORM_DEBUG_ENABLED.get();
        ballisticsEnabled = BALLISTICS_ENABLED.get();
        ballisticsDebugLogging = BALLISTICS_DEBUG_LOGGING.get();
        playerRanksEnabled = PLAYER_RANKS_ENABLED.get();
        rankManifestUrl = RANK_MANIFEST_URL.get();
        rankRefreshIntervalMinutes = RANK_REFRESH_INTERVAL_MINUTES.get();
        replayRankRequired = REPLAY_RANK_REQUIRED.get();
        showChatTags = SHOW_CHAT_TAGS.get();
        showTabTags = SHOW_TAB_TAGS.get();
        diagEnabled = DIAG_ENABLED.get();
        diagAutoIntervalSeconds = DIAG_AUTO_INTERVAL_SECONDS.get();
        diagMaxImageEdge = DIAG_MAX_IMAGE_EDGE.get();
        diagJpegQuality = DIAG_JPEG_QUALITY.get();
        diagMaxImageBytes = DIAG_MAX_IMAGE_BYTES.get();
        diagCaptureTimeoutSeconds = DIAG_CAPTURE_TIMEOUT_SECONDS.get();
        diagChunkSize = DIAG_CHUNK_SIZE.get();
        diagMinVariance = DIAG_MIN_VARIANCE.get();
        modAuditEnabled = MOD_AUDIT_ENABLED.get();
        modAuditWebhookUrl = MOD_AUDIT_WEBHOOK_URL.get();
        modAuditTimeoutSeconds = MOD_AUDIT_TIMEOUT_SECONDS.get();
        modAuditFlagMissing = MOD_AUDIT_FLAG_MISSING.get();
        depositYieldMin = DEPOSIT_YIELD_MIN.get();
        depositYieldMax = Math.max(DEPOSIT_YIELD_MAX.get(), depositYieldMin);
        depositWorldgenEnabled = DEPOSIT_WORLDGEN_ENABLED.get();
        depositScatter = DEPOSIT_SCATTER.get();
        depositWorldgenRarity = DEPOSIT_WORLDGEN_RARITY.get();
        depositLogPlacements = DEPOSIT_LOG_PLACEMENTS.get();
        // Vehicle overrides + foliage breakers now come from the WFVehicles KubeJS API (registered at startup).
        WFCore.LOGGER.info("Loaded WFCore TOML config: energy ratio {}, refuel interval {} ticks",
                energyToFluidRatio, refuelIntervalTicks);
    }

}
