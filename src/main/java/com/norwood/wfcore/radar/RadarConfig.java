package com.norwood.wfcore.radar;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import com.norwood.wfcore.WFCore;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Radar target whitelist. Keyed directly by block registry name (GregTech Modern machines are
 * identified by their registry name rather than block/item meta), mapping to a richness value.
 */
public final class RadarConfig {

    /** DBSCAN defaults, used when {@code /wfcore_radar scan} is run without explicit arguments. */
    public static final int DEFAULT_EPS = 200;
    public static final int DEFAULT_MIN_PTS = 10;

    private static final Object2IntOpenHashMap<ResourceLocation> WHITELIST = new Object2IntOpenHashMap<>();

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MACHINES;
    private static final ForgeConfigSpec.IntValue EPS;
    private static final ForgeConfigSpec.IntValue MIN_PTS;
    public static final ForgeConfigSpec SPEC;

    // Baked DBSCAN tuning: the default eps/minPts a scan uses when no per-scan override is given. Pre-seeded
    // with the config defaults so they are valid even before the first bake().
    private static int eps = DEFAULT_EPS;
    private static int minPts = DEFAULT_MIN_PTS;

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

        SPEC = builder.build();
    }

    private RadarConfig() {}

    public static boolean isWhitelisted(ResourceLocation id) {
        return WHITELIST.containsKey(id);
    }

    public static int getValue(ResourceLocation id) {
        return WHITELIST.getInt(id);
    }

    /** Configured default DBSCAN neighbourhood radius (blocks); overridable per scan. */
    public static int getEps() {
        return eps;
    }

    /** Configured default DBSCAN minimum cluster size; overridable per scan. */
    public static int getMinPts() {
        return minPts;
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
