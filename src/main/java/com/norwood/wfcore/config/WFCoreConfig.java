package com.norwood.wfcore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.chat.FilterAction;
import com.norwood.wfcore.common.darkness.DarknessEnforcement;

import java.util.ArrayList;
import java.util.List;

public final class WFCoreConfig {

    // -------------------------------------------------------------------------
    // Default constants (kept so callers that reference DEFAULT_* still compile,
    // and so the volatile fields have sane pre-load values).
    // -------------------------------------------------------------------------
    private static final double DEFAULT_FUEL_RANGE_MULTIPLIER = 1.0;
    private static final int DEFAULT_REFUEL_INTERVAL_TICKS = 20;
    private static final boolean DEFAULT_CLEAR_STRUCTURE_LOOT = true;
    private static final boolean DEFAULT_DISABLE_NETHER = true;
    private static final boolean DEFAULT_DISABLE_ENDER_PEARLS = true;
    private static final boolean DEFAULT_DISABLE_BOAT_THIRD_PERSON = true;
    private static final int DEFAULT_DEPOSIT_YIELD_MIN = 2000;
    private static final int DEFAULT_DEPOSIT_YIELD_MAX = 8000;
    private static final boolean DEFAULT_DEPOSIT_WORLDGEN_ENABLED = true;
    private static final boolean DEFAULT_DEPOSIT_SCATTER = true;
    private static final int DEFAULT_DEPOSIT_WORLDGEN_RARITY = 24;
    private static final boolean DEFAULT_DEPOSIT_LOG_PLACEMENTS = false;
    private static final boolean DEFAULT_DEPOSIT_HEAL = true;
    private static final boolean DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED = false;
    private static final boolean DEFAULT_VEHICLE_GUN_DATA_CACHE = true;
    private static final boolean DEFAULT_SBW_DRONE_HOT_PATH_CACHE = true;
    private static final boolean DEFAULT_GT_MACHINE_CAPABILITY_CACHE = true;
    private static final boolean DEFAULT_BALLISTICS_ENABLED = true;
    private static final boolean DEFAULT_BALLISTICS_DEBUG_LOGGING = false;
    private static final boolean DEFAULT_PLAYER_RANKS_ENABLED = true;
    private static final String DEFAULT_RANK_MANIFEST_URL = "https://warfactory.co/ranks.json";
    private static final int DEFAULT_RANK_REFRESH_INTERVAL_MINUTES = 4;
    private static final boolean DEFAULT_REPLAY_RANK_REQUIRED = true;
    private static final boolean DEFAULT_SHOW_CHAT_TAGS = true;
    private static final boolean DEFAULT_SHOW_TAB_TAGS = true;
    private static final boolean DEFAULT_TRUE_DARKNESS_ENABLED = true;
    private static final boolean DEFAULT_TRUE_DARKNESS_BLOCK_LIGHT_ONLY = false;
    private static final boolean DEFAULT_TRUE_DARKNESS_IGNORE_MOON_PHASE = true;
    private static final boolean DEFAULT_TRUE_DARKNESS_DARK_OVERWORLD = true;
    private static final boolean DEFAULT_TRUE_DARKNESS_DARK_NETHER = true;
    private static final boolean DEFAULT_TRUE_DARKNESS_DARK_END = true;
    private static final boolean DEFAULT_TRUE_DARKNESS_DARK_DEFAULT = true;
    private static final boolean DEFAULT_TRUE_DARKNESS_DARK_SKYLESS = false;
    private static final double DEFAULT_TRUE_DARKNESS_NETHER_FOG = 0.5;
    private static final double DEFAULT_TRUE_DARKNESS_END_FOG = 0.0;
    private static final boolean DEFAULT_BLOCK_CLIENT_SHADERS = true;
    private static final boolean DEFAULT_CHAT_MODERATION_ENABLED = true;
    private static final boolean DEFAULT_CHAT_FILTER_ENABLED = true;
    private static final FilterAction DEFAULT_CHAT_FILTER_ACTION = FilterAction.CENSOR;
    private static final boolean DEFAULT_CHAT_FILTER_WHOLE_WORD = false;
    private static final boolean DEFAULT_CHAT_FILTER_CASE_SENSITIVE = false;
    private static final String DEFAULT_CHAT_CENSOR_CHAR = "*";
    private static final boolean DEFAULT_CHAT_FILTER_NOTIFY_SENDER = true;
    private static final boolean DEFAULT_CHAT_FILTER_EXEMPT_OPS = true;
    private static final boolean DEFAULT_MAINTENANCE_ENABLED = false;
    private static final String DEFAULT_MAINTENANCE_KICK_MESSAGE =
            "The server is currently down for maintenance. Only operators can join right now.";

