package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder;
import com.gregtechceu.gtceu.client.renderer.machine.DynamicRenderHelper;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalDataHatchMachine;
import com.gregtechceu.gtceu.common.machine.electric.ChargerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.machine.*;
import com.norwood.wfcore.common.machine.compute.CPUSlotPartMachine;
import com.norwood.wfcore.common.machine.compute.CoolingPartMachine;
import com.norwood.wfcore.common.machine.compute.RAMSlotPartMachine;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeMachines;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static com.gregtechceu.gtceu.api.pattern.Predicates.*;
import static com.gregtechceu.gtceu.common.data.models.GTMachineModels.createWorkableCasingMachineModel;
import static com.norwood.wfcore.WFCore.WF_MACHINES;

public class WFMachines {

    public static MachineDefinition PRINTER;
    public static MachineDefinition AC_INPUT_HATCH;
    public static MachineDefinition AC_OUTPUT_HATCH;
    public static MachineDefinition CPU_SLOT;
    public static MachineDefinition RAM_SLOT;
    public static MachineDefinition COPPER_HEATSINK;
    public static MachineDefinition COOLING_LIQUID;
    public static MachineDefinition CREATIVE_COMPUTATION_SINK;
    public static MachineDefinition[] VEHICLE_CHARGER;
    public static MachineDefinition MV_COMPUTATION_TRANSMISSION_HATCH;
    public static MachineDefinition MV_COMPUTATION_RECEPTION_HATCH;
    public static MachineDefinition MV_DATA_TRANSMISSION_HATCH;
    public static MachineDefinition MV_DATA_RECEPTION_HATCH;
    public static MultiblockMachineDefinition MV_NETWORK_SWITCH;
    public static MultiblockMachineDefinition LARGE_TRANSFORMER;
    public static MultiblockMachineDefinition LARGE_BLAST_FURNACE;
    public static MultiblockMachineDefinition PRIMITIVE_ALLOYER;
    public static MultiblockMachineDefinition STRANDCASTER;
    public static MultiblockMachineDefinition GAS_EXTRACTOR;
    public static MultiblockMachineDefinition MAINFRAME;
    public static MultiblockMachineDefinition RESEARCH_UNIT;
    public static MultiblockMachineDefinition RADAR;
    public static MultiblockMachineDefinition LIGHT_GROUND_VEHICLE_FACTORY;
    public static MultiblockMachineDefinition TANK_ASSEMBLY;
    public static MultiblockMachineDefinition LIGHT_PLANE_ASSEMBLER;
    public static MultiblockMachineDefinition HEAVY_PLANE_ASSEMBLER;
    public static MultiblockMachineDefinition HEAVY_VEHICLE_DEPOT;
    public static MultiblockMachineDefinition HELICOPTER_ASSEMBLER;
    public static MultiblockMachineDefinition DRILL_RIG;
    public static MultiblockMachineDefinition STEAM_WIREMILL;
    public static MultiblockMachineDefinition MISSILE_FACTORY;
    public static MultiblockMachineDefinition MISSILE_LAUNCHER;
    public static MultiblockMachineDefinition INTERCEPTOR;
    public static MultiblockMachineDefinition GREENHOUSE;
    public static MultiblockMachineDefinition MOB_FARMER;

    // JEI preview stages for the Large Blast Furnace, laid out to match its pattern's axes
    // (start(RIGHT, UP, FRONT)): each aisle is a slice along RIGHT (the 9-wide axis that carries the
    // center + two side chimneys), strings go bottom->top (9 tall), the 5-char column is the FRONT
    // depth, and the controller ('S', aisle 4 / string 2 / char 4) faces SOUTH. 'P' = primitive brick,
    // 'F' = bronze firebox, ' ' = air. The side-chimney fireboxes sit on aisles 1 & 7 so they render
    // to the controller's LEFT/RIGHT, matching detectSideChimneys/computeChimneyMouths. Stage 1 = core
    // only, stage 2 = core + one side chimney, stage 3 = both.
    private static final String[][] PBF_SHAPE_STAGE1 = {
            { "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     " },
            { "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     " },
            { "  P  ", " PPP ", " PPP ", " PPP ", "  P  ", "     ", "     ", "     ", "     " },
            { " PPP ", "P   P", "P   P", "P   P", " PPP ", "  P  ", "  P  ", "  P  ", " PPP " },
            { " PPP ", "P   P", "P   S", "P   P", " P P ", " P P ", " P P ", " P P ", " P P " },
            { " PPP ", "P   P", "P   P", "P   P", " PPP ", "  P  ", "  P  ", "  P  ", " PPP " },
            { "  P  ", " PPP ", " PPP ", " PPP ", "  P  ", "     ", "     ", "     ", "     " },
            { "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     " },
            { "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     " },
    };
    private static final String[][] PBF_SHAPE_STAGE2 = {
            { "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     " },
            { "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     ", "     " },
            { "  P  ", " PPP ", " PPP ", " PPP ", "  P  ", " PPP ", "     ", "     ", "     " },
            { " PPP ", "P   P", "P   P", "P   P", " PPP ", "  P  ", "  P  ", "  P  ", " PPP " },
            { " PPP ", "P   P", "P   S", "P   P", " P P ", " P P ", " P P ", " P P ", " P P " },
            { " PPP ", "P   P", "P   P", "P   P", " PPP ", "  P  ", "  P  ", "  P  ", " PPP " },
            { "  P  ", " PPP ", " PPP ", " PPP ", "  P  ", " PPP ", "     ", "     ", "     " },
            { " FFF ", " P P ", " P P ", " P P ", " P P ", " P P ", "     ", "     ", "     " },
            { "  P  ", "  P  ", "  P  ", "  P  ", "  P  ", " PPP ", "     ", "     ", "     " },
    };
    private static final String[][] PBF_SHAPE_STAGE3 = {
            { "  P  ", "  P  ", "  P  ", "  P  ", "  P  ", " PPP ", "     ", "     ", "     " },
            { " FFF ", " P P ", " P P ", " P P ", " P P ", " P P ", "     ", "     ", "     " },
            { "  P  ", " PPP ", " PPP ", " PPP ", "  P  ", " PPP ", "     ", "     ", "     " },
            { " PPP ", "P   P", "P   P", "P   P", " PPP ", "  P  ", "  P  ", "  P  ", " PPP " },
            { " PPP ", "P   P", "P   S", "P   P", " P P ", " P P ", " P P ", " P P ", " P P " },
            { " PPP ", "P   P", "P   P", "P   P", " PPP ", "  P  ", "  P  ", "  P  ", " PPP " },
            { "  P  ", " PPP ", " PPP ", " PPP ", "  P  ", " PPP ", "     ", "     ", "     " },
            { " FFF ", " P P ", " P P ", " P P ", " P P ", " P P ", "     ", "     ", "     " },
            { "  P  ", "  P  ", "  P  ", "  P  ", "  P  ", " PPP ", "     ", "     ", "     " },
    };

    private static MultiblockShapeInfo largeBlastFurnaceShape(MultiblockMachineDefinition definition, String[][] aisles) {
        var builder = MultiblockShapeInfo.builder();

        for (String[] aisle : aisles) {
            builder.aisle(aisle);
        }
        return builder
                .where('S', (IMachineBlock) definition.getBlock(), Direction.SOUTH)
                .where('P', GTBlocks.CASING_PRIMITIVE_BRICKS.get())
                .where('F', GTBlocks.FIREBOX_BRONZE.get())
                .where(' ', Blocks.AIR.defaultBlockState())
                .build();
    }

