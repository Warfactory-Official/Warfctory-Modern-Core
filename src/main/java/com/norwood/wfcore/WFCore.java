package com.norwood.wfcore;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;


import com.norwood.wfcore.common.block.WFBlockResistanceTooltip;
import com.norwood.wfcore.common.block.WFBlockResistances;
import com.norwood.wfcore.common.chat.ChatModeration;
import com.norwood.wfcore.common.darkness.DarknessServerHandler;
import com.norwood.wfcore.common.data.WFContent;
import com.norwood.wfcore.common.data.WFDataComponents;
import com.norwood.wfcore.common.loot.WFLootEvents;
import com.norwood.wfcore.common.maintenance.MaintenanceService;
import com.norwood.wfcore.common.particle.WFParticles;
import com.norwood.wfcore.common.shader.ShaderServerHandler;
import com.norwood.wfcore.common.sound.WFSounds;
import com.norwood.wfcore.common.tool.BoltGunConversions;
import com.norwood.wfcore.common.worldgen.WFFeatures;
import com.norwood.wfcore.common.worldgen.density.WFDensityFunctions;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceAllocation;
import com.norwood.wfcore.common.worldgen.road.RoadCommands;
import com.norwood.wfcore.common.worldgen.road.RoadIndexManager;
import com.norwood.wfcore.common.worldgen.road.RoadRegistries;
import com.norwood.wfcore.common.worldgen.road.WorldgenPackProvider;
import com.norwood.wfcore.integration.gt.GTBootstrap;
import com.norwood.wfcore.integration.gt.GTIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.radar.RadarConfig;
import com.norwood.wfcore.radar.WFRadarScripts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod(WFCore.MOD_ID)
public class WFCore {

    public static final String MOD_ID = "wfcore";
    public static final boolean DEBUG = false;
    public static final Logger LOGGER = LogManager.getLogger();
    /**
     * The mod's registrate: GregTech's (a {@code GTRegistrate}) when GregTech is loaded, the plain one when it is not. Content that needs
     * nothing of GregTech registers through this; machines, covers and pipes go through {@code GTBootstrap.WF_MACHINES}, the same instance.
     */
    public static final com.tterrag.registrate.AbstractRegistrate<?> REGISTRATE = WFRegistrate.create(WFCore.MOD_ID);

    public WFCore(IEventBus modEventBus, ModContainer container) {
        com.norwood.wfcore.integration.combinedassault.VehicleGate.install();
        WFSystems.require(MOD_ID, "wflib:nvg");
        WFLibContent.nvg(MOD_ID, "nvg_gen1", "nvg_pvs7", "nvg_pvs14", "nvg_pvs31", "nvg_gpnvg18");
        WFLibContent.armor(WFCore.MOD_ID, "plate_carrier", "steel_plate", "ceramic_plate", "hazmat_suit", "combat_helmet");
        WFSystems.require(MOD_ID, "wflib:armor");
        WFSystems.require(MOD_ID, "wflib:mine");
        WFLibContent.mines(WFCore.MOD_ID, "ap", "bounding", "claymore", "heat", "naval", "scatter_ap", "scatter_at", "dispenser_ap", "dispenser_at");
        WFSystems.require(MOD_ID, "wflib:mist");
        // NeoForge-native TOML config. Registration + baking replace the old snakeyaml wfcore.yaml/radar.yaml.
        // ModLoadingContext is gone; the container is injected into the constructor instead.
        container.registerConfig(ModConfig.Type.COMMON, WFCoreConfig.SPEC, "wfcore.toml");
        container.registerConfig(ModConfig.Type.COMMON, RadarConfig.SPEC, "wfcore-radar.toml");
        modEventBus.addListener(this::onModConfig);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::loadComplete);
        modEventBus.addListener(this::onGatherData);

        if (GTIntegration.isLoaded()) {
            GTBootstrap.construct(modEventBus);
        } else {
            LOGGER.info("GregTech is not installed: wfcore's machines, materials, pipes, covers, research and GT worldgen hooks are off");
        }