    // -------------------------------------------------------------------------
    // Volatile cache fields — pre-initialised to defaults so getters are safe
    // even before the config file is loaded.
    // -------------------------------------------------------------------------
    private static volatile double fuelRangeMultiplier = DEFAULT_FUEL_RANGE_MULTIPLIER;
    private static volatile int refuelIntervalTicks = DEFAULT_REFUEL_INTERVAL_TICKS;
    private static volatile boolean clearStructureLoot = DEFAULT_CLEAR_STRUCTURE_LOOT;
    private static volatile boolean disableNether = DEFAULT_DISABLE_NETHER;
    private static volatile boolean disableEnderPearls = DEFAULT_DISABLE_ENDER_PEARLS;
    private static volatile boolean disableBoatThirdPerson = DEFAULT_DISABLE_BOAT_THIRD_PERSON;
    private static volatile int depositYieldMin = DEFAULT_DEPOSIT_YIELD_MIN;
    private static volatile int depositYieldMax = DEFAULT_DEPOSIT_YIELD_MAX;
    private static volatile boolean depositWorldgenEnabled = DEFAULT_DEPOSIT_WORLDGEN_ENABLED;
    private static volatile boolean depositScatter = DEFAULT_DEPOSIT_SCATTER;
    private static volatile int depositWorldgenRarity = DEFAULT_DEPOSIT_WORLDGEN_RARITY;
    private static volatile boolean depositLogPlacements = DEFAULT_DEPOSIT_LOG_PLACEMENTS;
    private static volatile boolean depositHeal = DEFAULT_DEPOSIT_HEAL;
    private static volatile boolean modelTransformDebugEnabled = DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED;
    private static volatile boolean vehicleGunDataCache = DEFAULT_VEHICLE_GUN_DATA_CACHE;
    private static volatile boolean sbwDroneHotPathCache = DEFAULT_SBW_DRONE_HOT_PATH_CACHE;
    private static volatile boolean gtMachineCapabilityCache = DEFAULT_GT_MACHINE_CAPABILITY_CACHE;
    private static volatile boolean ballisticsEnabled = DEFAULT_BALLISTICS_ENABLED;
    private static volatile boolean ballisticsDebugLogging = DEFAULT_BALLISTICS_DEBUG_LOGGING;
    private static volatile boolean playerRanksEnabled = DEFAULT_PLAYER_RANKS_ENABLED;
    /** Reserved: baked from config but currently ignored (see {@link #getRankManifestUrl()}, which is hardcoded). */
    private static volatile String rankManifestUrl = DEFAULT_RANK_MANIFEST_URL;
    private static volatile int rankRefreshIntervalMinutes = DEFAULT_RANK_REFRESH_INTERVAL_MINUTES;
    private static volatile boolean replayRankRequired = DEFAULT_REPLAY_RANK_REQUIRED;
    private static volatile boolean showChatTags = DEFAULT_SHOW_CHAT_TAGS;
    private static volatile boolean showTabTags = DEFAULT_SHOW_TAB_TAGS;
    private static volatile boolean trueDarknessEnabled = DEFAULT_TRUE_DARKNESS_ENABLED;
    private static volatile boolean trueDarknessBlockLightOnly = DEFAULT_TRUE_DARKNESS_BLOCK_LIGHT_ONLY;
    private static volatile boolean trueDarknessIgnoreMoonPhase = DEFAULT_TRUE_DARKNESS_IGNORE_MOON_PHASE;
    private static volatile boolean trueDarknessDarkOverworld = DEFAULT_TRUE_DARKNESS_DARK_OVERWORLD;
    private static volatile boolean trueDarknessDarkNether = DEFAULT_TRUE_DARKNESS_DARK_NETHER;
    private static volatile boolean trueDarknessDarkEnd = DEFAULT_TRUE_DARKNESS_DARK_END;
    private static volatile boolean trueDarknessDarkDefault = DEFAULT_TRUE_DARKNESS_DARK_DEFAULT;
    private static volatile boolean trueDarknessDarkSkyless = DEFAULT_TRUE_DARKNESS_DARK_SKYLESS;
    private static volatile double trueDarknessNetherFog = DEFAULT_TRUE_DARKNESS_NETHER_FOG;
    private static volatile double trueDarknessEndFog = DEFAULT_TRUE_DARKNESS_END_FOG;
    private static volatile boolean blockClientShaders = DEFAULT_BLOCK_CLIENT_SHADERS;
    private static volatile boolean chatModerationEnabled = DEFAULT_CHAT_MODERATION_ENABLED;
    private static volatile boolean chatFilterEnabled = DEFAULT_CHAT_FILTER_ENABLED;
    private static volatile FilterAction chatFilterAction = DEFAULT_CHAT_FILTER_ACTION;
    private static volatile boolean chatFilterWholeWord = DEFAULT_CHAT_FILTER_WHOLE_WORD;
    private static volatile boolean chatFilterCaseSensitive = DEFAULT_CHAT_FILTER_CASE_SENSITIVE;
    private static volatile String chatCensorChar = DEFAULT_CHAT_CENSOR_CHAR;
    private static volatile boolean chatFilterNotifySender = DEFAULT_CHAT_FILTER_NOTIFY_SENDER;
    private static volatile boolean chatFilterExemptOps = DEFAULT_CHAT_FILTER_EXEMPT_OPS;
    private static volatile List<String> chatBlacklist = List.of();
    private static volatile boolean maintenanceEnabled = DEFAULT_MAINTENANCE_ENABLED;
    private static volatile String maintenanceKickMessage = DEFAULT_MAINTENANCE_KICK_MESSAGE;