    public static void init() {
        AC_INPUT_HATCH = WF_MACHINES.machine("ac_input_hatch",
                        MachineDefinition::new,
                        holder -> new ACHatchPartMachine(holder, GTValues.EV, false),
                        MetaMachineBlock::new, MetaMachineItem::new, ACHatchBlockEntity::new)
                .langValue("AC Input Hatch")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.AC_INPUT)
                .tier(GTValues.MV)
                .overlayTieredHullModel("ac_input_hatch")
                .register();

        AC_OUTPUT_HATCH = WF_MACHINES.machine("ac_output_hatch",
                        MachineDefinition::new,
                        holder -> new ACHatchPartMachine(holder, GTValues.EV, true),
                        MetaMachineBlock::new, MetaMachineItem::new, ACHatchBlockEntity::new)
                .langValue("AC Output Hatch")
                .rotationState(RotationState.ALL)
                .abilities(WFPartAbility.AC_OUTPUT)
                .tier(GTValues.MV)
                .overlayTieredHullModel("ac_output_hatch")
                .register();
        PRINTER = WF_MACHINES.machine("printer", holder -> new PrinterMachine(holder, GTValues.LV))
                .langValue("Data Printer")
                .rotationState(RotationState.NON_Y_AXIS)
                .tier(GTValues.LV)
                .tooltips(Component.translatable("gtceu.universal.tooltip.voltage_in",
                        FormattingUtil.formatNumbers(GTValues.V[GTValues.LV]), GTValues.VNF[GTValues.LV]))
                .workableTieredHullModel(WFCore.id("block/multiblock/printer"))
                .register();

        // Tiered EU charging station for Superb Warfare vehicles. Reuses GregTech's own charger model + state
        // property so it looks and tiers exactly like a stock Turbo Charger; charges nearby energy-based vehicles
        // that are NOT under the WFCore fluid-fuel override (those run on a fluid tank instead of EU/FE).
        VEHICLE_CHARGER = GTMachineUtils.registerTieredMachines(WF_MACHINES, "vehicle_charger",
                (holder, tier) -> new VehicleChargerMachine(holder, tier),
                (tier, builder) -> builder
                        .rotationState(RotationState.ALL)
                        .modelProperty(GTMachineModelProperties.CHARGER_STATE, ChargerMachine.State.IDLE)
                        .model(GTMachineModels.createChargerModel())
                        .langValue(GTValues.VN[tier] + " Vehicle Charging Station")
                        .tooltips(Component.translatable("gtceu.universal.tooltip.voltage_in",
                                FormattingUtil.formatNumbers(GTValues.V[tier]), GTValues.VNF[tier]),
                                Component.literal("Charges nearby Superb Warfare vehicles that run on energy"))
                        .register(),
                GTMachineUtils.ALL_TIERS);

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

        COPPER_HEATSINK = WF_MACHINES.machine("copper_heatsink", holder -> new CoolingPartMachine(holder, false))
                .langValue("Copper Heatsink")
                .rotationState(RotationState.ALL)
                .allowCoverOnFront(true)
                .allowExtendedFacing(false)
                .abilities(WFPartAbility.GPC_COOLER)
                .tooltips(Component.translatable("wfcore.machine.copper_heatsink.tooltip1"),
                        Component.translatable("wfcore.machine.copper_heatsink.tooltip2"),
                        Component.translatable("wfcore.machine.copper_heatsink.tooltip3"),
                        Component.translatable("wfcore.machine.copper_heatsink.tooltip4"))
                .tier(GTValues.HV)
                .model(GTMachineModels.createBasicMachineModel(WFCore.id("block/machine/part/copper_heatsink")))
                .register();

        STEAM_WIREMILL = WF_MACHINES.multiblock("steam_wiremill", SteamParallelMultiblockMachine::new)
                .langValue("Steam Wiremill ").rotationState(RotationState.NON_Y_AXIS)
                .recipeType(GTRecipeTypes.WIREMILL_RECIPES)
                .recipeModifier(SteamParallelMultiblockMachine::recipeModifier, true)
                .addOutputLimit(ItemRecipeCapability.CAP, 1)
                .appearanceBlock(GTBlocks.BRONZE_HULL)
                .pattern(definition -> FactoryBlockPattern.start(
                                RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK)
                        .aisle("CCCCCCC", "BWWWBKB", "ABBBBBA")
                        .aisle("CCCCCCC", "BGGGGGB", "BBBBBBB")
                        .aisle("CCCCCCC", "BGGGGGB", "BBBBBBB")
                        .aisle("CCCCCCC", "BCCCCWB", "ABBBBBA")
                        .where('C', blocks(GTBlocks.BRONZE_HULL.get()))
                        .where('B', blocks(GTBlocks.CASING_BRONZE_BRICKS.get())
                                .or(abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                                .or(abilities(PartAbility.STEAM_EXPORT_ITEMS).setPreviewCount(1))
                                .or(abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                                .or(abilities(PartAbility.STEAM).setExactLimit(1)))
                        .where('W', frames(GTMaterials.Steel))
                        .where('G', blocks(GTBlocks.CASING_STEEL_GEARBOX.get()))
                        .where('A', air())
                        .where('K', controller(blocks(definition.getBlock())))
                        .build())
                .workableCasingModel(GTCEu.id("block/casings/steam/bronze/side"),
                        GTCEu.id("block/machines/wiremill"))
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
                .model(GTMachineModels.createOverlayTieredHullMachineModel(
                        GTCEu.id("block/machine/part/computation_data_hatch")))
                .tier(GTValues.MAX)
                .register();


        MV_COMPUTATION_TRANSMISSION_HATCH = WF_MACHINES
                .machine("mv_computation_transmission_hatch",
                        holder -> new OpticalComputationHatchMachine(holder, true))
                .langValue("MV Computation Data Transmission Hatch")
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.COMPUTATION_DATA_TRANSMISSION)
                .tier(GTValues.MV)

                .model(GTMachineModels.createOverlayTieredHullMachineModel(
                        GTCEu.id("block/machine/part/computation_data_hatch")))
                .register();

        MV_COMPUTATION_RECEPTION_HATCH = WF_MACHINES
                .machine("mv_computation_reception_hatch",
                        holder -> new OpticalComputationHatchMachine(holder, false))
                .langValue("MV Computation Data Reception Hatch")
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.COMPUTATION_DATA_RECEPTION)
                .tier(GTValues.MV)
                .model(GTMachineModels.createOverlayTieredHullMachineModel(
                        GTCEu.id("block/machine/part/computation_data_hatch")))
                .register();

