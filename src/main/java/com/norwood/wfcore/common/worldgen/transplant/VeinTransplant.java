package com.norwood.wfcore.common.worldgen.transplant;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.IWorldGenLayer;
import com.gregtechceu.gtceu.api.data.worldgen.SimpleWorldGenLayer;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGenLayers;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGeneratorUtils;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTOreVeins;
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
        layer = new SimpleWorldGenLayer(WFCore.id(LAYER_NAME), () -> target, Set.of(Level.OVERWORLD));
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

    /**
     * Registers an overworld copy of every nether vein onto {@link #LAYER_NAME}.
     *
     * <p>
     * <b>Deferred on 1.21.1 / GregTech 8.0.0.</b> This walked {@code GTRegistries.ORE_VEINS}, unfroze it,
     * built {@link GTOreDefinition} copies and registered them at runtime. In 8.0.0 ore veins are a
     * <em>datapack</em> registry ({@code GTRegistries.Keys.ORE_VEIN}) rather than a mutable one:
     * {@code GTRegistries.ORE_VEINS} is gone, definitions arrive through a
     * {@code HolderLookup.RegistryLookup<GTOreDefinition>}, and {@code GTOreVeins.updateLargestVeinSize}
     * now takes that lookup. Re-registering at runtime is no longer possible; the transplant has to be
     * rebuilt as generated datapack entries or as a reload listener over the vein lookup.
     *
     * <p>
     * Nothing calls this today — its only caller, {@code GTOreLoaderTransplantMixin}, is on the port work
     * queue in build.gradle. The layer registration, the id helpers and {@link #isNetherVein} above are
     * still live and used by the deposit retrofit and backfill paths.
     */
    public static int registerOverworldCopies() {
        LOGGER.warn("[WFCore] Vein transplant is not implemented on 1.21.1 yet: GregTech 8.0.0 moved ore "
                + "veins to a datapack registry, so runtime copies can no longer be registered.");
        return 0;
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