    // -------------------------------------------------------------------------
    // ModConfigSpec handles
    // -------------------------------------------------------------------------
    private static final ModConfigSpec.DoubleValue FUEL_RANGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue REFUEL_INTERVAL_TICKS;
    private static final ModConfigSpec.BooleanValue CLEAR_STRUCTURE_LOOT;
    private static final ModConfigSpec.BooleanValue DISABLE_NETHER;
    private static final ModConfigSpec.BooleanValue DISABLE_ENDER_PEARLS;
    private static final ModConfigSpec.BooleanValue DISABLE_BOAT_THIRD_PERSON;
    private static final ModConfigSpec.BooleanValue MODEL_TRANSFORM_DEBUG_ENABLED;
    private static final ModConfigSpec.BooleanValue VEHICLE_GUN_DATA_CACHE;
    private static final ModConfigSpec.BooleanValue SBW_DRONE_HOT_PATH_CACHE;
    private static final ModConfigSpec.BooleanValue GT_MACHINE_CAPABILITY_CACHE;
    private static final ModConfigSpec.BooleanValue BALLISTICS_ENABLED;
    private static final ModConfigSpec.BooleanValue BALLISTICS_DEBUG_LOGGING;
    private static final ModConfigSpec.BooleanValue PLAYER_RANKS_ENABLED;
    private static final ModConfigSpec.ConfigValue<String> RANK_MANIFEST_URL;
    private static final ModConfigSpec.IntValue RANK_REFRESH_INTERVAL_MINUTES;
    private static final ModConfigSpec.BooleanValue REPLAY_RANK_REQUIRED;
    private static final ModConfigSpec.BooleanValue SHOW_CHAT_TAGS;
    private static final ModConfigSpec.BooleanValue SHOW_TAB_TAGS;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_ENABLED;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_BLOCK_LIGHT_ONLY;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_IGNORE_MOON_PHASE;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_DARK_OVERWORLD;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_DARK_NETHER;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_DARK_END;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_DARK_DEFAULT;
    private static final ModConfigSpec.BooleanValue TRUE_DARKNESS_DARK_SKYLESS;
    private static final ModConfigSpec.DoubleValue TRUE_DARKNESS_NETHER_FOG;
    private static final ModConfigSpec.DoubleValue TRUE_DARKNESS_END_FOG;
    private static final ModConfigSpec.BooleanValue BLOCK_CLIENT_SHADERS;
    private static final ModConfigSpec.BooleanValue CHAT_MODERATION_ENABLED;
    private static final ModConfigSpec.BooleanValue CHAT_FILTER_ENABLED;
    private static final ModConfigSpec.EnumValue<FilterAction> CHAT_FILTER_ACTION;
    private static final ModConfigSpec.BooleanValue CHAT_FILTER_WHOLE_WORD;
    private static final ModConfigSpec.BooleanValue CHAT_FILTER_CASE_SENSITIVE;
    private static final ModConfigSpec.ConfigValue<String> CHAT_CENSOR_CHAR;
    private static final ModConfigSpec.BooleanValue CHAT_FILTER_NOTIFY_SENDER;
    private static final ModConfigSpec.BooleanValue CHAT_FILTER_EXEMPT_OPS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CHAT_BLACKLIST;
    private static final ModConfigSpec.IntValue DEPOSIT_YIELD_MIN;
    private static final ModConfigSpec.IntValue DEPOSIT_YIELD_MAX;
    private static final ModConfigSpec.BooleanValue DEPOSIT_WORLDGEN_ENABLED;
    private static final ModConfigSpec.BooleanValue DEPOSIT_SCATTER;
    private static final ModConfigSpec.IntValue DEPOSIT_WORLDGEN_RARITY;
    private static final ModConfigSpec.BooleanValue DEPOSIT_LOG_PLACEMENTS;
    private static final ModConfigSpec.BooleanValue DEPOSIT_HEAL;
    private static final ModConfigSpec.BooleanValue MAINTENANCE_ENABLED;
    private static final ModConfigSpec.ConfigValue<String> MAINTENANCE_KICK_MESSAGE;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        FUEL_RANGE_MULTIPLIER = builder
                .comment(
                        "How long a full fuel tank lasts, as a multiplier of the vehicle's NATIVE full-charge range.",
                        "The mB<->FE conversion is anchored per-vehicle to its own energy budget, so 1.0 means a full",
                        "tank drives (or flies) about as far as a full native charge would; 2.0 = twice as far, 0.5 = half.",
                        "This scales every fuelled vehicle uniformly. Per-fuel quality is set separately via the",
                        "WFVehicles KubeJS .fuel(id, ratio) multiplier (e.g. high-octane 2.0 lasts twice as long as diesel 1.0).")
                .defineInRange("fuelRangeMultiplier", DEFAULT_FUEL_RANGE_MULTIPLIER, 0.001, 1000.0);

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

