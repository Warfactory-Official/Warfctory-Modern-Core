package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
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
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.block.BoltableCasingBlock;
import com.norwood.wfcore.common.machine.ACHatchBlockEntity;
import com.norwood.wfcore.common.machine.ACHatchPartMachine;
import com.norwood.wfcore.common.machine.LargeBlastFurnaceMachine;
import com.norwood.wfcore.common.machine.LargeTransformerMachine;
import com.norwood.wfcore.common.machine.LightGroundVehicleFactoryMachine;
import com.norwood.wfcore.common.machine.MainframeMachine;
import com.norwood.wfcore.common.machine.PrinterMachine;
import com.norwood.wfcore.common.machine.RadarBlockEntity;
import com.norwood.wfcore.common.machine.RadarMachine;
import com.norwood.wfcore.common.machine.RadarStructure;
import com.norwood.wfcore.common.machine.ResearchUnitBlockEntity;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;
import com.norwood.wfcore.common.machine.VehicleFactoryBlockEntity;
import com.norwood.wfcore.common.machine.compute.CPUSlotPartMachine;
import com.norwood.wfcore.common.machine.compute.CoolingPartMachine;
import com.norwood.wfcore.common.machine.compute.RAMSlotPartMachine;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeMachines;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.air;
import static com.gregtechceu.gtceu.api.pattern.Predicates.any;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.gregtechceu.gtceu.api.pattern.Predicates.custom;
import static com.gregtechceu.gtceu.api.pattern.Predicates.frames;
import static com.gregtechceu.gtceu.api.pattern.Predicates.states;
import static com.gregtechceu.gtceu.common.data.models.GTMachineModels.createWorkableCasingMachineModel;
import static com.norwood.wfcore.WFCore.WF_MACHINES;

public class WFMachines {

    public static MachineDefinition PRINTER;
    public static MachineDefinition AC_INPUT_HATCH;
    public static MachineDefinition AC_OUTPUT_HATCH;
    public static MachineDefinition CPU_SLOT;
    public static MachineDefinition RAM_SLOT;
    public static MachineDefinition COOLING_FAN;
    public static MachineDefinition COOLING_LIQUID;
    public static MultiblockMachineDefinition LARGE_TRANSFORMER;
    public static MultiblockMachineDefinition LARGE_BLAST_FURNACE;
    public static MultiblockMachineDefinition MAINFRAME;
    public static MultiblockMachineDefinition RESEARCH_UNIT;
    public static MultiblockMachineDefinition RADAR;
    public static MultiblockMachineDefinition LIGHT_GROUND_VEHICLE_FACTORY;

    public static void init() {
        AC_INPUT_HATCH = WF_MACHINES.machine("ac_input_hatch",
                MachineDefinition::new,
                holder -> new ACHatchPartMachine(holder, GTValues.EV, false),
                MetaMachineBlock::new, MetaMachineItem::new, ACHatchBlockEntity::new)
                .langValue("AC Input Hatch")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.AC_INPUT)
                .tier(GTValues.EV)
                .overlayTieredHullModel("ac_input_hatch")
                .register();

