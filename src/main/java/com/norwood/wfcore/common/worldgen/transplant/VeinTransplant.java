package com.norwood.wfcore.common.worldgen.transplant;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.IWorldGenLayer;
import com.gregtechceu.gtceu.api.data.worldgen.SimpleWorldGenLayer;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGenLayers;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGeneratorUtils;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTOres;
import com.gregtechceu.gtceu.integration.map.cache.server.ServerCache;

import com.mojang.logging.LogUtils;
import com.norwood.wfcore.WFCore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class VeinTransplant {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String LAYER_NAME = "overworld_transplant";


    public static final TagKey<Block> TRANSPLANT_REPLACEABLES =
            TagKey.create(Registries.BLOCK, WFCore.id("overworld_transplant_replaceables"));

    public static final String ID_PREFIX = "transplant/";


    private static final Set<ResourceLocation> TRANSPLANT_IDS = new LinkedHashSet<>();

    /**
     * Original vein ids to leave nether-only (e.g. flavour ores that should stay in the Nether).
     * TODO: wire to {@code WFCoreConfig} so operators can edit without recompiling.
     */
    private static final Set<ResourceLocation> EXCLUDED = new LinkedHashSet<>();

    private static IWorldGenLayer layer;

    private VeinTransplant() {}

    public static void registerLayer() {
        if (layer != null) {
            return;
        }
        RuleTest target = new TagMatchTest(TRANSPLANT_REPLACEABLES);
        layer = new SimpleWorldGenLayer(LAYER_NAME, () -> target, Set.of(Level.OVERWORLD.location()));
        LOGGER.info("[WFCore] Registered overworld transplant worldgen layer '{}'", LAYER_NAME);
    }

    public static IWorldGenLayer layer() {
        return layer;
    }

    public static boolean isTransplantId(ResourceLocation id) {
        return TRANSPLANT_IDS.contains(id)
                || (WFCore.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(ID_PREFIX));
    }

    public static Set<ResourceLocation> transplantIds() {
        return Set.copyOf(TRANSPLANT_IDS);
    }

    public static int registerOverworldCopies() {
        if (layer == null) {
            LOGGER.error("[WFCore] Transplant layer not registered; skipping vein transplant. "
                    + "Was registerWorldgenLayers() called?");
            return 0;
        }
        var registry = GTRegistries.ORE_VEINS;
        boolean wasFrozen = registry.isFrozen();
        if (wasFrozen) {
            registry.unfreeze();
        }
        TRANSPLANT_IDS.clear();

        List<Map.Entry<ResourceLocation, GTOreDefinition>> snapshot = new ArrayList<>(registry.entries());
        int count = 0;

        for (var entry : snapshot) {
            ResourceLocation id = entry.getKey();
            GTOreDefinition def = entry.getValue();

            if (isTransplantId(id) || EXCLUDED.contains(id) || !isNetherVein(def)) {
                continue;
            }

            ResourceLocation copyId = copyId(id);
            if (registry.containKey(copyId)) {
                LOGGER.warn("[WFCore] Skipping transplant of {} - target id {} already exists.", id, copyId);
                continue;
            }

            VeinGenerator generator = def.veinGenerator();
            if (generator == null) {
                LOGGER.warn("[WFCore] Skipping transplant of {} - null vein generator.", id);
                continue;
            }

            GTOreDefinition copy = new GTOreDefinition(def);
            copy.veinGenerator(generator.copy().build());
            copy.layer(layer);
            copy.dimensionFilter(Set.of(Level.OVERWORLD));

            if (copy.biomes() != null && copy.biomes().get().size() > 0) {
                LOGGER.warn("[WFCore] Transplant {} has a biome filter carried from the nether vein; "
                        + "review whether it should be cleared for the overworld.", copyId);
            }

            copy.register(copyId);
            TRANSPLANT_IDS.add(copyId);
            count++;
            LOGGER.debug("[WFCore] Transplanted {} -> {}", id, copyId);
        }

        if (wasFrozen) {
            registry.freeze();
            if (count > 0) {
                GTOres.updateLargestVeinSize();
                ServerCache.instance.oreVeinDefinitionsChanged(registry.registry());
                WorldGeneratorUtils.invalidateOreVeinCache();
            }
        }
        LOGGER.info("[WFCore] Transplanted {} nether vein(s) onto the overworld layer '{}'.",
                count, LAYER_NAME);
        return count;
    }

    private static boolean isNetherVein(GTOreDefinition def) {
        if (def.layer() == WorldGenLayers.NETHERRACK) {
            return true;
        }
        Set<ResourceKey<Level>> dims = def.dimensionFilter();
        return dims != null && dims.contains(Level.NETHER) && !dims.contains(Level.OVERWORLD);
    }

    private static ResourceLocation copyId(ResourceLocation original) {
        return WFCore.id(ID_PREFIX + original.getNamespace() + "/" + original.getPath());
    }
}