        WFDensityFunctions.init(modEventBus);
        WFParticles.PARTICLE_TYPES.register(modEventBus);
        WFDataComponents.DATA_COMPONENTS.register(modEventBus);
        WFSounds.SOUNDS.register(modEventBus);

        // Everything below is on the game bus. Note there is no `register(this)`: WFCore's own listeners
        // are all lambdas on the MOD bus above, so it has no @SubscribeEvent methods — and NeoForge 21.1
        // throws IllegalArgumentException on registering a listener object with none, where Forge quietly
        // accepted it.
        if (GTIntegration.isLoaded()) {
            GTBootstrap.gameBus();
        }
        if (WarforgeIntegration.isLoaded()) {
            com.norwood.wfcore.integration.warforge.WarforgeFobGuard.install();
        }
        NeoForge.EVENT_BUS.register(new WFBlockResistanceTooltip());
        NeoForge.EVENT_BUS.register(new WFLootEvents());
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.block.PlayerUnbreakableHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(DarknessServerHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.camera.ThirdPersonServer.INSTANCE);
        NeoForge.EVENT_BUS.register(ShaderServerHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(ChatModeration.INSTANCE);
        NeoForge.EVENT_BUS.register(MaintenanceService.INSTANCE);

        if (GTIntegration.isLoaded()) {
            GTBootstrap.content();
        }
        WFContent.init();
    }

    /**
     * Create a ResourceLocation in the format "modid:path"
     *
     * @param path
     * @return ResourceLocation with the namespace of your mod
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Re-bakes cached config values whenever FML loads or reloads one of our TOML files. */
    private void onModConfig(final ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() == WFCoreConfig.SPEC) {
            WFCoreConfig.bake();
        } else if (config.getSpec() == RadarConfig.SPEC) {
            RadarConfig.bake();
        }
    }



    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            WFBlockResistances.registerDefaults();
            if (GTIntegration.isLoaded()) {
                GTBootstrap.commonSetup();
            }
            // Flush WFRadar KubeJS ops now that the block registry is frozen (machine enumeration is safe here).
            if (GTIntegration.isLoaded()) {
                WFRadarScripts.apply();
            }
            BoltGunConversions.apply();
            LOGGER.info("Bolt gun conversions: {} entry(ies)", BoltGunConversions.size());
        });
    }

    /** Runs after every mod's blocks are registered and startup scripts (KubeJS included) have executed. */
    private void loadComplete(final FMLLoadCompleteEvent event) {
        event.enqueueWork(WFBlockResistances::apply);
    }

    /**
     * Force a clean JVM shutdown once data generation finishes. This event fires ONLY in the `runData`
     * run, so this listener never executes in the client/server. A dependency loaded into the data run
     * (a config file-watcher / an un-shut-down executor) leaves a non-daemon thread alive, so after
     * {@code DataGenerator#run()} returns the JVM can never reach a natural exit and {@code ./gradlew
     * runData} hangs forever even though every provider succeeded.
     *
     * <p>
     * GatherDataEvent is posted on the same thread that then runs the generator and returns, so joining
     * that thread from a daemon watchdog blocks until all output has been written; we then call
     * {@link System#exit} to terminate past the leaked thread. The exit code mirrors datagen success: an
     * uncaught exception on the generator thread (a failing provider) flips it to a non-zero code so the
     * build still fails loudly instead of being masked green.
     */
    private void onGatherData(final GatherDataEvent event) {
        final Thread datagenThread = Thread.currentThread();
        final AtomicBoolean failed = new AtomicBoolean();
        final Thread.UncaughtExceptionHandler prior = datagenThread.getUncaughtExceptionHandler();
        datagenThread.setUncaughtExceptionHandler((t, e) -> {
            failed.set(true);
            if (prior != null) {
                prior.uncaughtException(t, e);
            }
        });

        Thread watchdog = new Thread(() -> {
            try {
                datagenThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            int code = failed.get() ? 1 : 0;
            LOGGER.info("wfcore: datagen finished (exit {}); forcing JVM shutdown to bypass a leaked "
                    + "non-daemon thread from a dependency.", code);
            System.exit(code);
        }, "wfcore-datagen-exit");
        watchdog.setDaemon(true);
        watchdog.start();
    }

}
