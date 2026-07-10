package com.norwood.wfcore.radar;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import com.norwood.wfcore.WFCore;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.List;

/**
 * Radar target whitelist. Keyed directly by block registry name (GregTech Modern machines are
 * identified by their registry name rather than block/item meta), mapping to a richness value.
 */
public final class RadarConfig {

    private static final Object2IntOpenHashMap<ResourceLocation> WHITELIST = new Object2IntOpenHashMap<>();

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> MACHINES;
    public static final ForgeConfigSpec SPEC;

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
        SPEC = builder.build();
    }

    private RadarConfig() {}

    public static boolean isWhitelisted(ResourceLocation id) {
        return WHITELIST.containsKey(id);
    }

    public static int getValue(ResourceLocation id) {
        return WHITELIST.getInt(id);
    }

    public static void bake() {
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
        WFCore.LOGGER.info("Loaded {} radar targets", WHITELIST.size());
    }
}
