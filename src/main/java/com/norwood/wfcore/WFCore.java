package com.norwood.wfcore;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.sound.SoundEntry;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;

import com.norwood.wfcore.common.block.WFBlockResistances;
import com.norwood.wfcore.common.compute.CPURegistry;
import com.norwood.wfcore.common.compute.RAMRegistry;
import com.norwood.wfcore.common.compute.WFComputeScripts;
import com.norwood.wfcore.common.data.WFContent;
import com.norwood.wfcore.common.data.WFMaterials;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.common.fluid.CoolantRegistry;
import com.norwood.wfcore.common.particle.WFParticles;
import com.norwood.wfcore.common.recipe.condition.WFRecipeConditions;
import com.norwood.wfcore.common.sound.WFSounds;
import com.norwood.wfcore.common.tool.BoltGunConversions;
import com.norwood.wfcore.common.worldgen.WFFeatures;
import com.norwood.wfcore.integration.superbwarfare.SbwBallisticsIntegration;
import com.norwood.wfcore.integration.tacz.TaczBallisticsIntegration;
import com.norwood.wfcore.radar.WFRadarScripts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.gui.VehicleUIFactory;
import com.norwood.wfcore.radar.RadarConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.norwood.wfcore.serializer.WFCoreSerializers.FLUID_STACK_ENTITY_DATA_SERIALIZER;

@Mod(WFCore.MOD_ID)
@SuppressWarnings("removal")
public class WFCore {

    public static final String MOD_ID = "wfcore";
    public static final boolean DEBUG = true;
    public static final Logger LOGGER = LogManager.getLogger();
    public static GTRegistrate WF_MACHINES = GTRegistrate.create(WFCore.MOD_ID);

    public WFCore() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Forge-native TOML config. Registration + baking replace the old snakeyaml wfcore.yaml/radar.yaml.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WFCoreConfig.SPEC, "wfcore.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RadarConfig.SPEC, "wfcore-radar.toml");
        modEventBus.addListener(this::onModConfig);

        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::loadComplete);
        modEventBus.addListener(this::onGatherData);
        modEventBus.addListener(com.norwood.wfcore.common.capability.WFCapabilities::register);

        modEventBus.addListener(this::addMaterialRegistries);
        modEventBus.addListener(this::addMaterials);
        modEventBus.addListener(this::modifyMaterials);

        modEventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modEventBus.addGenericListener(SoundEntry.class, this::registerSounds);

      WFFeatures.init(modEventBus);
       WFParticles.PARTICLE_TYPES.register(modEventBus);
      WFSounds.SOUNDS.register(modEventBus);

        // Most other events are fired on Forge's bus.
        // If we want to use annotations to register event listeners,
        // we need to register our object like this!
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.radar.RadarRegistryHandler());
        MinecraftForge.EVENT_BUS.register(com.norwood.wfcore.radar.Retrofitter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.norwood.wfcore.radar.RadarCommands.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.norwood.wfcore.common.deposit.DepositCommands.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.common.compute.WFComputeTooltips());
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.common.block.WFBlockResistanceTooltip());
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.common.loot.WFLootEvents());
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.common.ballistics.BallisticsEvents());
        MinecraftForge.EVENT_BUS.register(com.norwood.wfcore.integration.superbwarfare.DroneUpgradeBay.INSTANCE);

        WF_MACHINES.registerRegistrate();

      WFContent.init();
    }

    /**
     * Create a ResourceLocation in the format "modid:path"
     *
     * @param path
     * @return ResourceLocation with the namespace of your mod
     */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** Re-bakes cached config values whenever Forge loads or reloads one of our TOML files. */
    private void onModConfig(final ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getSpec() == WFCoreConfig.SPEC) {
            WFCoreConfig.bake();
        } else if (config.getSpec() == RadarConfig.SPEC) {
            RadarConfig.bake();
        }
    }

    private void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, x -> {
            x.register(new ResourceLocation(MOD_ID, "superb_fluid_stack"), FLUID_STACK_ENTITY_DATA_SERIALIZER);
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            WFDeposits.registerDefaults();
            WFBlockResistances.registerDefaults();
            CoolantRegistry.register();
            CPURegistry.register();
            RAMRegistry.register();
            // Apply KubeJS compute overrides last so packs can add/override/remove any built-in above,
            // plus retune the global WFComputeConfig tunables. (Startup scripts ran before this point.)
            WFComputeScripts.apply();
            LOGGER.info("Compute registries: {} CPU item(s), {} RAM item(s)",
                    CPURegistry.size(),
                    RAMRegistry.size());
            // Flush WFRadar KubeJS ops now that the block registry is frozen (machine enumeration is safe here).
            WFRadarScripts.apply();
            BoltGunConversions.apply();
            LOGGER.info("Bolt gun conversions: {} entry(ies)",
                    com.norwood.wfcore.common.tool.BoltGunConversions.size());
            // com.norwood.wfcore.common.research.WFResearches.registerTest();
            WFRecipeConditions.init();
            TaczBallisticsIntegration.register();
            SbwBallisticsIntegration.register();
            com.norwood.wfcore.integration.superbwarfare.DroneUpgradeBay.registerOverrides();
            UIFactory.register(VehicleUIFactory.INSTANCE);
            LOGGER.info("Hello from common setup! This is *after* registries are done, so we can do this:");
            LOGGER.info("Look, I found a {}!", Items.DIAMOND);
        });
    }

    /** Runs after every mod's blocks are registered and startup scripts (KubeJS included) have executed. */
    private void loadComplete(final FMLLoadCompleteEvent event) {
        event.enqueueWork(com.norwood.wfcore.common.block.WFBlockResistances::apply);
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
        final AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean();
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

    /**
     * Create a material manager for your mod using GT's API.
     * You MUST have this if you have custom materials.
     * Remember to register them not to GT's namespace, but your own.
     *
     * @param event
     */
    private void addMaterialRegistries(MaterialRegistryEvent event) {
        GTCEuAPI.materialManager.createRegistry(WFCore.MOD_ID);
    }

    /**
     * You will also need this for registering custom materials
     * Call init() from your Material class(es) here
     *
     * @param event
     */
    private void addMaterials(MaterialEvent event) {
        WFMaterials.init();
    }

    /**
     * (Optional) Used to modify pre-existing materials from GregTech
     *
     * @param event
     */
    private void modifyMaterials(PostMaterialEvent event) {
        // CustomMaterials.modify();
    }

    /**
     * Used to register your own new RecipeTypes.
     * Call init() from your RecipeType class(es) here
     *
     * @param event
     */
    private void registerRecipeTypes(GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType> event) {
        com.norwood.wfcore.common.data.VehicleFactoryRecipes.init();
        com.norwood.wfcore.common.data.WFRecipeTypes.init();
    }

    /**
     * Used to register your own new machines.
     * Call init() from your Machine class(es) here
     *
     * @param event
     */
    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        com.norwood.wfcore.common.data.WFMachines.init();
    }

    /**
     * Used to register your own new sounds
     * Call init from your Sound class(es) here
     *
     * @param event
     */
    public void registerSounds(GTCEuAPI.RegisterEvent<ResourceLocation, SoundEntry> event) {
        // CustomSounds.init();
    }
}
