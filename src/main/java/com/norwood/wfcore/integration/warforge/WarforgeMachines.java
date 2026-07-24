package com.norwood.wfcore.integration.warforge;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.machine.ChunkReinforcerBlockEntity;
import com.norwood.wfcore.common.machine.ChunkReinforcerMachine;

import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.any;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.gregtechceu.gtceu.api.pattern.Predicates.frames;
import static com.norwood.wfcore.WFCore.WF_MACHINES;

public final class WarforgeMachines {

    public static MultiblockMachineDefinition CHUNK_REINFORCER_LV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_MV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_HV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_EV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_IV;

    private WarforgeMachines() {}

    public static void init() {
        CHUNK_REINFORCER_LV = register("chunk_reinforcer_lv", GTValues.LV, 1, 2, "Basic Chunk Reinforcer (LV)",
                GTBlocks.MACHINE_CASING_LV, GTBlocks.CASING_STEEL_SOLID, GTMaterials.Steel,
                GTCEu.id("block/casings/solid/machine_casing_solid_steel"));
        CHUNK_REINFORCER_MV = register("chunk_reinforcer_mv", GTValues.MV, 1, 3, "Hardened Chunk Reinforcer (MV)",
                GTBlocks.MACHINE_CASING_MV, GTBlocks.CASING_ALUMINIUM_FROSTPROOF, GTMaterials.Aluminium,
                GTCEu.id("block/casings/solid/machine_casing_frost_proof"));
        CHUNK_REINFORCER_HV = register("chunk_reinforcer_hv", GTValues.HV, 2, 4, "Fortified Chunk Reinforcer (HV)",
                GTBlocks.MACHINE_CASING_HV, GTBlocks.CASING_STAINLESS_CLEAN, GTMaterials.StainlessSteel,
                GTCEu.id("block/casings/solid/machine_casing_clean_stainless_steel"));
        CHUNK_REINFORCER_EV = register("chunk_reinforcer_ev", GTValues.EV, 2, 6, "Advanced Chunk Reinforcer (EV)",
                GTBlocks.MACHINE_CASING_EV, GTBlocks.CASING_TITANIUM_STABLE, GTMaterials.Titanium,
                GTCEu.id("block/casings/solid/machine_casing_stable_titanium"));
        CHUNK_REINFORCER_IV = register("chunk_reinforcer_iv", GTValues.IV, 3, 8, "Bastion Chunk Reinforcer (IV)",
                GTBlocks.MACHINE_CASING_IV, GTBlocks.CASING_TUNGSTENSTEEL_ROBUST, GTMaterials.TungstenSteel,
                GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"));
    }

    /**
     * @param tierCasing  'A' - voltage-tier machine casing (the reinforcer's spine)
     * @param shellCasing 'C' - bulk hull casing; doubles as the food input-bus slot and the controller appearance
     * @param frame       'F' - structural frame ringing the nose and tail
     * @param casingTex   GT texture the controller hull renders with (matches {@code shellCasing})
     */
    private static MultiblockMachineDefinition register(String name, int tier, int radius, int bonus,
                                                        String langValue,
                                                        Supplier<? extends Block> tierCasing,
                                                        Supplier<? extends Block> shellCasing,
                                                        Material frame,
                                                        ResourceLocation casingTex) {
        return WF_MACHINES.multiblock(name,
                holder -> new ChunkReinforcerMachine(holder, tier, radius, bonus),
                MetaMachineBlock::new, MetaMachineItem::new, ChunkReinforcerBlockEntity::new)
                .langValue(langValue)
                .appearanceBlock(shellCasing)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle(" A ", " F ", " F ")
                        .aisle("AAA", "CCC", "CCC")
                        .aisle("AAA", "C S", "CCC")
                        .aisle("AAA", "CCC", "CCC")
                        .aisle(" A ", " F ", " F ")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('A', blocks(tierCasing.get()))
                        .where('C', blocks(shellCasing.get()).setMinGlobalLimited(12)
                                .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1)))
                        .where('F', frames(frame))
                        .where(' ', any())
                        .build())
                .workableCasingModel(casingTex, WFCore.id("block/multiblock/research_unit"))
                .tooltips(Component.translatable("wfcore.machine.chunk_reinforcer.tooltip1"),
                        Component.translatable("wfcore.machine.chunk_reinforcer.tooltip2", radius, bonus))
                .register();
    }
}
