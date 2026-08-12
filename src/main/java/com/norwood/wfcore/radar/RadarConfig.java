package com.norwood.wfcore.radar;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import com.norwood.wfcore.WFCore;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Radar target whitelist. Keyed directly by block registry name (GregTech Modern machines are
 * identified by their registry name rather than block/item meta), mapping to a richness value.
 */
public final class RadarConfig {

    /** DBSCAN defaults, used when {@code /wfcore_radar scan} is run without explicit arguments. */
    public static final int DEFAULT_EPS = 200;
    public static final int DEFAULT_MIN_PTS = 10;

    public static final int DEFAULT_CALIB_MIN_DISTANCE = 2000;
    public static final int DEFAULT_CALIB_MAX_DISTANCE = 5000;
    public static final int DEFAULT_CALIB_TOLERANCE = 128;
    public static final int DEFAULT_CALIB_BASE_COUNT = 3;
    public static final int DEFAULT_CALIB_SEA_LEVEL = 63;
    public static final int DEFAULT_CALIB_BORDER_MARGIN = 16;

    private static final Object2IntOpenHashMap<ResourceLocation> WHITELIST = new Object2IntOpenHashMap<>();

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MACHINES;
    private static final ForgeConfigSpec.IntValue EPS;
    private static final ForgeConfigSpec.IntValue MIN_PTS;
    private static final ForgeConfigSpec.IntValue CALIB_MIN_DISTANCE;
    private static final ForgeConfigSpec.IntValue CALIB_MAX_DISTANCE;
    private static final ForgeConfigSpec.IntValue CALIB_TOLERANCE;
    private static final ForgeConfigSpec.IntValue CALIB_BASE_COUNT;
    private static final ForgeConfigSpec.IntValue CALIB_SEA_LEVEL;
    private static final ForgeConfigSpec.IntValue CALIB_BORDER_MARGIN;
    public static final ForgeConfigSpec SPEC;

    // Baked DBSCAN tuning: the default eps/minPts a scan uses when no per-scan override is given. Pre-seeded
    // with the config defaults so they are valid even before the first bake().
    private static int eps = DEFAULT_EPS;
    private static int minPts = DEFAULT_MIN_PTS;

    private static int calibMinDistance = DEFAULT_CALIB_MIN_DISTANCE;
    private static int calibMaxDistance = DEFAULT_CALIB_MAX_DISTANCE;
    private static int calibTolerance = DEFAULT_CALIB_TOLERANCE;
    private static int calibBaseCount = DEFAULT_CALIB_BASE_COUNT;
    private static int calibSeaLevel = DEFAULT_CALIB_SEA_LEVEL;
    private static int calibBorderMargin = DEFAULT_CALIB_BORDER_MARGIN;

    // KubeJS overrides (via the WFRadar binding). Re-applied on top of the config whitelist at the end of every
    // bake() so a pack's additions/removals/tunables survive a config reload instead of being clobbered by it.
    private static final Object2IntOpenHashMap<ResourceLocation> SCRIPT_ADDITIONS = new Object2IntOpenHashMap<>();
    private static final Set<ResourceLocation> SCRIPT_REMOVALS = new HashSet<>();
    private static Integer scriptEps;
    private static Integer scriptMinPts;
    private static volatile boolean baked = false;

