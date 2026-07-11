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
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.block.BoltableCasingBlock;
import com.norwood.wfcore.common.machine.*;
import com.norwood.wfcore.common.machine.compute.CPUSlotPartMachine;
import com.norwood.wfcore.common.machine.compute.CoolingPartMachine;
import com.norwood.wfcore.common.machine.compute.RAMSlotPartMachine;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeMachines;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
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
    public static MachineDefinition CREATIVE_COMPUTATION_SINK;
    public static MultiblockMachineDefinition LARGE_TRANSFORMER;
    public static MultiblockMachineDefinition LARGE_BLAST_FURNACE;
    public static MultiblockMachineDefinition MAINFRAME;
    public static MultiblockMachineDefinition RESEARCH_UNIT;
    public static MultiblockMachineDefinition RADAR;
    public static MultiblockMachineDefinition LIGHT_GROUND_VEHICLE_FACTORY;
    public static MultiblockMachineDefinition DRILL_RIG;

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
                .tooltips(Component.translatable("wfcore.machine.cooling_fan.tooltip1"),
                        Component.translatable("wfcore.machine.cooling_fan.tooltip2"),
                        Component.translatable("wfcore.machine.cooling_fan.tooltip3"),
                        Component.translatable("wfcore.machine.cooling_fan.tooltip4"))
                .tier(GTValues.HV)
                .overlayTieredHullModel("cooling_fan")
                .register();

        COOLING_LIQUID = WF_MACHINES.machine("cooling_liquid", holder -> new CoolingPartMachine(holder, true))
                .langValue("Liquid Cooler")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.GPC_COOLER)
                .tooltips(Component.translatable("wfcore.machine.cooling_liquid.tooltip1"),
                        Component.translatable("wfcore.machine.cooling_liquid.tooltip2"),
                        Component.translatable("wfcore.machine.cooling_liquid.tooltip3"))
                .tier(GTValues.HV)
                .overlayTieredHullModel("cooling_liquid")
                .register();

        // Debug/load-test tool: drains a configurable CWU/t from an attached computation provider. Mirrors
        // GregTech's creative_computation_provider (same MAX-hull + optical overlay look), but consumes.
        CREATIVE_COMPUTATION_SINK = WF_MACHINES
                .machine("creative_computation_sink", CreativeComputationSinkMachine::new)
                .langValue("Creative Computation Sink")
                .rotationState(RotationState.NONE)
                .model(GTMachineModels.createSingleOverlayTieredHullMachineModel(
                        GTCEu.id("block/overlay/machine/overlay_data_hatch_optical"),
                        GTCEu.id("block/overlay/machine/overlay_data_hatch_optical_emissive")))
                .tier(GTValues.MAX)
                .register();

        MAINFRAME = WF_MACHINES.multiblock("mainframe", MainframeMachine::new)
                .langValue("Computation Mainframe")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.BACK, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("SA", "CC", "CC", "CC", "AA")
                        .aisle("VA", "XV", "XV", "XV", "VA")
                        .setRepeatable(2, 6)
                        .aisle("AA", "CC", "CC", "CC", "AA")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('A', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()))
                        .where('V', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()))
                        .where('X', abilities(WFPartAbility.GPC_CPU_SLOT).setMinGlobalLimited(1)
                                .or(abilities(WFPartAbility.GPC_RAM_SLOT).setMinGlobalLimited(1))
                                .or(abilities(WFPartAbility.GPC_COOLER))
                                .or(blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get())))
                        .where('C', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(5)
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                                .or(abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1))
                                .or(abilities(PartAbility.COMPUTATION_DATA_TRANSMISSION)
                                        .setMinGlobalLimited(1).setMaxGlobalLimited(1)))
                        .build())
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        WFCore.id("block/multiblock/mainframe"))
                .register();

        RESEARCH_UNIT = WF_MACHINES.multiblock("research_unit", ResearchUnitMachine::new,
                MetaMachineBlock::new, MetaMachineItem::new, ResearchUnitBlockEntity::new)
                .langValue("Research Unit")
                // The unit's animated GLTF core is drawn by our own GltfMachineRenderer (registered in
                // WFClientEvents). GTM's default BER would clobber it (see RadarMachine), so disable it;
                // the casing/front overlay still render from the chunk-mesh baked model.
                .hasBER(false)
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle(" BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .aisle(" BBBBB ", " DEEED ", " DEEED ", " DEEED ", " BBBBB ", "       ", "       ")
                        .aisle(" BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .aisle("AAAAAAA", "CCCCCCC", "CGGGGGC", "CGGGGGC", "CGGGGGC", "CCCCCCC", "AAAAAAA")
                        .aisle("AAAAAAA", "CCCCCCC", "CG   GS", "CG   GC", "CG   GC", "CCCCCCC", "AAAAAAA")
                        .aisle("AAAAAAA", "CCCCCCC", "CGGGGGC", "CGGGGGC", "CGGGGGC", "CCCCCCC", "AAAAAAA")
                        .aisle(" BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .aisle(" BBBBB ", " D   F ", " D   F ", " D   F ", " BBBBB ", "       ", "       ")
                        .aisle(" BBBBB ", " B   F ", " B   F ", " B   F ", " BBBBB ", "       ", "       ")
                        .aisle(" BBBBB ", " D   F ", " D   F ", " D   F ", " BBBBB ", "       ", "       ")
                        .aisle(" BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('A', blocks(GTBlocks.STEEL_HULL.get()))
                        .where('B', blocks(GTBlocks.MACHINE_CASING_LV.get())
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2,
                                        1))
                                .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1).setMaxGlobalLimited(1,
                                        1))
                                .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1, 1))
                                .or(abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMinGlobalLimited(1, 1)))
                        .where('C', blocks(GTBlocks.CASING_STEEL_SOLID.get()))
                        .where('D', frames(WFMaterials.GalvanizedSteel))
                        .where('E', blocks(GTBlocks.FIREBOX_STEEL.get()))
                        .where('F', blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                        .where('G', blocks(GTBlocks.COIL_CUPRONICKEL.get()))
                        .where(' ', any())
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
                .recipeModifier(LargeBlastFurnaceMachine::modifyRecipe)
                .tooltips(Component.translatable("wfcore.machine.large_blast_furnace.tooltip1"),
                        Component.translatable("wfcore.machine.large_blast_furnace.tooltip2"),
                        Component.translatable("wfcore.machine.large_blast_furnace.tooltip3"),
                        Component.translatable("wfcore.machine.large_blast_furnace.tooltip4"))
                .appearanceBlock(GTBlocks.CASING_PRIMITIVE_BRICKS)
                .pattern(definition -> FactoryBlockPattern.start(
                        RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK)
                        // 3x3x3 primitive-brick central chamber (X, controller Y on the front face).
                        // A Steel Firebox (G) on the left and/or right is an optional side chamber that
                        // adds parallel; L is its optional brick shell. Both default to air, so the
                        // furnace forms and runs with 0, 1 or 2 chambers.
                        .aisle("  XXX  ", "  XYX  ", "  XXX  ")
                        .aisle("  XXX  ", "LGX#XGL", "  XXX  ")
                        .aisle("  XXX  ", "  XXX  ", "  XXX  ")
                        .where('Y', controller(blocks(definition.getBlock())))
                        .where('X', blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get())
                                .or(abilities(PartAbility.IMPORT_ITEMS))
                                .or(abilities(PartAbility.EXPORT_ITEMS))
                                .or(abilities(PartAbility.EXPORT_FLUIDS)))
                        .where('#', air())
                        .where('G', blocks(GTBlocks.FIREBOX_STEEL.get()).or(air()))
                        .where('L', blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get()).or(air()))
                        .where(' ', any())
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
                            .where('B', blocks(WFBlocks.CONCRETE_BASE.get()))
                            .where('C', frames(WFMaterials.GalvanizedSteel))
                            .where('D', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()))
                            .where('K', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get())
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2, 1))
                                    .or(abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMaxGlobalLimited(1, 1)))
                            .where('E', blocks(WFBlocks.GALVANIZED_STEEL_CASING.get()))
                            .where('F', blocks(WFBlocks.CONDENSED_CABLES.get()))
                            .where('G', blocks(matBlock(WFMaterials.GalvanizedSteel)))
                            .where('H', states(WFBlocks.BOLTABLE_CASING.get().defaultBlockState()
                                    .setValue(BoltableCasingBlock.BOLTED, false)))
                            .where('I', frames(GTMaterials.Aluminium))
                            .where('J', blocks(matBlock(GTMaterials.Aluminium)))
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
                .appearanceBlock(GTBlocks.STEEL_HULL)
                .allowFlip(false)
                .allowExtendedFacing(false)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            { "HAA         AAH", "CCC         CCC", "AAA         AAA", "               ",
                                    "               ", "               ", "               ", "               ",
                                    "               ", "               ", "               " },
                            { "HAAAAAAAAAAAAAH", "CCC         CCC", "AAA         AAA", " C           C ",
                                    " C           C ", " C           C ", " A           A ", "  C         C  ",
                                    "   C       C   ", "    GFFFFFG    ", "               " },
                            { "HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", " F           F ",
                                    "               ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               " },
                            { "  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    " F           F ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               " },
                            { "  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    "               ", " F           F ", "               ", "               ",
                                    "   F       F   ", "               ", "               " },
                            { "HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", "               ",
                                    "               ", "               ", " F           F ", "               ",
                                    "   F       F   ", "               ", "               " },
                            { "HAACACACACACAAH", "CCCDEEEEEEEDCCS", "AAA         AAA", " C           C ",
                                    " C           C ", " C           C ", " A           A ", "  C         C  ",
                                    "   C       C   ", "    C     C    ", "     G   G     " },
                            { "HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", "               ",
                                    "               ", "               ", " F           F ", "               ",
                                    "   F       F   ", "               ", "               " },
                            { "  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    "               ", " F           F ", "               ", "               ",
                                    "   F       F   ", "               ", "               " },
                            { "  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    " F           F ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               " },
                            { "HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", " F           F ",
                                    "               ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               " },
                            { "HAAAAAAAAAAAAAH", "CCC         CCC", "AAA         AAA", " C           C ",
                                    " C           C ", " C           C ", " A           A ", "  C         C  ",
                                    "   C       C   ", "    GFFFFFG    ", "               " },
                            { "HAA         AAH", "CCC         CCC", "AAA         AAA", "               ",
                                    "               ", "               ", "               ", "               ",
                                    "               ", "               ", "               " },
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.STEEL_HULL.get())) // gtceu:steel_machine_casing x168
                            .where('B', blocks(GTBlocks.FIREBOX_STEEL.get())) // gtceu:steel_firebox_casing[active=false]
                                                                              // x8
                            .where('C', blocks(GTBlocks.CASING_STEEL_SOLID.get())) // gtceu:solid_machine_casing x138
                            .where('D', blocks(GTBlocks.REINFORCED_STONE.get())) // gtceu:reinforced_stone x18
                            .where('E', blocks(GTBlocks.LIGHT_CONCRETE.get())) // gtceu:light_concrete x63
                            .where('F', frames(GTMaterials.Steel)) // gtceu:steel_frame x42
                            .where('G', blocks(GTBlocks.MACHINE_CASING_LV.get())) // gtceu:lv_machine_casing x6
                            .where('H', blocks(GTBlocks.STEEL_HULL.get())
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.DATA_ACCESS))) // gtceu:steel_machine_casing x168
                            .where(' ', any()).build();

                })
                .workableCasingModel(GTCEu.id("block/casings/steam/steel/side"),
                        GTCEu.id("block/machines/assembler"))
                .register();

        DRILL_RIG = WF_MACHINES.multiblock("drill_rig", DrillRigMachine::new,
                MetaMachineBlock::new, MetaMachineItem::new, DrillRigBlockEntity::new)
                .langValue("Drilling Rig")
                // The animated GLTF rig is drawn by our own GltfMachineRenderer (registered in WFClientEvents).
                // GTM's default BER would clobber it (see RadarMachine), so disable it; the casing/overlay still
                // render from the chunk-mesh baked model.
                .hasBER(false)
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.DRILLING)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(definition -> {
                    var pattern = FactoryBlockPattern.start(
                            RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT);
                    for (String[] aisle : DrillRigStructure.AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.CASING_STEEL_SOLID.get()))
                            .where('B', frames(GTMaterials.Steel)
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.EXPORT_ITEMS).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(3))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(3))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(3)))
                            .where('M', abilities(PartAbility.MUFFLER))
                            .where('C', blocks(GTBlocks.CASING_STEEL_GEARBOX.get()))
                            .where('D', blocks(GTBlocks.LIGHT_CONCRETE.get()))
                            .where('E', blocks(GTBlocks.CASING_STEEL_PIPE.get()))
                            .where('F', blocks(WFBlocks.DRILL_HEAD.get()))
                            .where('G', blocks(GTBlocks.CASING_GRATE.get()))
                            .where('H', any())
                            .where(' ', any())
                            .build();
                })
                .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/machines/miner"))
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
