package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.block.BoltableCasingBlock;
import com.norwood.wfcore.common.machine.PrinterMachine;
import com.norwood.wfcore.common.machine.RadarBlockEntity;
import com.norwood.wfcore.common.machine.RadarMachine;
import com.norwood.wfcore.common.machine.RadarStructure;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.any;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.gregtechceu.gtceu.api.pattern.Predicates.frames;
import static com.gregtechceu.gtceu.api.pattern.Predicates.states;
import static com.norwood.wfcore.WFCore.EXAMPLE_REGISTRATE;

public class WFMachines {

    public static MachineDefinition PRINTER;
    public static MultiblockMachineDefinition RADAR;

    public static void init() {
        PRINTER = EXAMPLE_REGISTRATE.machine("printer", holder -> new PrinterMachine(holder, GTValues.LV))
                .langValue("Data Printer")
                .rotationState(RotationState.NON_Y_AXIS)
                .tier(GTValues.LV)
                .overlayTieredHullModel("printer")
                .tooltips(Component.translatable("wfcore.machine.printer.tooltip"))
                .register();

        RADAR = EXAMPLE_REGISTRATE.multiblock("radar", RadarMachine::new,
                MetaMachineBlock::new, MetaMachineItem::new, RadarBlockEntity::new)
                .rotationState(RotationState.NON_Y_AXIS)
                .langValue("Radar")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> {
                    var pattern = FactoryBlockPattern.start(
                            RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT);
                    for (String[] aisle : RadarStructure.AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('A', controller(blocks(definition.getBlock())))
                            .where('B', blocks(matBlock(GTMaterials.Aluminium)))
                            .where('C', blocks(matBlock(GTMaterials.Steel)))
                            .where('D', blocks(matBlock(GTMaterials.Lead)))
                            .where('E', frames(GTMaterials.Aluminium))
                            .where('F', frames(WFMaterials.GalvanizedSteel))
                            .where('G', blocks(Blocks.SMOOTH_STONE))
                            .where('H', blocks(Blocks.COPPER_BLOCK))
                            .where('J', states(WFBlocks.BOLTABLE_CASING.get().defaultBlockState()
                                    .setValue(BoltableCasingBlock.BOLTED, true)))
                            .where('K', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()))
                            .where('I', abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                    .or(blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMaxGlobalLimited(4)))
                            .where(' ', any())
                            .build();
                })
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/radar"))
                .tooltips(Component.translatable("wfcore.machine.radar.tooltip"))
                .register();
    }

    private static Block matBlock(Material material) {
        return ChemicalHelper.getBlock(TagPrefix.block, material);
    }
}