        MV_DATA_TRANSMISSION_HATCH = WF_MACHINES
                .machine("mv_data_transmission_hatch",
                        holder -> new OpticalDataHatchMachine(holder, true))
                .langValue("MV Optical Data Transmission Hatch")
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.OPTICAL_DATA_TRANSMISSION)
                .tier(GTValues.MV)
                .model(GTMachineModels.createOverlayTieredHullMachineModel(
                        GTCEu.id("block/machine/part/optical_data_hatch")))
                .register();

        MV_DATA_RECEPTION_HATCH = WF_MACHINES
                .machine("mv_data_reception_hatch",
                        holder -> new OpticalDataHatchMachine(holder, false))
                .langValue("MV Optical Data Reception Hatch")
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.OPTICAL_DATA_RECEPTION)
                .tier(GTValues.MV)
                .model(GTMachineModels.createOverlayTieredHullMachineModel(
                        GTCEu.id("block/machine/part/optical_data_hatch")))
                .register();


        MV_NETWORK_SWITCH = WF_MACHINES.multiblock("mv_network_switch", MVNetworkSwitchMachine::new)
                .langValue("MV Network Switch")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .recipeType(GTRecipeTypes.DUMMY_RECIPES)
                .tooltips(Component.translatable("wfcore.machine.mv_network_switch.tooltip0"),
                        Component.translatable("wfcore.machine.mv_network_switch.tooltip1"),
                        Component.translatable("wfcore.machine.mv_network_switch.tooltip2"),
                        Component.translatable("wfcore.machine.mv_network_switch.tooltip3",
                                FormattingUtil.formatNumbers(GTValues.VA[GTValues.MV])))
                .pattern(definition -> FactoryBlockPattern.start()
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "XAX", "XXX")
                        .aisle("XXX", "XSX", "XXX")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('A', blocks(WFBlocks.CONDENSED_CABLES.get()))
                        .where('X', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(7)
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2, 1))
                                .or(abilities(PartAbility.COMPUTATION_DATA_TRANSMISSION).setMinGlobalLimited(1, 1))
                                .or(abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMinGlobalLimited(1, 2))
                                // Maintenance Hatch (required once maintenance is enabled in the GT config).
                                .or(autoAbilities(true, false, false)))
                        .build())
                .shapeInfo(definition -> MultiblockShapeInfo.builder()
                        .aisle("XMX", "XSX", "XRX")
                        .aisle("XXX", "XAX", "XXX")
                        .aisle("XEX", "XXX", "TTT")
                        .where('S', definition, Direction.NORTH)
                        .where('X', WFBlocks.ALUMINIUM_SHEET_CASING.get())
                        .where('A', WFBlocks.CONDENSED_CABLES.get())
                        .where('R', MV_COMPUTATION_RECEPTION_HATCH, Direction.NORTH)
                        .where('T', MV_COMPUTATION_TRANSMISSION_HATCH, Direction.SOUTH)
                        .where('M', GTMachines.MAINTENANCE_HATCH, Direction.NORTH)
                        .where('E', GTMachines.ENERGY_INPUT_HATCH[GTValues.MV], Direction.NORTH)
                        .build())
                // Reuse GregTech's network_switch front overlay art over WFCore's aluminium casing.
                // The aluminium casing is a single all-faces texture (not a sided bottom/top/side dir),
                // so use the non-sided workable model like the Mainframe does.
                .workableCasingModel(WFCore.id("block/casings/aluminium_sheet_casing"),
                        GTCEu.id("block/multiblock/network_switch"))
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
                        .aisle(" XXXXX ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .aisle(" XXXXX ", " DEEED ", " DEEED ", " DEEED ", " BBBBB ", "       ", "       ")
                        .aisle(" XXXXX ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .aisle("AAAAAAA", "CCCCCCC", "CGGGGGC", "CGGGGGC", "CGGGGGC", "CCCCCCC", "AAAAAAA")
                        .aisle("AAAAAAA", "CCCCCCC", "CG   GS", "CG   GC", "CG   GC", "CCCCCCC", "AAAAAAA")
                        .aisle("AAAAAAA", "CCCCCCC", "CGGGGGC", "CGGGGGC", "CGGGGGC", "CCCCCCC", "AAAAAAA")
                        .aisle(" XXXXX ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .aisle(" XXXXX ", " D   F ", " D   F ", " D   F ", " BBBBB ", "       ", "       ")
                        .aisle(" XXXXX ", " B   F ", " B   F ", " B   F ", " BBBBB ", "       ", "       ")
                        .aisle(" XXXXX ", " D   F ", " D   F ", " D   F ", " BBBBB ", "       ", "       ")
                        .aisle(" XXXXX ", " BBBBB ", " BBBBB ", " BBBBB ", " BBBBB ", "       ", "       ")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('A', blocks(GTBlocks.STEEL_HULL.get()))
                        .where('B', blocks(GTBlocks.MACHINE_CASING_LV.get()))
                        .where('X', blocks(GTBlocks.MACHINE_CASING_LV.get())
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(2,
                                        1))
                                .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1).setMaxGlobalLimited(1,
                                        1))
                                // Optional Import Fluid Hatch: only needed for researches that list a fluid cost.
                                .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1, 1))
                                // Optional Optical Data Transmission Hatch (Research Data Output): the unit is a
                                // research producer, so it transmits; wire a Data Bank to it to two-way sync.
                                .or(abilities(PartAbility.OPTICAL_DATA_TRANSMISSION).setMaxGlobalLimited(1, 1))
                                .or(abilities(PartAbility.COMPUTATION_DATA_RECEPTION))
                                // Maintenance Hatch (required once maintenance is enabled in the GT config).
                                .or(autoAbilities(true, false, false)))
                        .where('C', blocks(GTBlocks.CASING_STEEL_SOLID.get()))
                        .where('D', frames(WFMaterials.GalvanizedSteel))
                        .where('E', blocks(GTBlocks.FIREBOX_STEEL.get()))
                        .where('F', blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                        .where('G', blocks(GTBlocks.COIL_CUPRONICKEL.get()))
                        .where(' ', any())
                        .build())
                .workableCasingModel(GTCEu.id("block/casings/voltage/lv/side"),
                        WFCore.id("block/multiblock/research_unit"))
                .register();

        LARGE_TRANSFORMER = WF_MACHINES.multiblock("large_transformer", LargeTransformerMachine::new)
                .langValue("Large Transformer")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .allowExtendedFacing(false)
                .pattern(definition -> FactoryBlockPattern.start(
                                RelativeDirection.BACK, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("XXX", "XXX", "XXX")
                        .aisle("XXX", "SPX", "XXX")
                        .aisle("XXX", "XXX", "XXX")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('P', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()))
                        .where('X', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get()).setMinGlobalLimited(8)
                                .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(PartAbility.OUTPUT_ENERGY).setMinGlobalLimited(1))
                                .or(abilities(WFPartAbility.AC_INPUT).setMaxGlobalLimited(1))
                                .or(abilities(WFPartAbility.AC_OUTPUT).setMaxGlobalLimited(1))
                                // Coolant hatch is only needed for the optional AC conversion, so keep it optional.
                                .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1)))
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
                                RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT)
                        .aisle("  D  ", "  D  ", "  D  ", "  D  ", "  D  ", " DDD ", "     ", "     ", "     ")
                        .aisle(" CCC ", " D#D ", " D#D ", " D#D ", " D#D ", " D#D ", "     ", "     ", "     ")
                        .aisle("  B  ", " BBB ", " BBB ", " BBB ", "  B  ", " DDD ", "     ", "     ", "     ")
                        .aisle(" BBB ", "B###B", "B###B", "B###B", " BBB ", "  B  ", "  B  ", "  B  ", " BBB ")
                        .aisle(" BBB ", "B###B", "B###S", "B###B", " B#B ", " B#B ", " B#B ", " B#B ", " B#B ")
                        .aisle(" BBB ", "B###B", "B###B", "B###B", " BBB ", "  B  ", "  B  ", "  B  ", " BBB ")
                        .aisle("  B  ", " BBB ", " BBB ", " BBB ", "  B  ", " DDD ", "     ", "     ", "     ")
                        .aisle(" CCC ", " D#D ", " D#D ", " D#D ", " D#D ", " D#D ", "     ", "     ", "     ")
                        .aisle("     ", "  D  ", "  D  ", "  D  ", "  D  ", " DDD ", "     ", "     ", "     ")
                        .where('S', controller(blocks(definition.getBlock())))
                        .where('B', blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get())
                                .or(abilities(PartAbility.IMPORT_ITEMS))
                                .or(abilities(PartAbility.EXPORT_ITEMS))
                                .or(abilities(PartAbility.EXPORT_FLUIDS)))
                        .where('D', blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get()).or(air()))
                        .where('#', air())
                        .where('C', blocks(GTBlocks.FIREBOX_BRONZE.get()).or(air()))
                        .where(' ', any())
                        .build())
                // JEI previews the three stages: core only, core + one side chimney, core + both.
                .shapeInfos(definition -> List.of(
                        largeBlastFurnaceShape(definition, PBF_SHAPE_STAGE1),
                        largeBlastFurnaceShape(definition, PBF_SHAPE_STAGE2),
                        largeBlastFurnaceShape(definition, PBF_SHAPE_STAGE3)))
                .model(createWorkableCasingMachineModel(
                        GTCEu.id("block/casings/solid/machine_primitive_bricks"),
                        GTCEu.id("block/multiblock/primitive_blast_furnace"))
                        .andThen(b -> b.addDynamicRenderer(DynamicRenderHelper::createPBFLavaRender)))
                .hasBER(true)
                .register();

        PRIMITIVE_ALLOYER = WF_MACHINES.multiblock("primitive_alloyer", PrimitiveAlloyerMachine::new)
                .langValue("Primitive Alloyer")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.PRIMITIVE_ALLOYER)
                .tooltips(Component.translatable("wfcore.machine.primitive_alloyer.tooltip1"),
                        Component.translatable("wfcore.machine.primitive_alloyer.tooltip2"),
                        Component.translatable("wfcore.machine.primitive_alloyer.tooltip3"))
                .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            { " AAA ", " CBC ", " CBC ", " CBC ", " CCC " },
                            { "ABBBA", "C###C", "C###C", "C###C", "C###C" },
                            { "ABBBA", "B###S", "B###B", "B###B", "C###C" },
                            { "ABBBA", "C###C", "C###C", "C###C", "C###C" },
                            { " AAA ", " CBC ", " CBC ", " CBC ", " CCC " },
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.FIREBOX_BRONZE.get())) // gtceu:bronze_firebox_casing x12
                            .where('#', air()) // gtceu:bronze_firebox_casing x12
                            .where('B', blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get())) // gtceu:firebricks x20
                            .where('C', blocks(GTBlocks.CASING_BRONZE_BRICKS.get()) // gtceu:steam_machine_casing x36
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1))
                                    .or(abilities(PartAbility.EXPORT_FLUIDS).setMinGlobalLimited(1)))
                            .where(' ', any())
                            .build();
                })
                .model(createWorkableCasingMachineModel(
                        GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                        GTCEu.id("block/multiblock/primitive_blast_furnace"))
                        .andThen(b -> b.addDynamicRenderer(DynamicRenderHelper::makeRecipeFluidAreaRender)))
                .hasBER(true)
                .register();

        STRANDCASTER = WF_MACHINES.multiblock("strandcaster", StrandcasterMachine::new)
                .langValue("Strandcaster")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.STRANDCASTER)
                .recipeModifier(StrandcasterMachine::modifyRecipe)
                .tooltips(Component.translatable("wfcore.machine.strandcaster.tooltip1"),
                        Component.translatable("wfcore.machine.strandcaster.tooltip2"),
                        Component.translatable("wfcore.machine.strandcaster.tooltip3"))
                .appearanceBlock(GTBlocks.CASING_BRONZE_BRICKS)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            { "AAA", "BBB", "CBC", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "BBB", "BBB", "BBB" },
                            { "AAA", "B S", "B B", "BBB" },
                            { "AAA", "BBB", "BBB", "BBB" },
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.BRONZE_HULL.get())) // gtceu:bronze_machine_casing x27
                            .where('B', blocks(GTBlocks.CASING_BRONZE_BRICKS.get()) // gtceu:steam_machine_casing x44
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(2))
                                    .or(abilities(PartAbility.EXPORT_ITEMS).setMinGlobalLimited(1)))
                            .where('C', frames(GTMaterials.Bronze)) // gtceu:bronze_frame x12
                            .where(' ', any())
                            .build();
                })
                .model(createWorkableCasingMachineModel(
                        GTCEu.id("block/casings/solid/machine_casing_bronze_plated_bricks"),
                        GTCEu.id("block/machines/fluid_solidifier"))
                        .andThen(b -> b.addDynamicRenderer(DynamicRenderHelper::makeRecipeFluidAreaRender)))
                .hasBER(true)
                .register();

        GAS_EXTRACTOR = WF_MACHINES.multiblock("gas_extractor", WorkableElectricMultiblockMachine::new)
                .langValue("Large Gas Extractor")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.GAS_EXTRACTOR)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(GTBlocks.CASING_STAINLESS_CLEAN)
                .pattern(definition -> {
                    var pattern = FactoryBlockPattern.start(
                            RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT);
                    for (String[] aisle : GasExtractorStructure.AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.STEEL_HULL.get())) // gtceu:steel_machine_casing x40
                            .where('B', blocks(GTBlocks.CASING_STEEL_PIPE.get())) // gtceu:steel_pipe_casing x34
                            .where('C', blocks(GTBlocks.CASING_STEEL_SOLID.get()) // gtceu:solid_machine_casing x44
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.EXPORT_ITEMS).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(3))
                                    .or(abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(3)))
                            .where('D', frames(WFMaterials.GalvanizedSteel)) // wfcore:galvanized_steel_frame x16
                            .where('E', blocks(WFBlocks.GALVANIZED_STEEL_CASING.get())) // gtceu:atomic_casing x42
                            .where('F', blocks(GTBlocks.CASING_STAINLESS_CLEAN.get())) // gtceu:clean_machine_casing x80
                            .where('G', frames(GTMaterials.StainlessSteel)) // gtceu:stainless_steel_frame x16
                            .where(' ', any())
                            .build();
                })
                .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_clean_stainless_steel"),
                        GTCEu.id("block/machines/air_scrubber"))
                .register();

        RADAR = WF_MACHINES.multiblock("radar", RadarMachine::new,
                        MetaMachineBlock::new, MetaMachineItem::new, RadarBlockEntity::new)
                .langValue("Radar")
                .appearanceBlock(WFBlocks.ALUMINIUM_SHEET_CASING)
                .hasBER(false)
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
                                    .or(abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMaxGlobalLimited(1, 1))
                                    // Maintenance Hatch (required once maintenance is enabled in the GT config).
                                    .or(autoAbilities(true, false, false)))
                            .where('E', blocks(WFBlocks.GALVANIZED_STEEL_CASING.get()))
                            .where('F', blocks(WFBlocks.CONDENSED_CABLES.get()))
                            .where('G', blocks(matBlock(WFMaterials.GalvanizedSteel)))
                            // Requires the bolted casing block: built with the unbolted casing, then bolted
                            // into place with the bolt gun to complete the structure (1.12.2 mechanic).
                            .where('H', blocks(WFBlocks.BOLTABLE_CASING_BOLTED.get()))
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
                .recipeType(VehicleFactoryRecipes.LIGHT_GROUND_VEHICLE_FACTORY)
                .appearanceBlock(GTBlocks.STEEL_HULL)
                .allowFlip(false)
                .allowExtendedFacing(false)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            {"HAA         AAH", "CCC         CCC", "AAA         AAA", "               ",
                                    "               ", "               ", "               ", "               ",
                                    "               ", "               ", "               "},
                            {"HAAAAAAAAAAAAAH", "CCC         CCC", "AAA         AAA", " C           C ",
                                    " C           C ", " C           C ", " A           A ", "  C         C  ",
                                    "   C       C   ", "    GFFFFFG    ", "               "},
                            {"HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", " F           F ",
                                    "               ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               "},
                            {"  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    " F           F ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               "},
                            {"  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    "               ", " F           F ", "               ", "               ",
                                    "   F       F   ", "               ", "               "},
                            {"HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", "               ",
                                    "               ", "               ", " F           F ", "               ",
                                    "   F       F   ", "               ", "               "},
                            {"HAACACACACACAAH", "CCCDEEEEEEEDCCS", "AAA         AAA", " C           C ",
                                    " C           C ", " C           C ", " A           A ", "  C         C  ",
                                    "   C       C   ", "    C     C    ", "     G   G     "},
                            {"HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", "               ",
                                    "               ", "               ", " F           F ", "               ",
                                    "   F       F   ", "               ", "               "},
                            {"  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    "               ", " F           F ", "               ", "               ",
                                    "   F       F   ", "               ", "               "},
                            {"  BCACACACACB  ", "  CDEEEEEEEDC  ", "               ", "               ",
                                    " F           F ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               "},
                            {"HAACACACACACAAH", "CCCDEEEEEEEDCCC", "AAA         AAA", " F           F ",
                                    "               ", "               ", "               ", "  F         F  ",
                                    "               ", "               ", "               "},
                            {"HAAAAAAAAAAAAAH", "CCC         CCC", "AAA         AAA", " C           C ",
                                    " C           C ", " C           C ", " A           A ", "  C         C  ",
                                    "   C       C   ", "    GFFFFFG    ", "               "},
                            {"HAA         AAH", "CCC         CCC", "AAA         AAA", "               ",
                                    "               ", "               ", "               ", "               ",
                                    "               ", "               ", "               "},
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

        TANK_ASSEMBLY = WF_MACHINES
                .multiblock("tank_assembly", LightGroundVehicleFactoryMachine::new,
                        MetaMachineBlock::new, MetaMachineItem::new, VehicleFactoryBlockEntity::new)
                .langValue("Tank Assembly Line")
                .recipeType(VehicleFactoryRecipes.TANK_ASSEMBLY)
                .appearanceBlock(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM)
                .allowFlip(false)
                .allowExtendedFacing(false)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            {"AAA       AAA", "DDD       DDD", "             ", "             ", "             ", "             ", "    H   H    ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDD", " A         A ", " A         A ", " A         A ", " A         A ", " AFGFGFGFGFA ", " A  I   I  A ", " AFGFGFGFGFA "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", " A         A ", "             ", "             ", "    H   H    ", "             ", "             "},
                            {" AABCCCCCBAA ", "             ", "             ", " E         E ", "             ", "             ", "             ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", " A         A ", "             ", "             ", "    H   H    ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDD", " A         A ", " A         A ", " A         A ", " A         A ", " AFGFGFGFGFA ", " A  I   I  A ", " AFGFGFGFGFA "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", " A         A ", "             ", "             ", "    H   H    ", "             ", "             "},
                            {" AABCCCCCBAA ", "             ", "             ", " E         E ", "             ", "             ", "             ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", " A         A ", "             ", "             ", "    H   H    ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDD", " A         A ", " A         A ", " A         A ", " A         A ", " AFGFGFGFGFA ", " A  I   I  A ", " AFGFGFGFGFA "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", " A         A ", "             ", "             ", "    H   H    ", "             ", "             "},
                            {" AABCCCCCBAA ", "             ", "             ", " E         E ", "             ", "             ", "             ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", " A         A ", "             ", "             ", "    H   H    ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDS", " A         A ", " A         A ", " A         A ", " A         A ", " AFGFGFGFGFA ", " A  I   I  A ", " AFGFGFGFGFA "},
                            {"AAA       AAA", "DDD       DDD", "             ", "             ", "             ", "             ", "    H   H    ", "             ", "             "},
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM.get())
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.DATA_ACCESS))) // gtceu:titanium_turbine_casing x152
                            .where('B', blocks(GTBlocks.REINFORCED_STONE.get())) // gtceu:reinforced_stone x26
                            .where('C', blocks(GTBlocks.LIGHT_CONCRETE.get())) // gtceu:light_concrete x65
                            .where('D', blocks(GTBlocks.METAL_SHEETS.get(DyeColor.BLACK).get())) // gtceu:black_metal_sheet x71
                            .where('E', blocks(GTBlocks.LAMPS.get(DyeColor.YELLOW).get())) // gtceu:yellow_lamp[bloom=true,powered=false,lit=true,inverted=false] x6
                            .where('F', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x40
                            .where('G', frames(GTMaterials.Polytetrafluoroethylene)) // gtceu:polytetrafluoroethylene_frame x32
                            .where('H', frames(WFMaterials.GalvanizedSteel)) // wfcore:galvanized_steel_frame x16
                            .where('I', blocks(GTBlocks.CASING_STAINLESS_STEEL_GEARBOX.get())) // gtceu:stainless_steel_gearbox x8
                            .where(' ', any()).build();
                })
                .workableCasingModel(WFCore.id("block/casings/machine_casing_turbine_titanium"),
                        GTCEu.id("block/machines/assembler"))
                .register();

        LIGHT_PLANE_ASSEMBLER = WF_MACHINES
                .multiblock("light_plane_assembler", LightGroundVehicleFactoryMachine::new,
                        MetaMachineBlock::new, MetaMachineItem::new, VehicleFactoryBlockEntity::new)
                .langValue("Light Plane Assembler")
                .recipeType(VehicleFactoryRecipes.LIGHT_PLANE_ASSEMBLER)
                .appearanceBlock(GTBlocks.STEEL_HULL)
                .allowFlip(false)
                .allowExtendedFacing(false)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            {"AAA       AAA", "DDD       DDD", "             ", "             ", "             ", "             ", "             ", "             "},
                            {"AAABCCCCCBAAA", "DDD       DDD", " D         D ", " D         D ", " D         D ", " D         D ", " D         D ", " ADDDDDDDDDA "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", "             ", "             ", "  E       E  ", "             ", "  E       E  "},
                            {"  ABCCCCCBA  ", "  D       D  ", "             ", "             ", "             ", "             ", "   E     E   ", "   EEEEEEE   "},
                            {"  ABCCCCCBA  ", "  D       D  ", "             ", "             ", "             ", "             ", "             ", "    F F F    "},
                            {"  ABCCCCCBA  ", "  D       D  ", "             ", "             ", "             ", "             ", "             ", "     F F     "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", "             ", "             ", "             ", "             ", "    F F F    "},
                            {"AAABCCCCCBAAA", "DDD       DDS", " A         D ", " D         D ", " D         D ", " DE       ED ", " D E     E D ", " AEEEEEEEEEA "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", "             ", "             ", "             ", "             ", "    F F F    "},
                            {"  ABCCCCCBA  ", "  D       D  ", "             ", "             ", "             ", "             ", "             ", "     F F     "},
                            {"  ABCCCCCBA  ", "  D       D  ", "             ", "             ", "             ", "             ", "             ", "    F F F    "},
                            {"  ABCCCCCBA  ", "  D       D  ", "             ", "             ", "             ", "             ", "   E     E   ", "   EEEEEEE   "},
                            {"AAABCCCCCBAAA", "DDD       DDD", "             ", "             ", "             ", "  E       E  ", "             ", "  E       E  "},
                            {"AAABCCCCCBAAA", "DDD       DDD", " A         D ", " D         D ", " D         D ", " D         D ", " D         D ", " ADDDDDDDDDA "},
                            {"AAA       AAA", "DDD       DDD", "             ", "             ", "             ", "             ", "             ", "             "},
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.STEEL_HULL.get())
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.DATA_ACCESS))) // gtceu:steel_machine_casing x74
                            .where('B', blocks(GTBlocks.REINFORCED_STONE.get())) // gtceu:reinforced_stone x26
                            .where('C', blocks(GTBlocks.LIGHT_CONCRETE.get())) // gtceu:light_concrete x65
                            .where('D', blocks(GTBlocks.CASING_STEEL_SOLID.get())) // gtceu:solid_machine_casing x111
                            .where('E', frames(GTMaterials.Steel)) // gtceu:steel_frame x39
                            .where('F', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x16
                            .where(' ', any()).build();
                })
                .workableCasingModel(GTCEu.id("block/casings/steam/steel/side"),
                        GTCEu.id("block/machines/assembler"))
                .register();

        HEAVY_PLANE_ASSEMBLER = WF_MACHINES
                .multiblock("heavy_plane_assembler", LightGroundVehicleFactoryMachine::new,
                        MetaMachineBlock::new, MetaMachineItem::new, VehicleFactoryBlockEntity::new)
                .langValue("Heavy Plane Assembler")
                .recipeType(VehicleFactoryRecipes.HEAVY_PLANE_ASSEMBLER)
                .appearanceBlock(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM)
                .allowFlip(false)
                .allowExtendedFacing(false)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            {"    AAAAAAAAAAAAAAAAAAAAAAAAAA    ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  "},
                            {" IIIACCCCCCCCCCCCCCCCCCCCCCCCAIII ", " III                          III ", " III                          III ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  "},
                            {" IIIACCCCCCCCCCCCCCCCCCCCCCCCAIII ", " III                          III ", " III                          III ", "  B                            B  ", "  B                            B  ", "  B                            B  ", "  B                            B  ", "  B                            B  ", "  BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB  ", "  B                            B  "},
                            {" IIIACCCCCCCCCCCCCCCCCCCCCCCCAIII ", " III                          III ", " III                          III ", "                                  ", "  G                            G  ", "  G                            G  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "                                  "},
                            {"    ACCCCCCCCCCCCCCCCCCCCCCCCA    ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "                                  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "                                  "},
                            {"AAAAACCCCCCCCCCCCCCCCCCCCCCCCAAAAA", "                                  ", "                                  ", "                                  ", "                                  ", "  G                            G  ", "  G                            G  ", "  B                            B  ", "  B  T    T    T    T    T    TB  ", "                                  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", " E E                          E E ", " EEE                          EEE ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "                                  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " EFE                          EFE ", "                                  ", "                                  ", "                                  ", "  G                            G  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "                                  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "                                  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " EFE                          EFE ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B  T    T    T    T    T    TB  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B  T    T    T    T    T    TB  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B  T    T    T    T    T    TB  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " EFE                          EFE ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "  B                            B  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " FFF                          FFF ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "                                  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", "                                  ", " EFE                          EFE ", "                                  ", "                                  ", "                                  ", "  G                            G  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "                                  "},
                            {"ACCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCA", " E E                          E E ", " EEE                          EEE ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "  B                            B  ", "  B  T    T    T    T    T    TB  ", "                                  "},
                            {"AAAAACCCCCCCCCCCCCCCCCCCCCCCCAAAAA", "                                  ", "                                  ", "                                  ", "                                  ", "  G                            G  ", "  G                            G  ", "  B                            B  ", "  B T T  T T  T T  T T  T T  T B  ", "                                  "},
                            {"    ACCCCCCCCCCCCCCCCCCCCCCCCA    ", "                                  ", "                                  ", "                                  ", "                                  ", "  B                            B  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "                                  "},
                            {" IIIACCCCCCCCCCCCCCCCCCCCCCCCAIII ", " III                          III ", " III                          III ", "                                  ", "  G                            G  ", "  G                            G  ", "                                  ", "  B                            B  ", "  BT   TT   TT   TT   TT   TT  B  ", "                                  "},
                            {" IIIACCCCCCCCCCCCCCCCCCCCCCCCAIII ", " III                          IIS ", " III                          III ", "  B                            B  ", "  B                            B  ", "  B                            B  ", "  B                            B  ", "  B                            B  ", "  BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB  ", "  B                            B  "},
                            {" IIIACCCCCCCCCCCCCCCCCCCCCCCCAIII ", " III                          III ", " III                          III ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  "},
                            {"    AAAAAAAAAAAAAAAAAAAAAAAAAA    ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  ", "                                  "},
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.REINFORCED_STONE.get())) // gtceu:reinforced_stone x128
                            // Plain casing: carries NO IO. All energy/item/fluid/data parts are confined to the
                            // corner-pillar 'I' cubes below, so hatches can't be placed anywhere else.
                            .where('B', blocks(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM.get())) // titanium_turbine_casing x224
                            // IO zone: the bottom 3x3x3 of each of the four corner pillars (the only IO-capable
                            // blocks in the whole structure).
                            .where('I', blocks(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM.get())
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.DATA_ACCESS))) // gtceu:titanium_turbine_casing x107
                            .where('C', blocks(GTBlocks.LIGHT_CONCRETE.get())) // gtceu:light_concrete x880
                            .where('E', frames(WFMaterials.GalvanizedSteel)) // wfcore:galvanized_steel_frame x36
                            .where('F', blocks(GTBlocks.CASING_TITANIUM_GEARBOX.get())) // gtceu:atomic_casing x92
                            .where('G', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x20
                            // Titanium-frame diamond box-lattice roof (the "boxes on top").
                            .where('T', frames(GTMaterials.Titanium)) // gtceu:titanium_frame x261
                            .where(' ', any()).build();
                })
                .workableCasingModel(WFCore.id("block/casings/machine_casing_turbine_titanium"),
                        GTCEu.id("block/machines/assembler"))
                .register();

        HEAVY_VEHICLE_DEPOT = WF_MACHINES
                .multiblock("heavy_vehicle_depot", LightGroundVehicleFactoryMachine::new,
                        MetaMachineBlock::new, MetaMachineItem::new, VehicleFactoryBlockEntity::new)
                .langValue("Heavy Vehicle Depot")
                .recipeType(VehicleFactoryRecipes.HEAVY_VEHICLE_DEPOT)
                .appearanceBlock(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM)
                .allowFlip(false)
                .allowExtendedFacing(false)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            {"AAA           AAA", "CCC           CCC", "AAA           AAA", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 "},
                            {"AAABBBBBBBBBBBAAA", "CDC           CDC", "AAA           AAA", "GG             GG", "AA             AA", " A             A ", "  A           A  ", "  A           A  ", "   A         A   ", "    AA     AA    ", "      AAAAA      "},
                            {"BAAAAAAAAAAAAAAAB", "BDCEFFFFFFFFFECDB", "BAA           AAB", "HG             GH", "AA             AA", " I             I ", "  I           I  ", "  I           I  ", "   A   B B   A   ", "    II  A  II    ", "      IIAII      "},
                            {"AAACCCCCCCCCCCAAA", "CDCEFFFFFFFFFECDC", "AAA           AAA", "GG             GG", "AA             AA", " A             A ", "  A           A  ", "  A           A  ", "   A         A   ", "    AA     AA    ", "      AAAAA      "},
                            {"AAACCCCCCCCCCCAAA", "CCCEFFFFFFFFFECCC", "AAA           AAA", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {" AACCCCCCCCCCCAA ", "  BEFFFFFFFFFEB  ", " BA           AB ", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {" AACCCCCCCCCCCAA ", "  BEFFFFFFFFFEB  ", " BA           AB ", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A   B B   A   ", "    JJ  A  JJ    ", "      JJAJJ      "},
                            {" AACCCCCCCCCCCAA ", "  BEFFFFFFFFFEB  ", " BA           AB ", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {"AAACCCCCCCCCCCAAA", "CCCEFFFFFFFFFECCC", "AAA           AAA", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {"AAACCCCCCCCCCCAAA", "CDCEFFFFFFFFFEADC", "AAA           AAA", "GG             GG", "AA             AA", " A             A ", "  A           A  ", "  A           A  ", "   A         A   ", "    AA     AA    ", "      AAAAA      "},
                            {"BAACCCCCCCCCCCAAA", "BDCEFFFFFFFFFECDS", "BAA           AAA", "HG             GH", "AA             AA", " I             I ", "  I           I  ", "  I           I  ", "   A   B B   A   ", "    II  A  II    ", "      IIAII      "},
                            {"AAACCCCCCCCCCCAAA", "CDCEFFFFFFFFFECDC", "AAA           AAA", "GG             GG", "AA             AA", " A             A ", "  A           A  ", "  A           A  ", "   A         A   ", "    AA     AA    ", "      AAAAA      "},
                            {"AAACCCCCCCCCCCAAA", "CCCEFFFFFFFFFECCC", "AAA           AAA", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {" AACCCCCCCCCCCAA ", "  BEFFFFFFFFFEB  ", " BA           AB ", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {" AACCCCCCCCCCCAA ", "  BEFFFFFFFFFEB  ", " BA           AB ", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A   B B   A   ", "    JJ  A  JJ    ", "      JJAJJ      "},
                            {" AACCCCCCCCCCCAA ", "  BEFFFFFFFFFEB  ", " BA           AB ", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {"AAACCCCCCCCCCCAAA", "CCCEFFFFFFFFFECCC", "AAA           AAA", " H             H ", " A             A ", " J             J ", "  J           J  ", "  J           J  ", "   A         A   ", "    JJ     JJ    ", "      JJAJJ      "},
                            {"AAACCCCCCCCCCCAAA", "CDCEFFFFFFFFFECDC", "AAA           AAA", "GG             GG", "AA             AA", " A             A ", "  A           A  ", "  A           A  ", "   A         A   ", "    AA     AA    ", "      AAAAA      "},
                            {"BAAAAAAAAAAAAAAAB", "BDCEFFFFFFFFFECDB", "BAA           AAB", "HG             GH", "AA             IA", " I             I ", "  I           I  ", "  I           I  ", "   A         A   ", "    II     II    ", "      IIAII      "},
                            {"AAABBBBBBBBBBBAAA", "CDC           CDC", "AAA           AAA", "GG             GG", "AA             AA", " A             A ", "  A           A  ", "  A           A  ", "   A   B B   A   ", "    AA  A  AA    ", "      AAAAA      "},
                            {"AAA           AAA", "CCC           CCC", "AAA           AAA", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 ", "                 "},
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM.get())
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.DATA_ACCESS))) // gtceu:titanium_turbine_casing x430
                            .where('B', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x71
                            .where('C', blocks(GTBlocks.METAL_SHEETS.get(DyeColor.BLACK).get())) // gtceu:black_metal_sheet x230
                            .where('D', blocks(GTBlocks.CASING_STAINLESS_STEEL_GEARBOX.get())) // gtceu:stainless_steel_gearbox x18
                            .where('E', blocks(GTBlocks.REINFORCED_STONE.get())) // gtceu:reinforced_stone x34
                            .where('F', blocks(GTBlocks.LIGHT_CONCRETE.get())) // gtceu:light_concrete x153
                            .where('G', blocks(WFBlocks.ALUMINIUM_SHEET_CASING.get())) // wfcore:aluminium_sheet_casing x30
                            .where('H', frames(WFMaterials.GalvanizedSteel)) // wfcore:galvanized_steel_frame x26
                            .where('I', frames(GTMaterials.Polytetrafluoroethylene)) // gtceu:polytetrafluoroethylene_frame x43
                            .where('J', blocks(GTBlocks.CASING_LAMINATED_GLASS.get())) // gtceu:laminated_glass x140
                            .where(' ', any()).build();
                })
                .workableCasingModel(WFCore.id("block/casings/machine_casing_turbine_titanium"),
                        GTCEu.id("block/machines/assembler"))
                .register();

        HELICOPTER_ASSEMBLER = WF_MACHINES
                .multiblock("helicopter_assembler", LightGroundVehicleFactoryMachine::new,
                        MetaMachineBlock::new, MetaMachineItem::new, VehicleFactoryBlockEntity::new)
                .langValue("Helicopter Assembler")
                .recipeType(VehicleFactoryRecipes.HELICOPTER_ASSEMBLER)
                .appearanceBlock(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM)
                .allowFlip(false)
                .allowExtendedFacing(false)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            { " XXXX     XXXX ", " A           A ", " B           B ", " A           A ", " B           B ", " A           A ", "               ", "               ", "               " },
                            { "XBBBBXXXXXBBBXX", "ACADD     DDACA", "BCB         BCB", "ACA         ACA", "BCB         BCB", "ACA         ACA", " A           A ", " A           A ", " A           A " },
                            { "XBXXXBBBBBXXXBX", " ADEEDDDDDEEDA ", " B           B ", " A           A ", " B           B ", " AB         BA ", " A B       B A ", " B  B     B  B ", " ABBABBBBBABBA " },
                            { "XBXBBXXXXXBXXBX", " DEEEEEEEEEEED ", "               ", "               ", "               ", "               ", " A           A ", " A           A ", " A           A " },
                            { "XBXBXBBBBBXBXBX", " DEEEEEEEEEEED ", "               ", "               ", "               ", "               ", "               ", "               ", "               " },
                            { " XBXBXXXXXBXBX ", "  DEEEEEEEEED  ", "               ", "               ", "               ", "               ", "               ", "               ", "               " },
                            { " XBXBXXXXXBXBX ", "  DEEEEEEEEED  ", "               ", "               ", "               ", "               ", "               ", "               ", "               " },
                            { " XBXBXXXXXBXBS ", "  DEEEEEEEEED  ", "               ", "               ", "               ", "               ", "               ", "               ", "               " },
                            { " XBXBXXXXXBXBX ", "  DEEEEEEEEED  ", "               ", "               ", "               ", "               ", "               ", "               ", "               " },
                            { " XBXBXXXXXBXBX ", "  DEEEEEEEEED  ", "               ", "               ", "               ", "               ", "               ", "               ", "               " },
                            { "XBXBXBBBBBXBXBX", " DEEEEEEEEEEED ", "               ", "               ", "               ", "               ", "               ", "               ", "               " },
                            { "XBXXBXXXXXBXXBX", " DEEEEEEEEEEED ", "               ", "               ", "               ", "               ", " A           A ", " A           A ", " A           A " },
                            { "XBXXXBBBBBXXXBX", " ADEEDDDDDEEDA ", " B           B ", " A           A ", " B           B ", " AB         BA ", " A B       B A ", " B  B     B  B ", " ABBABBBBBABBA " },
                            { "XXBBBXXXXXBBBXX", "ACADD     DDACA", "BCB         BCB", "ACA         ACA", "BCB         BCB", "ACA         ACA", " A           A ", " A           A ", " A           A " },
                            { " XXXX     XXXX ", " A           A ", " B           B ", " A           A ", " B           B ", " A           A ", "               ", "               ", "               " },
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM.get()))
                            .where('X', blocks(WFBlocks.MACHINE_CASING_TURBINE_TITANIUM.get())
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.DATA_ACCESS))) // wfcore:machine_casing_turbine_titanium x210
                            .where('B', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x140
                            .where('C', blocks(WFBlocks.CONDENSED_CABLES.get())) // wfcore:condensed_cables x20
                            .where('D', blocks(GTBlocks.REINFORCED_STONE.get())) // gtceu:reinforced_stone x40
                            .where('E', blocks(GTBlocks.LIGHT_CONCRETE.get())) // gtceu:light_concrete x97
                            .where(' ', any()).build();
                })
                .workableCasingModel(WFCore.id("block/casings/machine_casing_turbine_titanium"),
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

        MISSILE_FACTORY = WF_MACHINES.multiblock("missile_factory", MissileFactoryMachine::new)
                .langValue("Missile Factory")
                // No explicit .tooltips() for the singular "...machine.missile_factory.tooltip" key:
                // MetaMachineBlock.appendHoverText already auto-adds that lang key, so passing it here too
                // would print the line twice.
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.MISSILE_FACTORY)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(WFBlocks.REINFORCED_STAINLESS_CASING)
                .pattern(definition -> {
                    var pattern = FactoryBlockPattern.start(
                            RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT);
                    for (String[] aisle : MissileBuildingStructure.AISLES) {
                        pattern.aisle(aisle);
                    }
                    // No EXPORT_ITEMS ability: finished missiles complete into the controller's internal
                    // core store (MissileFactoryMachine.missileStore) and can only leave via a linked
                    // Missile Launch Silo — a player can never hold one.
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(WFBlocks.GALVANIZED_STEEL_CASING.get()) // gtceu:solid_machine_casing x115
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2, 1))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1, 1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2, 1))
                                    // Maintenance Hatch (required once maintenance is enabled in the GT config).
                                    .or(autoAbilities(true, false, false)))
                            .where('B', blocks(WFBlocks.CONCRETE_BASE.get())) // x145
                            .where('C', blocks(WFBlocks.GALVANIZED_STEEL_CASING.get())) // gtceu:atomic_casing x104
                            .where('D', blocks(WFBlocks.REINFORCED_STAINLESS_CASING.get())) // gtceu:sturdy_machine_casing x327
                            .where('E', frames(WFMaterials.GalvanizedSteel)) // wfcore:galvanized_steel_frame x133
                            .where('F', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x36
                            .where('G', blocks(GTBlocks.METAL_SHEETS.get(DyeColor.LIGHT_GRAY).get())) // x124
                            .where(' ', any())
                            .build();
                })
                .workableCasingModel(WFCore.id("block/casings/reinforced_stainless_casing"),
                        GTCEu.id("block/machines/assembler"))
                .register();

        MISSILE_LAUNCHER = WF_MACHINES.multiblock("missile_launcher", MissileLauncherMachine::new,
                MetaMachineBlock::new, MetaMachineItem::new, MissileLauncherBlockEntity::new)
                .langValue("Missile Launch Silo")
                .hasBER(false)
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(GCYMBlocks.CASING_INDUSTRIAL_STEAM)
                .allowFlip(false)
                .pattern(definition -> {
                    var pattern = FactoryBlockPattern.start(
                            RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT);
                    for (String[] aisle : MissileLauncherStructure.AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(WFBlocks.REINFORCED_STAINLESS_CASING.get()) // gtceu:solid_machine_casing x47
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2, 1))
                                    // Maintenance Hatch (required once maintenance is enabled in the GT config).
                                    .or(autoAbilities(true, false, false)))
                            .where('B', blocks(WFBlocks.CONCRETE_BASE.get())) // x36
                            .where('C', frames(WFMaterials.GalvanizedSteel)) // wfcore:galvanized_steel_frame x125
                            .where('D', blocks(WFBlocks.GALVANIZED_STEEL_CASING.get())) // gtceu:atomic_casing x122
                            .where('E', blocks(WFBlocks.REINFORCED_STAINLESS_CASING.get())) // wfcore:reinforced_stainless_casing x159
                            .where('F', blocks(GTBlocks.FIREBOX_STEEL.get())) // gtceu:steel_firebox_casing x64
                            .where('G', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x28
                            .where(' ', any())
                            .build();
                })
                // Reinforced Stainless casing hull with the assembler front, matching the silo's E-casing.
                .workableCasingModel(WFCore.id("block/casings/reinforced_stainless_casing"),
                        GTCEu.id("block/machines/assembler"))
                .register();

        INTERCEPTOR = WF_MACHINES.multiblock("interceptor", InterceptorMachine::new,
                MetaMachineBlock::new, MetaMachineItem::new, InterceptorBlockEntity::new)
                .langValue("Interceptor Battery")
                // Auto-added from the "...machine.interceptor.tooltip" lang key (see missile_factory).
                .hasBER(false)
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(GTBlocks.STEEL_HULL)
                .allowFlip(false)
                .pattern(definition -> {
                    var pattern = FactoryBlockPattern.start(
                            RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.RIGHT);
                    for (String[] aisle : InterceptorStructure.AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.CASING_STEEL_SOLID.get()) // gtceu:solid_machine_casing x22
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2, 1))
                                    // Maintenance Hatch (required once maintenance is enabled in the GT config).
                                    .or(autoAbilities(true, false, false)))
                            .where('B', frames(GTMaterials.BlackSteel)) // gtceu:black_steel_frame x4
                            .where('C', blocks(WFBlocks.GALVANIZED_STEEL_CASING.get()))
                            .where('D', frames(WFMaterials.GalvanizedSteel)) // wfcore:galvanized_steel_frame x4
                            .where(' ', any())
                            .build();
                })
                // Same casing + assembler front as the Light Plane Assembler.
                .workableCasingModel(GTCEu.id("block/casings/steam/steel/side"),
                        GTCEu.id("block/machines/assembler"))
                .register();


        GREENHOUSE = WF_MACHINES.multiblock("greenhouse", WorkableElectricMultiblockMachine::new)
                .langValue("Greenhouse")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.GREENHOUSE)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            { " AAA ", " CBC ", " CBC ", " CBC ", " CCC " },
                            { "ABBBA", "C###C", "C###C", "C###C", "C###C" },
                            { "ABBBA", "B###S", "B###B", "B###B", "C###C" },
                            { "ABBBA", "C###C", "C###C", "C###C", "C###C" },
                            { " AAA ", " CBC ", " CBC ", " CBC ", " CCC " },
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.CASING_TEMPERED_GLASS.get())) // roof/floor glass
                            .where('B', blocks(GTBlocks.CASING_TEMPERED_GLASS.get())) // glass walls
                            .where('#', air())
                            .where('C', blocks(GTBlocks.CASING_STEEL_SOLID.get()) // steel frame + hatches
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1))
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1))
                                    .or(abilities(PartAbility.EXPORT_ITEMS).setMinGlobalLimited(1)))
                            .where(' ', any())
                            .build();
                })
                .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                        GTCEu.id("block/machines/assembler"))
                .register();


        MOB_FARMER = WF_MACHINES.multiblock("mob_farmer", WorkableElectricMultiblockMachine::new)
                .langValue("Mob Farmer")
                .rotationState(RotationState.NON_Y_AXIS)
                .recipeType(WFRecipeTypes.MOB_FARMER)
                .recipeModifier(GTRecipeModifiers.OC_NON_PERFECT)
                .appearanceBlock(GTBlocks.MACHINE_CASING_MV)
                .pattern(definition -> {
                    final String[][] AISLES = {
                            { "AAA", "BBB", "CBC", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "B B", "C C", " B " },
                            { "AAA", "BBB", "BBB", "BBB" },
                            { "AAA", "B S", "B B", "BBB" },
                            { "AAA", "BBB", "BBB", "BBB" },
                    };
                    var pattern = FactoryBlockPattern.start(RelativeDirection.FRONT, RelativeDirection.UP,
                            RelativeDirection.RIGHT);
                    for (String[] aisle : AISLES) {
                        pattern.aisle(aisle);
                    }
                    return pattern
                            .where('S', controller(blocks(definition.getBlock())))
                            .where('A', blocks(GTBlocks.MACHINE_CASING_MV.get())) // MV hull
                            .where('B', blocks(GTBlocks.CASING_STEEL_SOLID.get()) // steel casing + hatches
                                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1))
                                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                                            .setMaxGlobalLimited(2))
                                    .or(abilities(PartAbility.IMPORT_ITEMS).setMinGlobalLimited(1))
                                    .or(abilities(PartAbility.EXPORT_ITEMS).setMinGlobalLimited(1)))
                            .where('C', frames(GTMaterials.Steel)) // cage bars
                            .where(' ', any())
                            .build();
                })
                .workableCasingModel(GTCEu.id("block/casings/voltage/mv/side"),
                        GTCEu.id("block/machines/assembler"))
                .register();

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
