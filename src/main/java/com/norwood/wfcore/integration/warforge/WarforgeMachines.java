package com.norwood.wfcore.integration.warforge;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.network.chat.Component;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.machine.ChunkReinforcerBlockEntity;
import com.norwood.wfcore.common.machine.ChunkReinforcerMachine;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.norwood.wfcore.WFCore.WF_MACHINES;

public final class WarforgeMachines {

    public static MultiblockMachineDefinition CHUNK_REINFORCER_LV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_MV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_HV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_EV;
    public static MultiblockMachineDefinition CHUNK_REINFORCER_IV;

    private WarforgeMachines() {}

    public static void init() {
        CHUNK_REINFORCER_LV = register("chunk_reinforcer_lv", GTValues.LV, 1, 2, "Basic Chunk Reinforcer (LV)");
        CHUNK_REINFORCER_MV = register("chunk_reinforcer_mv", GTValues.MV, 1, 3, "Hardened Chunk Reinforcer (MV)");
        CHUNK_REINFORCER_HV = register("chunk_reinforcer_hv", GTValues.HV, 2, 4, "Fortified Chunk Reinforcer (HV)");
        CHUNK_REINFORCER_EV = register("chunk_reinforcer_ev", GTValues.EV, 2, 6, "Advanced Chunk Reinforcer (EV)");
        CHUNK_REINFORCER_IV = register("chunk_reinforcer_iv", GTValues.IV, 3, 8, "Bastion Chunk Reinforcer (IV)");
    }

    // TODO: make better design on that
    private static MultiblockMachineDefinition register(String name, int tier, int radius, int bonus,
                                                        String langValue) {
        return WF_MACHINES.multiblock(name,
                holder -> new ChunkReinforcerMachine(holder, tier, radius, bonus),
                MetaMachineBlock::new, MetaMachineItem::new, ChunkReinforcerBlockEntity::new)
                .langValue(langValue)
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("XXX", "XSX", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('X', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(8)
                                .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1)))
                        .build())
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/research_unit"))
                .tooltips(Component.translatable("wfcore.machine.chunk_reinforcer.tooltip1"),
                        Component.translatable("wfcore.machine.chunk_reinforcer.tooltip2", radius, bonus))
                .register();
    }
}
