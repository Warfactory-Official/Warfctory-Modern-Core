package com.norwood.wfcore.radar;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

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

    public static final int DEFAULT_CALIB_BORDER_MARGIN = 16;

    private static final Object2IntOpenHashMap<ResourceLocation> WHITELIST = new Object2IntOpenHashMap<>();

    private static final ModConfigSpec.ConfigValue<List<? extends String>> MACHINES;
    private static final ModConfigSpec.IntValue CALIB_BORDER_MARGIN;
    private static final ProfileSpec RADAR_SPEC;
    private static final ProfileSpec SEISMIC_SPEC;
    public static final ModConfigSpec SPEC;

    private static int calibBorderMargin = DEFAULT_CALIB_BORDER_MARGIN;

    // KubeJS overrides (via the WFRadar binding). Re-applied on top of the config whitelist at the end of every
    // bake() so a pack's additions/removals survive a config reload instead of being clobbered by it.
    private static final Object2IntOpenHashMap<ResourceLocation> SCRIPT_ADDITIONS = new Object2IntOpenHashMap<>();
    private static final Set<ResourceLocation> SCRIPT_REMOVALS = new HashSet<>();
    private static volatile boolean baked = false;

    /** Per-profile keys under the current builder path; detection keys only when {@code detection}. */
    private record ProfileSpec(RadarProfile profile, ModConfigSpec.IntValue eps, ModConfigSpec.IntValue minPts,
                               ModConfigSpec.IntValue calibMin, ModConfigSpec.IntValue calibMax,
                               ModConfigSpec.IntValue calibTolerance, ModConfigSpec.IntValue calibBaseCount,
                               ModConfigSpec.IntValue minTier, ModConfigSpec.IntValue baseCWUt,
                               ModConfigSpec.IntValue scanTicks, ModConfigSpec.IntValue range,
                               ModConfigSpec.IntValue depthThreshold, ModConfigSpec.DoubleValue deepMultiplier,
                               ModConfigSpec.DoubleValue shallowMultiplier) {

        static ProfileSpec define(ModConfigSpec.Builder builder, RadarProfile profile, boolean detection) {
            RadarProfile.Defaults d = profile.defaults;
            builder.comment("DBSCAN clustering tuning. These are the defaults a scan uses;",
                    "\"/wfcore_radar scan <eps> <minPts>\" overrides them per command scan for dialing in.")
                    .push("clustering");
            var eps = builder.comment(
                    "Neighbourhood radius in blocks: two targets within this distance count as neighbours.",
                    "Larger values merge nearby bases into one cluster; smaller values split them apart.")
                    .defineInRange("eps", d.eps(), 1, 30_000_000);
            var minPts = builder.comment(
                    "Minimum points: the fewest neighbouring targets needed to seed a cluster.",
                    "Higher values report only dense bases; lower values pick up smaller groupings (more noise).")
                    .defineInRange("minPts", d.minPts(), 1, 100_000);
            builder.pop();

            builder.comment("Satellite Distance Calibrator scan-session tuning.",
                    "A scan session generates target points the player must build calibrators at before scanning.")
                    .push("calibrator");
            var calibMin = builder.comment("Closest a generated calibrator target may be to the machine (blocks).")
                    .defineInRange("minDistance", d.calibMinDistance(), 1, 30_000_000);
            var calibMax = builder.comment("Farthest a generated calibrator target may be from the machine (blocks).")
                    .defineInRange("maxDistance", d.calibMaxDistance(), 1, 30_000_000);
            var calibTolerance = builder.comment(
                    "How close (blocks, horizontal) a built calibrator must be to a target point to satisfy it.")
                    .defineInRange("tolerance", d.calibTolerance(), 1, 30_000_000);
            var calibBaseCount = builder.comment(
                    "Calibrators required at scan.minTier; +1 per voltage tier above that.")
                    .defineInRange("baseCount", d.calibBaseCount(), 1, 32);
            builder.pop();

            builder.comment("Scan cost. Energy = VA[tier] EU/t while scanning.").push("scan");
            var minTier = builder.comment("Lowest energy-hatch voltage tier that scans (0=ULV, 1=LV, 2=MV, 3=HV, ...).")
                    .defineInRange("minTier", d.minTier(), 0, 14);
            var baseCWUt = builder.comment("CWU/t at minTier and minTier+1; x4 per tier above.")
                    .defineInRange("baseCWUt", d.baseCWUt(), 0, 1 << 20);
            var scanTicks = builder.comment("Scan duration (ticks) at minTier; x0.8 per tier above.")
                    .defineInRange("scanTicks", d.scanTicks(), 1, 10_000_000);
            builder.pop();

            ModConfigSpec.IntValue range = null;
            ModConfigSpec.IntValue depthThreshold = null;
            ModConfigSpec.DoubleValue deep = null;
            ModConfigSpec.DoubleValue shallow = null;
            if (detection) {
                builder.comment("What the scan sees.").push("detection");
                range = builder.comment("Horizontal radius (blocks) around the machine; targets beyond are ignored.",
                        "0 = whole level.")
                        .defineInRange("range", d.range(), 0, 30_000_000);
                depthThreshold = builder.comment(
                        "A target (registered machine or player) deeper than this many blocks below the level's",
                        "sea level is 'deep'.")
                        .defineInRange("depthThreshold", d.depthThreshold(), 0, 4096);
                deep = builder.comment("Richness multiplier for deep targets.")
                        .defineInRange("deepMultiplier", d.deepMultiplier(), 0.0, 1_000_000.0);
                shallow = builder.comment("Richness multiplier for shallow targets; 0 = not detected at all.")
                        .defineInRange("shallowMultiplier", d.shallowMultiplier(), 0.0, 1_000_000.0);
                builder.pop();
            }
            return new ProfileSpec(profile, eps, minPts, calibMin, calibMax, calibTolerance, calibBaseCount,
                    minTier, baseCWUt, scanTicks, range, depthThreshold, deep, shallow);
        }

        void bake() {
            RadarProfile.Defaults d = profile.defaults;
            profile.apply(new RadarProfile.Defaults(eps.get(), minPts.get(), calibMin.get(), calibMax.get(),
                    calibTolerance.get(), calibBaseCount.get(), minTier.get(), baseCWUt.get(), scanTicks.get(),
                    range == null ? d.range() : range.get(),
                    depthThreshold == null ? d.depthThreshold() : depthThreshold.get(),
                    deepMultiplier == null ? d.deepMultiplier() : deepMultiplier.get(),
                    shallowMultiplier == null ? d.shallowMultiplier() : shallowMultiplier.get()));
        }
    }

    static {
        WHITELIST.defaultReturnValue(0);
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MACHINES = builder.comment(
                "Radar target whitelist (shared by radar and seismic radar).",
                "Every machine/block listed here is visible to the radar and contributes to a base's score.",
                "Use registry names directly; GregTech Modern machines are keyed by their registry name.",
                "Format per entry: \"registryId=value\" where value is the richness contributed to the combined base score (defaults to 1 if the =value is omitted).",
                "Example: \"gtceu:electric_blast_furnace=10\"")
                .defineList("machines", java.util.List.of(
                        "gtceu:electric_blast_furnace=10",
                        "gtceu:large_chemical_reactor=25",
                        "minecraft:furnace=1"),
                        o -> o instanceof String);
        CALIB_BORDER_MARGIN = builder.comment(
                "Keep generated calibrator targets at least this many blocks inside the world border.")
                .defineInRange("calibratorBorderMargin", DEFAULT_CALIB_BORDER_MARGIN, 0, 30_000_000);

        RADAR_SPEC = ProfileSpec.define(builder, RadarProfile.RADAR, false);

        builder.comment("Seismic radar: cheap short-range scanner that sees deep (underground) targets.")
                .push("seismic");
        SEISMIC_SPEC = ProfileSpec.define(builder, RadarProfile.SEISMIC, true);
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

    public static synchronized void bake() {
        calibBorderMargin = CALIB_BORDER_MARGIN.get();
        RADAR_SPEC.bake();
        SEISMIC_SPEC.bake();

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
        baked = true;

        WFCore.LOGGER.info("Loaded {} radar targets; {} script override(s)",
                WHITELIST.size(), SCRIPT_ADDITIONS.size() + SCRIPT_REMOVALS.size());
    }
}