        DISABLE_ENDER_PEARLS = builder
                .comment(
                        "Make ender pearls inert")
                .define("disableEnderPearls", DEFAULT_DISABLE_ENDER_PEARLS);

        DISABLE_BOAT_THIRD_PERSON = builder
                .comment(
                        "Lock the camera to first person while riding a vanilla boat (including chest boats)")
                .define("disableBoatThirdPerson", DEFAULT_DISABLE_BOAT_THIRD_PERSON);

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
                "Deposit worldgen. Deposits generate as chunks are first explored; enabled must be true for ANY of them to appear.",
                "Ambient weighted scatter across the world. Turn scatter off to rely only on KubeJS nodes/regions. rarity is \"1 in N chunks\".")
                .push("worldgen");

        DEPOSIT_WORLDGEN_ENABLED = builder
                .comment("Master switch. When false, NO deposits generate (nodes, regions or scatter), whatever KubeJS registers.")
                .define("enabled", DEFAULT_DEPOSIT_WORLDGEN_ENABLED);

        DEPOSIT_SCATTER = builder
                .define("scatter", DEFAULT_DEPOSIT_SCATTER);

        DEPOSIT_WORLDGEN_RARITY = builder
                .defineInRange("rarity", DEFAULT_DEPOSIT_WORLDGEN_RARITY, 1, Integer.MAX_VALUE);

        DEPOSIT_HEAL = builder
                .comment(
                        "Retro-fit healer: as already-generated chunks reload, stamp in any node/region deposit they should host but",
                        "were missing (e.g. because deposits were off or unregistered when the chunk first generated). Deterministic and",
                        "one-shot per chunk (never double-placed, never resurrected after a deposit is drilled away). Scatter can't be",
                        "healed. Harmless when off or when there are no node/region placements.")
                .define("healExistingChunks", DEFAULT_DEPOSIT_HEAL);

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

        builder.comment(
                "Server-enforced True Darkness (mod 'darkness'). True Darkness is a client-only render mod whose",
                "settings a player could otherwise change via config/darkness.properties or its in-game config",
                "screen. WFCore pins these values into the mod every frame and, on a server, pushes THIS section to",
                "each client on join so the server's copy wins over the client's own. Editing darkness.properties or",
                "the config screen then has no effect.")
                .push("trueDarkness");

        TRUE_DARKNESS_ENABLED = builder
                .comment("Master switch. When off, WFCore stops enforcing and True Darkness falls back to its own config.")
                .define("enabled", DEFAULT_TRUE_DARKNESS_ENABLED);

        TRUE_DARKNESS_BLOCK_LIGHT_ONLY = builder
                .comment("Only darken block light; areas lit by the sky stay lit. false = full darkness even under open sky at night.")
                .define("blockLightOnly", DEFAULT_TRUE_DARKNESS_BLOCK_LIGHT_ONLY);

        TRUE_DARKNESS_IGNORE_MOON_PHASE = builder
                .comment("Night is fully dark regardless of moon phase. false = a full moon lightens the night.")
                .define("ignoreMoonPhase", DEFAULT_TRUE_DARKNESS_IGNORE_MOON_PHASE);

        TRUE_DARKNESS_DARK_OVERWORLD = builder
                .comment("Enable the darkness effect in the Overworld.")
                .define("darkOverworld", DEFAULT_TRUE_DARKNESS_DARK_OVERWORLD);

        TRUE_DARKNESS_DARK_NETHER = builder
                .comment("Enable the darkness effect in the Nether (moot while the Nether is disabled).")
                .define("darkNether", DEFAULT_TRUE_DARKNESS_DARK_NETHER);

        TRUE_DARKNESS_DARK_END = builder
                .comment("Enable the darkness effect in The End.")
                .define("darkEnd", DEFAULT_TRUE_DARKNESS_DARK_END);

        TRUE_DARKNESS_DARK_DEFAULT = builder
                .comment("Enable the darkness effect in modded dimensions that have a sky.")
                .define("darkDefault", DEFAULT_TRUE_DARKNESS_DARK_DEFAULT);

        TRUE_DARKNESS_DARK_SKYLESS = builder
                .comment("Enable the darkness effect in modded dimensions with no sky. Off by default to avoid breaking mining/utility dimensions.")
                .define("darkSkyless", DEFAULT_TRUE_DARKNESS_DARK_SKYLESS);

        TRUE_DARKNESS_NETHER_FOG = builder
                .comment("Nether fog brightness floor (0 = darkest, 1 = vanilla). Only applies while darkNether is on.")
                .defineInRange("netherFog", DEFAULT_TRUE_DARKNESS_NETHER_FOG, 0.0, 1.0);

        TRUE_DARKNESS_END_FOG = builder
                .comment("End fog brightness floor (0 = darkest, 1 = vanilla). Only applies while darkEnd is on.")
                .defineInRange("endFog", DEFAULT_TRUE_DARKNESS_END_FOG, 0.0, 1.0);

        builder.pop();

        builder.comment(
                "Server-enforced shader lock, the anti-bypass companion to [trueDarkness]. Oculus/Iris shaders",
                "replace vanilla lighting entirely, so a shaderpack (or a fullbright shader) would render straight",
                "through hardcore darkness. When enabled, a DEDICATED server tells each client on join to run with",
                "shaders disabled (WFCore forces Iris onto the vanilla pipeline, which True Darkness then darkens);",
                "the client's own shader toggle cannot re-enable them while connected. Clients without Oculus ignore",
                "this, and single-player is never affected.")
                .push("shaderControl");

        BLOCK_CLIENT_SHADERS = builder
                .comment("Block Oculus/Iris shaders on dedicated servers while True Darkness enforcement is on, so",
                        "players cannot shade around hardcore darkness. Off = shaders are left to the client.")
                .define("blockClientShaders", DEFAULT_BLOCK_CLIENT_SHADERS);

        builder.pop();

        builder.comment(
                "Server-side chat moderation. Mutes are managed in-game with /wfcore_chat (op level 2) and persist",
                "in the world save; the word blacklist below is merged with any words added via",
                "/wfcore_chat filter add. Hooks player chat only - it does not touch /me, /say or /msg.")
                .push("chatModeration");

        CHAT_MODERATION_ENABLED = builder
                .comment("Master switch. When off, chat is never intercepted: no mutes and no filtering apply.")
                .define("enabled", DEFAULT_CHAT_MODERATION_ENABLED);

        builder.comment("Blacklist word/phrase filter applied to every chat message.").push("filter");

        CHAT_FILTER_ENABLED = builder
                .comment("Enable the blacklist filter. Mutes still work when this is off.")
                .define("enabled", DEFAULT_CHAT_FILTER_ENABLED);

        CHAT_FILTER_ACTION = builder
                .comment("What to do with a message that contains a blacklisted word:",
                        "BLOCK = cancel the whole message; CENSOR = replace only the offending word(s).")
                .defineEnum("action", DEFAULT_CHAT_FILTER_ACTION);

        CHAT_BLACKLIST = builder
                .comment("Blacklisted words/phrases. Example: blacklist = [\"badword\", \"a nasty phrase\"].",
                        "Matched case-insensitively unless caseSensitive is true.")
                .defineList("blacklist", List.of(), o -> o instanceof String);

        CHAT_FILTER_WHOLE_WORD = builder
                .comment("Match whole words only. true: \"ass\" ignores \"class\". false: substring match (more",
                        "aggressive, catches embedded/leetspeak but risks false positives like Scunthorpe).")
                .define("matchWholeWord", DEFAULT_CHAT_FILTER_WHOLE_WORD);

        CHAT_FILTER_CASE_SENSITIVE = builder
                .comment("Match case exactly. Usually false so BadWord and badword both trip the filter.")
                .define("caseSensitive", DEFAULT_CHAT_FILTER_CASE_SENSITIVE);

        CHAT_CENSOR_CHAR = builder
                .comment("Character a censored word is replaced with (CENSOR action). First character is used.")
                .define("censorChar", DEFAULT_CHAT_CENSOR_CHAR);

        CHAT_FILTER_NOTIFY_SENDER = builder
                .comment("Privately tell the sender when their message was blocked or censored.")
                .define("notifySender", DEFAULT_CHAT_FILTER_NOTIFY_SENDER);

        CHAT_FILTER_EXEMPT_OPS = builder
                .comment("Players with op permission level >= 2 bypass the filter (mutes still apply to them).")
                .define("exemptOps", DEFAULT_CHAT_FILTER_EXEMPT_OPS);

        builder.pop(2);

        builder.comment(
                "Maintenance mode. While enabled, only server operators may be connected: non-operators are kicked",
                "on join, and any non-operators already online are kicked the moment it is switched on. Toggle it",
                "live with /wfcore maintenance on|off|status (op level 2). The 'enabled' flag below is written back",
                "when toggled, so the lock survives a restart - the server stays locked until an operator turns it off.")
                .push("maintenance");

        MAINTENANCE_ENABLED = builder
                .comment("Whether maintenance mode is currently active. Normally toggled via /wfcore maintenance, not edited by hand.")
                .define("enabled", DEFAULT_MAINTENANCE_ENABLED);

        MAINTENANCE_KICK_MESSAGE = builder
                .comment("Disconnect message shown to non-operators kicked or refused while maintenance mode is active.")
                .define("kickMessage", DEFAULT_MAINTENANCE_KICK_MESSAGE);

        builder.pop();

        builder.comment(
                "Stopgap server-thread optimisations for bugs in the currently shipped versions of other mods.",
                "Each of these is expected to become redundant once the mod in question is updated; leaving one on",
                "against a fixed build is harmless (it just never wins anything) but they are togglable so a bad",
                "interaction can be ruled out without a rebuild.")
                .push("performance");

        VEHICLE_GUN_DATA_CACHE = builder
                .comment(
                        "Memoise Superb Warfare's VehicleEntity.getGunDataMap for the duration of a tick.",
                        "SBW builds before the upstream 'gunDataMapCache' fix rebuild that map on every call, copying",
                        "each weapon's ItemStack and re-resolving it through a Guava cache that is keyed by identity",
                        "(weakKeys), so the copy can never hit and every lookup reconstructs GunData from NBT. The map",
                        "is read several times per vehicle per tick, which measured at ~45s of server thread per hour.",
                        "The cache is dropped when the game time advances and whenever setGunDataMap writes back, so a",
                        "read-modify-write cycle still sees its own change. Server side only.",
                        "Turn this off if you are running an SBW build that already has the upstream fix.")
                .define("vehicleGunDataCache", DEFAULT_VEHICLE_GUN_DATA_CACHE);

        SBW_DRONE_HOT_PATH_CACHE = builder
                .comment(
                        "Take three hot paths out of the SBW Drone Warfare addon (modid sbwdroneconfig).",
                        "Its isMonitor and isSupportedStackableStack checks resolve an item's registry name on every",
                        "call just to compare it, which is a reverse registry lookup (a linear seek through Guava's",
                        "HashBiMap inverse) on a per-ItemStack path; together they measured ~28s of server thread per",
                        "hour. Both depend only on the item, which cannot change once registries freeze, so the result",
                        "is memoised per item using the addon's own answer.",
                        "Separately, a drone sitting in water re-runs its full control disconnect every tick, which",
                        "rebroadcasts an unchanged spotlight state to every player on the server (~10s/hour); those",
                        "no-op broadcasts are dropped. Directed syncs and real state changes are never skipped.")
                .define("sbwDroneHotPathCache", DEFAULT_SBW_DRONE_HOT_PATH_CACHE);

        GT_MACHINE_CAPABILITY_CACHE = builder
                .comment(
                        "Cache GTCEu's MetaMachineBlockEntity.getCapability for the duration of a tick.",
                        "GTCEu builds a new LazyOptional on every call and re-scans the machine's traits to do it,",
                        "rather than handing back a cached instance the way Forge's capability contract expects. The",
                        "pipe networks resolve their endpoint handlers every tick and a machine with several energy or",
                        "item sources pointed at it is resolved once per source, which measured 64.8s of server thread",
                        "per hour.",
                        "Scope is one tick on purpose: per-side capabilities really do change (IO config, covers, the",
                        "machine being broken) and a longer-lived cache risks a machine that silently stops accepting",
                        "power. Entries that have been invalidated mid-tick are recomputed rather than served.",
                        "Turn this off first if machines start behaving oddly around pipes or covers.")
                .define("gtMachineCapabilityCache", DEFAULT_GT_MACHINE_CAPABILITY_CACHE);

        builder.pop();

        SPEC = builder.build();
    }

    private WFCoreConfig() {}

    // -------------------------------------------------------------------------
    // Public API — unchanged signatures
    // -------------------------------------------------------------------------

    /**
     * Global multiplier on how long a full fuel tank lasts, relative to each vehicle's native full-charge range.
     * {@code 1.0} = native range, {@code 2.0} = twice as long, {@code 0.5} = half. The absolute mB->FE conversion
     * is derived per-vehicle from its own {@code MaxEnergy} (see {@code SuperbWarfareInvMixin}); this only scales it.
     */
    public static double getFuelRangeMultiplier() {
        return fuelRangeMultiplier;
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

    /** When true, ender pearls are inert: throwing one does nothing and a thrown pearl never teleports its owner. */
    public static boolean isEnderPearlsDisabled() {
        return disableEnderPearls;
    }

    /** When true, the camera is held in first person while the player rides a vanilla boat. */
    public static boolean isBoatThirdPersonDisabled() {
        return disableBoatThirdPerson;
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

    /**
     * When true (and worldgen is enabled), the load-time healer retro-fits missing node/region deposits into
     * already-generated chunks. See {@code DepositRetrofitHandler}.
     */
    public static boolean isDepositHealEnabled() {
        return depositHeal;
    }

    /** Dev tool gate: the numpad model-transform debugger (see IAnimatedMachine) only arms when this is true. */
    public static boolean isModelTransformDebugEnabled() {
        return modelTransformDebugEnabled;
    }

    /** Whether to memoise SBW's {@code VehicleEntity.getGunDataMap} per tick (stopgap; see config comment). */
    public static boolean isVehicleGunDataCacheEnabled() {
        return vehicleGunDataCache;
    }

    /** Whether to memoise the SBW drone addon's registry-name checks and drop no-op spotlight broadcasts. */
    public static boolean isSbwDroneHotPathCacheEnabled() {
        return sbwDroneHotPathCache;
    }

    /** Whether to cache GTCEu machine capability lookups for the duration of a tick. */
    public static boolean isGtMachineCapabilityCacheEnabled() {
        return gtMachineCapabilityCache;
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

    public static String getRankManifestUrl() {
        return DEFAULT_RANK_MANIFEST_URL;
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

    /** Master switch for server-enforced True Darkness. When off, True Darkness uses its own config. */
    public static boolean isTrueDarknessEnabled() {
        return trueDarknessEnabled;
    }

    /** When true, only block light is darkened; sky-lit areas stay lit. */
    public static boolean isTrueDarknessBlockLightOnly() {
        return trueDarknessBlockLightOnly;
    }

    /** When true, night is fully dark regardless of moon phase. */
    public static boolean isTrueDarknessIgnoreMoonPhase() {
        return trueDarknessIgnoreMoonPhase;
    }

    /** When true, the darkness effect applies in the Overworld. */
    public static boolean isTrueDarknessDarkOverworld() {
        return trueDarknessDarkOverworld;
    }

    /** When true, the darkness effect applies in the Nether. */
    public static boolean isTrueDarknessDarkNether() {
        return trueDarknessDarkNether;
    }

    /** When true, the darkness effect applies in The End. */
    public static boolean isTrueDarknessDarkEnd() {
        return trueDarknessDarkEnd;
    }

    /** When true, the darkness effect applies in modded dimensions with a sky. */
    public static boolean isTrueDarknessDarkDefault() {
        return trueDarknessDarkDefault;
    }

    /** When true, the darkness effect applies in modded dimensions with no sky. */
    public static boolean isTrueDarknessDarkSkyless() {
        return trueDarknessDarkSkyless;
    }

    /** Nether fog brightness floor (0 darkest .. 1 vanilla), used only while darkNether is on. */
    public static double getTrueDarknessNetherFog() {
        return trueDarknessNetherFog;
    }

    /** End fog brightness floor (0 darkest .. 1 vanilla), used only while darkEnd is on. */
    public static double getTrueDarknessEndFog() {
        return trueDarknessEndFog;
    }

    /** When true, dedicated servers make clients disable Oculus/Iris shaders so hardcore darkness can't be bypassed. */
    public static boolean isBlockClientShaders() {
        return blockClientShaders;
    }

    /** Master switch for server-side chat moderation (mutes + blacklist filter). */
    public static boolean isChatModerationEnabled() {
        return chatModerationEnabled;
    }

    /** When true, the blacklist word filter is applied to chat messages. */
    public static boolean isChatFilterEnabled() {
        return chatFilterEnabled;
    }

    /** Whether a blacklisted message is cancelled ({@link FilterAction#BLOCK}) or masked ({@link FilterAction#CENSOR}). */
    public static FilterAction getChatFilterAction() {
        return chatFilterAction;
    }

    /** When true, blacklist words match whole words only; when false, substring matches too. */
    public static boolean isChatFilterWholeWord() {
        return chatFilterWholeWord;
    }

    /** When true, blacklist matching is case-sensitive. */
    public static boolean isChatFilterCaseSensitive() {
        return chatFilterCaseSensitive;
    }

    /** The character a censored word is replaced with (first char of the configured string). */
    public static String getChatCensorChar() {
        return chatCensorChar;
    }

    /** When true, the sender is privately told their message was blocked or censored. */
    public static boolean isChatFilterNotifySender() {
        return chatFilterNotifySender;
    }

    /** When true, players with op permission level >= 2 bypass the blacklist filter. */
    public static boolean isChatFilterExemptOps() {
        return chatFilterExemptOps;
    }

    /** The static blacklist from {@code wfcore.toml} (merged with runtime words by the moderator). */
    public static List<String> getChatBlacklist() {
        return chatBlacklist;
    }

    /**
     * When true, maintenance mode is active: only operators may be connected and everyone else is kicked on
     * join. Toggled live via {@code /wfcore maintenance} and persisted to {@code wfcore.toml}.
     */
    public static boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    /** Disconnect message shown to non-operators kicked or refused while maintenance mode is active. */
    public static String getMaintenanceKickMessage() {
        return maintenanceKickMessage;
    }

    /**
     * Toggle maintenance mode at runtime. Updates the cached value immediately and, when the config is
     * loaded, writes the new state to {@code wfcore.toml} so it survives a restart. Persistence failures
     * are logged but never block the in-memory change.
     */
    public static void setMaintenanceEnabled(boolean enabled) {
        maintenanceEnabled = enabled;
        try {
            MAINTENANCE_ENABLED.set(enabled);
            SPEC.save();
        } catch (RuntimeException e) {
            WFCore.LOGGER.warn("[wfcore-maintenance] could not persist maintenance state to wfcore.toml: {}",
                    e.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Bake — called by WFCore on ModConfigEvent
    // -------------------------------------------------------------------------

    public static void bake() {
        fuelRangeMultiplier = FUEL_RANGE_MULTIPLIER.get();
        refuelIntervalTicks = REFUEL_INTERVAL_TICKS.get();
        clearStructureLoot = CLEAR_STRUCTURE_LOOT.get();
        disableNether = DISABLE_NETHER.get();
        disableEnderPearls = DISABLE_ENDER_PEARLS.get();
        disableBoatThirdPerson = DISABLE_BOAT_THIRD_PERSON.get();
        modelTransformDebugEnabled = MODEL_TRANSFORM_DEBUG_ENABLED.get();
        vehicleGunDataCache = VEHICLE_GUN_DATA_CACHE.get();
        sbwDroneHotPathCache = SBW_DRONE_HOT_PATH_CACHE.get();
        gtMachineCapabilityCache = GT_MACHINE_CAPABILITY_CACHE.get();
        ballisticsEnabled = BALLISTICS_ENABLED.get();
        ballisticsDebugLogging = BALLISTICS_DEBUG_LOGGING.get();
        playerRanksEnabled = PLAYER_RANKS_ENABLED.get();
        rankManifestUrl = RANK_MANIFEST_URL.get();
        rankRefreshIntervalMinutes = RANK_REFRESH_INTERVAL_MINUTES.get();
        replayRankRequired = REPLAY_RANK_REQUIRED.get();
        showChatTags = SHOW_CHAT_TAGS.get();
        showTabTags = SHOW_TAB_TAGS.get();
        trueDarknessEnabled = TRUE_DARKNESS_ENABLED.get();
        trueDarknessBlockLightOnly = TRUE_DARKNESS_BLOCK_LIGHT_ONLY.get();
        trueDarknessIgnoreMoonPhase = TRUE_DARKNESS_IGNORE_MOON_PHASE.get();
        trueDarknessDarkOverworld = TRUE_DARKNESS_DARK_OVERWORLD.get();
        trueDarknessDarkNether = TRUE_DARKNESS_DARK_NETHER.get();
        trueDarknessDarkEnd = TRUE_DARKNESS_DARK_END.get();
        trueDarknessDarkDefault = TRUE_DARKNESS_DARK_DEFAULT.get();
        trueDarknessDarkSkyless = TRUE_DARKNESS_DARK_SKYLESS.get();
        trueDarknessNetherFog = TRUE_DARKNESS_NETHER_FOG.get();
        trueDarknessEndFog = TRUE_DARKNESS_END_FOG.get();
        blockClientShaders = BLOCK_CLIENT_SHADERS.get();
        chatModerationEnabled = CHAT_MODERATION_ENABLED.get();
        chatFilterEnabled = CHAT_FILTER_ENABLED.get();
        chatFilterAction = CHAT_FILTER_ACTION.get();
        chatFilterWholeWord = CHAT_FILTER_WHOLE_WORD.get();
        chatFilterCaseSensitive = CHAT_FILTER_CASE_SENSITIVE.get();
        chatCensorChar = CHAT_CENSOR_CHAR.get();
        chatFilterNotifySender = CHAT_FILTER_NOTIFY_SENDER.get();
        chatFilterExemptOps = CHAT_FILTER_EXEMPT_OPS.get();
        maintenanceEnabled = MAINTENANCE_ENABLED.get();
        maintenanceKickMessage = MAINTENANCE_KICK_MESSAGE.get();
        List<String> blacklist = new ArrayList<>();
        for (String word : CHAT_BLACKLIST.get()) {
            blacklist.add(word);
        }
        chatBlacklist = List.copyOf(blacklist);
        // Feed the client-side holder so single-player / no-server-support sessions still enforce locally.
        // On a server the join packet (DarknessServerHandler) overrides this with the server's authoritative copy.
        DarknessEnforcement.set(trueDarknessEnabled, trueDarknessBlockLightOnly, trueDarknessIgnoreMoonPhase,
                trueDarknessDarkOverworld, trueDarknessDarkNether, trueDarknessDarkEnd, trueDarknessDarkDefault,
                trueDarknessDarkSkyless, trueDarknessNetherFog, trueDarknessEndFog);
        depositYieldMin = DEPOSIT_YIELD_MIN.get();
        depositYieldMax = Math.max(DEPOSIT_YIELD_MAX.get(), depositYieldMin);
        depositWorldgenEnabled = DEPOSIT_WORLDGEN_ENABLED.get();
        depositScatter = DEPOSIT_SCATTER.get();
        depositWorldgenRarity = DEPOSIT_WORLDGEN_RARITY.get();
        depositLogPlacements = DEPOSIT_LOG_PLACEMENTS.get();
        depositHeal = DEPOSIT_HEAL.get();
        // Vehicle overrides + foliage breakers now come from the WFVehicles KubeJS API (registered at startup).
        WFCore.LOGGER.info("Loaded WFCore TOML config: fuel range multiplier {}, refuel interval {} ticks",
                fuelRangeMultiplier, refuelIntervalTicks);
    }

}