    static {
        WHITELIST.defaultReturnValue(0);
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MACHINES = builder.comment(
                "Radar target whitelist.",
                "Every machine/block listed here is visible to the radar and contributes to a base's score.",
                "Use registry names directly; GregTech Modern machines are keyed by their registry name.",
                "Format per entry: \"registryId=value\" where value is the richness contributed to the combined base score (defaults to 1 if the =value is omitted).",
                "Example: \"gtceu:electric_blast_furnace=10\"")
                .defineList("machines", java.util.List.of(
                        "gtceu:electric_blast_furnace=10",
                        "gtceu:large_chemical_reactor=25",
                        "minecraft:furnace=1"),
                        o -> o instanceof String);

        builder.comment("DBSCAN clustering tuning. These are the defaults a radar (and /wfcore_radar scan) use;",
                "the command accepts \"/wfcore_radar scan <eps> <minPts>\" to override them per scan for dialing in.")
                .push("clustering");
        EPS = builder.comment(
                "Neighbourhood radius in blocks: two targets within this distance count as neighbours.",
                "Larger values merge nearby bases into one cluster; smaller values split them apart.")
                .defineInRange("eps", DEFAULT_EPS, 1, 30_000_000);
        MIN_PTS = builder.comment(
                "Minimum points: the fewest neighbouring targets needed to seed a cluster.",
                "Higher values report only dense bases; lower values pick up smaller groupings (more noise).")
                .defineInRange("minPts", DEFAULT_MIN_PTS, 1, 100_000);
        builder.pop();

        builder.comment("Satellite Distance Calibrator scan-session tuning.",
                "A scan session generates target points the player must build calibrators at before scanning.")
                .push("calibrator");
        CALIB_MIN_DISTANCE = builder.comment("Closest a generated calibrator target may be to the radar (blocks).")
                .defineInRange("minDistance", DEFAULT_CALIB_MIN_DISTANCE, 1, 30_000_000);
        CALIB_MAX_DISTANCE = builder.comment("Farthest a generated calibrator target may be from the radar (blocks).")
                .defineInRange("maxDistance", DEFAULT_CALIB_MAX_DISTANCE, 1, 30_000_000);
        CALIB_TOLERANCE = builder.comment(
                "How close (blocks, horizontal) a built calibrator must be to a target point to satisfy it.")
                .defineInRange("tolerance", DEFAULT_CALIB_TOLERANCE, 1, 30_000_000);
        CALIB_BASE_COUNT = builder.comment(
                "Calibrators required at the radar's minimum tier (HV); +1 per voltage tier above that.")
                .defineInRange("baseCount", DEFAULT_CALIB_BASE_COUNT, 1, 32);
        CALIB_SEA_LEVEL = builder.comment("Minimum Y a calibrator's controller must sit at for the structure to form.")
                .defineInRange("seaLevel", DEFAULT_CALIB_SEA_LEVEL, -64, 320);
        CALIB_BORDER_MARGIN = builder.comment(
                "Keep generated targets at least this many blocks inside the world border.")
                .defineInRange("borderMargin", DEFAULT_CALIB_BORDER_MARGIN, 0, 30_000_000);
        builder.pop();

        SPEC = builder.build();
    }

    private RadarConfig() {}

    public static boolean isWhitelisted(ResourceLocation id) {
        return WHITELIST.containsKey(id);
    }

    public static int getValue(ResourceLocation id) {
        return WHITELIST.getInt(id);
    }

    /**
     * Read-only snapshot of every radar-detectable target (block registry id -> richness value), sorted by id.
     * Boxed and copied on purpose: this is a one-shot diagnostic/listing view (e.g. {@code /wfcore_radar list}),
     * not a hot path, so it must not alias the live fastutil map or its reused entry objects.
     */
    public static synchronized List<Map.Entry<ResourceLocation, Integer>> snapshot() {
        List<Map.Entry<ResourceLocation, Integer>> out = new ArrayList<>(WHITELIST.size());
        for (var e : WHITELIST.object2IntEntrySet()) {
            out.add(Map.entry(e.getKey(), e.getIntValue()));
        }
        out.sort(Map.Entry.comparingByKey());
        return out;
    }

    /** Configured default DBSCAN neighbourhood radius (blocks); overridable per scan. */
    public static int getEps() {
        return eps;
    }

    /** Configured default DBSCAN minimum cluster size; overridable per scan. */
    public static int getMinPts() {
        return minPts;
    }

    /** Closest a generated calibrator target may be to the radar (blocks). */
    public static int getCalibratorMinDistance() {
        return calibMinDistance;
    }

