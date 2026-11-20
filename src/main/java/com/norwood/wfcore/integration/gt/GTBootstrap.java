package com.norwood.wfcore.integration.gt;

import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformers;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.compute.CPURegistry;
import com.norwood.wfcore.common.compute.RAMRegistry;
import com.norwood.wfcore.common.compute.WFComputeScripts;
import com.norwood.wfcore.common.compute.WFComputeTooltips;
import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.data.WFCovers;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.data.WFMaterials;
import com.norwood.wfcore.common.data.WFRecipeTypes;
import com.norwood.wfcore.common.fluid.CoolantRegistry;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;
import com.norwood.wfcore.common.recipe.WFRecipeTypeTweaks;
import com.norwood.wfcore.common.recipe.condition.WFRecipeConditions;
import com.norwood.wfcore.common.worldgen.resource.ResourceCommands;
import com.norwood.wfcore.common.worldgen.transplant.VeinBackfillCommand;
import com.norwood.wfcore.common.worldgen.transplant.VeinBackfillRunner;
import com.norwood.wfcore.common.worldgen.transplant.VeinTransplant;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Everything of wfcore that needs GregTech, in one place so it can be left out. {@link WFCore} calls in here only when
 * {@link GTIntegration#isLoaded()}; this class and everything it names are never loaded otherwise.
 */
public final class GTBootstrap {
    /** The mod's registrate, as GregTech's: the same instance as {@link WFCore#REGISTRATE}. Machines, materials, covers and pipes register through it. */
    public static final GTRegistrate WF_MACHINES = (GTRegistrate) WFCore.REGISTRATE;

    private GTBootstrap() {
    }

    /** Mod construction, before the shared registries: listeners and sync-system types. */
    public static void construct(IEventBus modEventBus) {
        modEventBus.addListener(GTBootstrap::modifyMaterials);
        modEventBus.addListener(com.norwood.wfcore.common.worldgen.road.RoadRegistries::register);
        modEventBus.addListener(com.norwood.wfcore.common.worldgen.resource.ResourceAllocation::register);
        modEventBus.addListener(com.norwood.wfcore.common.worldgen.road.WorldgenPackProvider::addPackFinders);
        com.norwood.wfcore.common.worldgen.WFFeatures.init(modEventBus);
        subscribers(modEventBus);
        // gas masks and suits GregTech rates as full protection keep WFLib's mist out (reads GT's hazard protection)
        com.norwood.wfcore.integration.wflib.GasProtection.install();
        // GregTech 8.0.0's sync system resolves a field's transformer the first time it sees the type, so
        // wfcore's own @SaveField element types have to be registered before any machine is constructed.
        ValueTransformers.registerCodecTransformer(ResearchUnitMachine.Job.class, ResearchUnitMachine.Job.CODEC,
                ByteBufCodecs.fromCodecWithRegistries(ResearchUnitMachine.Job.CODEC));
    }

    /** Static @SubscribeEvent handlers that need GregTech (what @EventBusSubscriber would have registered, were it safe to load them without it). */
    public static void subscribers(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.item.behavior.PipeNetWalkerBehavior.class);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.worldgen.rock.RockCommands.class);
        modEventBus.register(com.norwood.wfcore.common.capability.WFCapabilities.class);
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(com.norwood.wfcore.client.render.DrillParticleHandler.class);
            NeoForge.EVENT_BUS.register(com.norwood.wfcore.client.debug.ModelTransformDebugHandler.class);
            NeoForge.EVENT_BUS.register(com.norwood.wfcore.client.render.VehicleFactoryOverlayRenderer.class);
            NeoForge.EVENT_BUS.register(com.norwood.wfcore.client.render.MissileLaunchTrail.class);
            NeoForge.EVENT_BUS.register(com.norwood.wfcore.client.render.DrillScanDebugRenderer.class);
        }
    }

    /** Game-bus handlers that read GregTech's data. */
    public static void gameBus() {
        NeoForge.EVENT_BUS.register(new com.norwood.wfcore.radar.RadarRegistryHandler());
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.radar.Retrofitter.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.deposit.DepositCommands.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.worldgen.DepositRetrofitHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.worldgen.road.RoadIndexManager.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.worldgen.road.RoadCommands.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.worldgen.DensityDumpCommands.INSTANCE);
        if (com.norwood.wfcore.integration.warforge.WarforgeIntegration.isLoaded()) {
            com.norwood.wfcore.integration.warforge.WarforgeVeinAllocation.install();
        }

        NeoForge.EVENT_BUS.register(com.norwood.wfcore.radar.RadarCommands.INSTANCE);
        NeoForge.EVENT_BUS.register(VeinBackfillCommand.INSTANCE);
        NeoForge.EVENT_BUS.register(VeinBackfillRunner.INSTANCE);
        NeoForge.EVENT_BUS.register(ResourceCommands.INSTANCE);
        NeoForge.EVENT_BUS.register(new WFComputeTooltips());
    }

    /** GT content declared at construction (ordering AFTER gtceu), like GT's own CommonProxy#init. */
    public static void content() {
        WFMaterials.init();
        com.norwood.wfcore.common.data.WFOres.init();
        WFRecipeConditions.init();
        VehicleFactoryRecipes.init();
        WFRecipeTypes.init();
        WFCovers.init();
        VeinTransplant.registerLayer();
        WFMachines.init();
    }

    /** Common setup: the registries that name GregTech items and materials, and the scripts that edit them. */
    public static void commonSetup() {
        com.norwood.wfcore.common.deposit.WFDeposits.registerDefaults();
        WFCore.LOGGER.info("Deposits: {} type(s), {} node(s), {} region(s); worldgen enabled={} scatter={} rarity={} heal={}",
                com.norwood.wfcore.common.deposit.WFDeposits.all().size(), com.norwood.wfcore.common.deposit.WFDeposits.nodeCount(), com.norwood.wfcore.common.deposit.WFDeposits.regionCount(),
                com.norwood.wfcore.config.WFCoreConfig.isDepositWorldgenEnabled(), com.norwood.wfcore.config.WFCoreConfig.isDepositScatterEnabled(),
                com.norwood.wfcore.config.WFCoreConfig.getDepositWorldgenRarity(), com.norwood.wfcore.config.WFCoreConfig.isDepositHealEnabled());
        com.norwood.wfcore.integration.wflib.AnomalySiteDensity.register();
        CoolantRegistry.register();
        CPURegistry.register();
        RAMRegistry.register();
        // Apply KubeJS compute overrides last so packs can add/override/remove any built-in above,
        // plus retune the global WFComputeConfig tunables. (Startup scripts ran before this point.)
        WFComputeScripts.apply();
        WFCore.LOGGER.info("Compute registries: {} CPU item(s), {} RAM item(s)", CPURegistry.size(), RAMRegistry.size());
        WFRecipeTypeTweaks.init();
    }

    /**
     * (Optional) Used to modify pre-existing materials from GregTech. This is the one material event that
     * survived 8.0.0 — it fires after every material is registered and before the registry freezes.
     */
    private static void modifyMaterials(PostMaterialEvent event) {
        // CustomMaterials.modify();
    }
}