        AC_OUTPUT_HATCH = WF_MACHINES.machine("ac_output_hatch",
                MachineDefinition::new,
                holder -> new ACHatchPartMachine(holder, GTValues.EV, true),
                MetaMachineBlock::new, MetaMachineItem::new, ACHatchBlockEntity::new)
                .langValue("AC Output Hatch")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.AC_OUTPUT)
                .tier(GTValues.EV)
                .overlayTieredHullModel("ac_output_hatch")
                .register();
        PRINTER = WF_MACHINES.machine("printer", holder -> new PrinterMachine(holder, GTValues.LV))
                .langValue("Data Printer")
                .rotationState(RotationState.NON_Y_AXIS)
                .tier(GTValues.LV)
                .workableTieredHullModel(WFCore.id("block/multiblock/printer"))
                .register();

        CPU_SLOT = WF_MACHINES.machine("cpu_slot", CPUSlotPartMachine::new)
                .langValue("CPU Slot")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.GPC_CPU_SLOT)
                .tier(GTValues.HV)
                .modelProperty(CPUSlotPartMachine.CPU_FILL, CPUSlotPartMachine.CpuFill.EMPTY)
                .model(cpuSlotModel())
                .register();

        RAM_SLOT = WF_MACHINES.machine("ram_slot", RAMSlotPartMachine::new)
                .langValue("RAM Slot")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.GPC_RAM_SLOT)
                .tier(GTValues.HV)
                .modelProperty(RAMSlotPartMachine.RAM_FILL, RAMSlotPartMachine.FillLevel.L0)
                .model(ramSocketModel())
                .register();

        COOLING_FAN = WF_MACHINES.machine("cooling_fan", holder -> new CoolingPartMachine(holder, false))
                .langValue("Cooling Fan")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.GPC_COOLER)
                .tier(GTValues.HV)
                .overlayTieredHullModel("cooling_fan")
                .register();

        COOLING_LIQUID = WF_MACHINES.machine("cooling_liquid", holder -> new CoolingPartMachine(holder, true))
                .langValue("Liquid Cooler")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.GPC_COOLER)
                .tier(GTValues.HV)
                .overlayTieredHullModel("cooling_liquid")
                .register();

        MAINFRAME = WF_MACHINES.multiblock("mainframe", MainframeMachine::new)
                .langValue("Computation Mainframe")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("XXXXX", "XXXXX", "XXSXX", "XXXXX", "XXXXX")
                        .aisle("XXXXX", "X###X", "X###X", "X###X", "XXXXX")
                        .aisle("XXXXX", "X###X", "X###X", "X###X", "XXXXX")
                        .aisle("XXXXX", "X###X", "X###X", "X###X", "XXXXX")
                        .aisle("XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('X', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(30)
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                                .or(abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1))
                                .or(abilities(PartAbility.COMPUTATION_DATA_TRANSMISSION).setMinGlobalLimited(1)))
                        .where('#', abilities(WFPartAbility.GPC_CPU_SLOT).setMinGlobalLimited(1)
                                .or(abilities(WFPartAbility.GPC_RAM_SLOT).setMinGlobalLimited(1))
                                .or(abilities(WFPartAbility.GPC_COOLER))
                                .or(blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get())))
                        .build())
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/mainframe"))
                .register();

        RESEARCH_UNIT = WF_MACHINES.multiblock("research_unit", ResearchUnitMachine::new,
                MetaMachineBlock::new, MetaMachineItem::new, ResearchUnitBlockEntity::new)
                .langValue("Research Unit")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("XXX", "XSX", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('X', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(8)
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1))
                                .or(abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMinGlobalLimited(1)))
                        .build())
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/research_unit"))
                .register();

        LARGE_TRANSFORMER = WF_MACHINES.multiblock("large_transformer", LargeTransformerMachine::new)
                .langValue("Large Transformer")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("XXX", "XSX", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('X', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(8)
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(PartAbility.OUTPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(WFPartAbility.AC_INPUT).setMaxGlobalLimited(1))
                                .or(abilities(WFPartAbility.AC_OUTPUT).setMaxGlobalLimited(1))
                                .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1)))
                        .build())
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/large_transformer"))
                .register();

        LARGE_BLAST_FURNACE = WF_MACHINES
                .multiblock("large_blast_furnace", LargeBlastFurnaceMachine::new)
                .langValue("Large Blast Furnace")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.LARGE_BLAST_FURNACE)
                .appearanceBlock(GTBlocks.CASING_PRIMITIVE_BRICKS)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK)
                        .aisle("#XXX#", "#XXX#", "#XXX#", "#####", "#####", "#####", "#####")
                        .aisle("XXXXX", "X&&&X", "XX#XX", "#XXX#", "##X##", "##X##", "#XXX#")
                        .aisle("XXXXX", "X&&&X", "X###X", "#X#X#", "#X#X#", "#X#X#", "#X#X#")
                        .aisle("XXXXX", "X&&&X", "XX#XX", "#XXX#", "##X##", "##X##", "#XXX#")
                        .aisle("#XXX#", "#XYX#", "#XXX#", "#####", "#####", "#####", "#####")
                        .where('X', blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get())
                                .or(abilities(PartAbility.IMPORT_ITEMS))
                                .or(abilities(PartAbility.EXPORT_ITEMS))
                                .or(abilities(PartAbility.EXPORT_FLUIDS)))
                        .where('#', air())
                        .where('&', air().or(custom(bws -> GTUtil.isBlockSnow(bws.getBlockState()), null)))
                        .where('Y', controller(blocks(definition.getBlock())))
                        .build())
                .model(createWorkableCasingMachineModel(
                        GTCEu.id("block/casings/solid/machine_primitive_bricks"),
                        GTCEu.id("block/multiblock/primitive_blast_furnace"))
                        .andThen(b -> b.addDynamicRenderer(DynamicRenderHelper::createPBFLavaRender)))
                .hasBER(true)
                .register();

        RADAR = WF_MACHINES.multiblock("radar", RadarMachine::new,
                MetaMachineBlock::new, MetaMachineItem::new, RadarBlockEntity::new)
                .langValue("Radar")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                // The dish is drawn by our own GltfMachineRenderer (registered via EntityRenderersEvent).
                // GTM registers BlockEntityWithBERModelRenderer for the same BE type when hasBER is on
                // (default true) and clobbers ours, so the model + render mask silently never run. Disable
                // it: the casing/front overlay still render from the chunk-mesh baked model.
                .hasBER(false)
                // The dish is left-right symmetric but the controller 'A' sits off-centre, so a flipped
                // pattern match would mirror everything across the controller and wreck the mask/model.
                .allowFlip(false)
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
                            .where('K', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get())
                                    .or(abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMaxGlobalLimited(1)))
                            .where('I', abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                    .or(blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMaxGlobalLimited(4)))
                            .where(' ', any())
                            .build();
                })
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/radar"))
                .register();

        LIGHT_GROUND_VEHICLE_FACTORY = WF_MACHINES
                .multiblock("light_ground_vehicle_factory", LightGroundVehicleFactoryMachine::new,
                        MetaMachineBlock::new, MetaMachineItem::new, VehicleFactoryBlockEntity::new)
                .langValue("MV Light Ground Vehicle Factory")
                .recipeType(VehicleFactoryRecipes.VEHICLE_ASSEMBLER)
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("KKKKKKKK", "KKKKKKKK", "KKKKKKKK", "KKKCKKKK")
                        .aisle("KKKKKKKK", "K      K", "K      K", "KKKKKKKK")
                        .aisle("KKKKKKKK", "K      K", "K      K", "KKKKKKKK")
                        .aisle("KKKKKKKK", "K      K", "K      K", "KKKKKKKK")
                        .aisle("KKKKKKKK", "K      K", "K      K", "KKKKKKKK")
                        .aisle("KKKKKKKK", "K      K", "K      K", "KKKKKKKK")
                        .aisle("KKKKKKKK", "K      K", "K      K", "KKKKKKKK")
                        .aisle("KKKKKKKK", "KKKKKKKK", "KKKKKKKK", "KKKKKKKK")
                        .where('C', controller(blocks(definition.getBlock())))
                        .where('K', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(40)
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2))
                                .or(abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(4))
                                .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(4)))
                        .where(' ', any())
                        .build())
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/vehicle_factory"))
                .register();

        // WarForge integration: only when WarForge is present. The chunk-reinforcer machines reference
        // com.flansmod.warforge.* and live in WarforgeMachines so verifying this class never loads them
        // (verification ignores the runtime gate). WarforgeMachines is loaded only by this call.
        if (WarforgeIntegration.isLoaded()) {
            WarforgeMachines.init();
        }
    }

    private static Block matBlock(Material material) {
        return ChemicalHelper.getBlock(TagPrefix.block, material);
    }

    /**
     * Front-overlay model for the RAM slot: the {@code ram_socket} sprite picked by how many sockets are
     * filled ({@code ram_socket}, {@code ram_socket1}..{@code ram_socket4}), driven by
     * {@link RAMSlotPartMachine.FillLevel} via the {@code RAM_FILL} render-state property.
     */
    private static MachineBuilder.ModelInitializer ramSocketModel() {
        return (ctx, prov, builder) -> {
            builder.forAllStatesModels(state -> {
                var tex = WFCore
                        .id("block/overlay/part/ram/ram_socket" + state.getValue(RAMSlotPartMachine.RAM_FILL).suffix());
                var model = prov.models().nested()
                        .parent(prov.models().getExistingFile(GTCEu.id("block/overlay/2_layer/front")))
                        .texture("overlay", tex)
                        .texture("overlay_2", tex);
                return GTMachineModels.tieredHullTextures(model, builder.getOwner().getTier());
            });
            builder.addReplaceableTextures("bottom", "top", "side");
        };
    }

    /**
     * Front-overlay model for the CPU slot: the {@code cpu_slot} sprite (empty) or {@code cpu_slot_filled}
     * (a CPU is installed), driven by {@link CPUSlotPartMachine.CpuFill} via the {@code cpu_fill} render-state
     * property.
     */
    private static MachineBuilder.ModelInitializer cpuSlotModel() {
        return (ctx, prov, builder) -> {
            builder.forAllStatesModels(state -> {
                var tex = WFCore
                        .id("block/overlay/part/cpu_slot" + state.getValue(CPUSlotPartMachine.CPU_FILL).suffix());
                var model = prov.models().nested()
                        .parent(prov.models().getExistingFile(GTCEu.id("block/overlay/2_layer/front")))
                        .texture("overlay", tex)
                        .texture("overlay_2", tex);
                return GTMachineModels.tieredHullTextures(model, builder.getOwner().getTier());
            });
            builder.addReplaceableTextures("bottom", "top", "side");
        };
    }
}