    /** Farthest a generated calibrator target may be from the radar (blocks). */
    public static int getCalibratorMaxDistance() {
        return calibMaxDistance;
    }

    /** How close (blocks, horizontal) a built calibrator must be to a target point to satisfy it. */
    public static int getCalibratorTolerance() {
        return calibTolerance;
    }

    /** Calibrators required at the radar's minimum tier (HV); +1 per voltage tier above that. */
    public static int getCalibratorBaseCount() {
        return calibBaseCount;
    }

    /** Minimum Y a calibrator's controller must sit at for the structure to form. */
    public static int getCalibratorSeaLevel() {
        return calibSeaLevel;
    }

    /** Keep generated targets at least this many blocks inside the world border. */
    public static int getCalibratorBorderMargin() {
        return calibBorderMargin;
    }

    /** KubeJS: force {@code id} onto the whitelist with {@code value}, overriding the config. */
    public static synchronized void scriptAdd(ResourceLocation id, int value) {
        SCRIPT_ADDITIONS.put(id, value);
        SCRIPT_REMOVALS.remove(id);
        if (baked) {
            WHITELIST.put(id, value);
        }
    }

    /** KubeJS: force {@code id} off the whitelist even if the config lists it. */
    public static synchronized void scriptRemove(ResourceLocation id) {
        SCRIPT_REMOVALS.add(id);
        SCRIPT_ADDITIONS.removeInt(id);
        if (baked) {
            WHITELIST.removeInt(id);
        }
    }

    /** KubeJS: override the default DBSCAN neighbourhood radius. */
    public static synchronized void scriptEps(int value) {
        scriptEps = value;
        if (baked) {
            eps = value;
        }
    }

    /** KubeJS: override the default DBSCAN minimum cluster size. */
    public static synchronized void scriptMinPts(int value) {
        scriptMinPts = value;
        if (baked) {
            minPts = value;
        }
    }

    public static synchronized void bake() {
        eps = EPS.get();
        minPts = MIN_PTS.get();

        calibMinDistance = CALIB_MIN_DISTANCE.get();
        calibMaxDistance = Math.max(CALIB_MAX_DISTANCE.get(), calibMinDistance);
        calibTolerance = CALIB_TOLERANCE.get();
        calibBaseCount = CALIB_BASE_COUNT.get();
        calibSeaLevel = CALIB_SEA_LEVEL.get();
        calibBorderMargin = CALIB_BORDER_MARGIN.get();

        WHITELIST.clear();
        for (String raw : MACHINES.get()) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            String idText = (eq < 0 ? entry : entry.substring(0, eq)).trim();
            int value = 1;
            if (eq >= 0) {
                try {
                    value = Integer.parseInt(entry.substring(eq + 1).trim());
                } catch (NumberFormatException e) {
                    WFCore.LOGGER.warn("Ignoring invalid radar value in '{}'", raw);
                }
            }
            ResourceLocation id = ResourceLocation.tryParse(idText);
            if (id == null) {
                WFCore.LOGGER.warn("Ignoring invalid radar target id '{}'", idText);
                continue;
            }
            WHITELIST.put(id, value);
        }

        // Re-apply KubeJS overrides on top of the freshly-loaded config so they win and survive reloads.
        for (var override : SCRIPT_ADDITIONS.object2IntEntrySet()) {
            WHITELIST.put(override.getKey(), override.getIntValue());
        }
        for (ResourceLocation removed : SCRIPT_REMOVALS) {
            WHITELIST.removeInt(removed);
        }
        if (scriptEps != null) {
            eps = scriptEps;
        }
        if (scriptMinPts != null) {
            minPts = scriptMinPts;
        }
        baked = true;

        WFCore.LOGGER.info("Loaded {} radar targets (DBSCAN eps={}, minPts={}); {} script override(s)",
                WHITELIST.size(), eps, minPts, SCRIPT_ADDITIONS.size() + SCRIPT_REMOVALS.size());
    }
}
