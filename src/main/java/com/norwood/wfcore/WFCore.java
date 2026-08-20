package com.norwood.wfcore;

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
import net.neoforged.neoforge.registries.RegisterEvent;

import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.CodecTransformer;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformers;

import com.norwood.wfcore.common.ballistics.BallisticsEvents;
import com.norwood.wfcore.common.block.WFBlockResistanceTooltip;
import com.norwood.wfcore.common.block.WFBlockResistances;
import com.norwood.wfcore.common.chat.ChatModeration;
import com.norwood.wfcore.common.compute.CPURegistry;
import com.norwood.wfcore.common.compute.RAMRegistry;
import com.norwood.wfcore.common.compute.WFComputeScripts;
import com.norwood.wfcore.common.compute.WFComputeTooltips;
import com.norwood.wfcore.common.darkness.DarknessServerHandler;
import com.norwood.wfcore.common.data.WFContent;
import com.norwood.wfcore.common.data.WFDataComponents;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.data.WFMaterials;
import com.norwood.wfcore.common.data.WFRecipeTypes;
import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.common.fluid.CoolantRegistry;
import com.norwood.wfcore.common.loot.WFLootEvents;
import com.norwood.wfcore.common.maintenance.MaintenanceService;
import com.norwood.wfcore.common.particle.WFParticles;
import com.norwood.wfcore.common.recipe.WFRecipeTypeTweaks;
import com.norwood.wfcore.common.recipe.condition.WFRecipeConditions;
import com.norwood.wfcore.common.shader.ShaderServerHandler;
import com.norwood.wfcore.common.sound.WFSounds;
import com.norwood.wfcore.common.tool.BoltGunConversions;
import com.norwood.wfcore.common.worldgen.DepositRetrofitHandler;
import com.norwood.wfcore.common.worldgen.WFFeatures;
import com.norwood.wfcore.common.worldgen.road.RoadCommands;
import com.norwood.wfcore.common.worldgen.road.RoadIndexManager;
import com.norwood.wfcore.common.worldgen.road.RoadRegistries;
import com.norwood.wfcore.common.worldgen.road.WorldgenPackProvider;
import com.norwood.wfcore.common.worldgen.transplant.VeinBackfillCommand;
import com.norwood.wfcore.common.worldgen.transplant.VeinBackfillRunner;
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
     * GT 8.0.0 removed {@code GTRegistrate#registerRegistrate}: {@code create(modId)} looks up this mod's
     * event bus and registers its own listeners, so there is nothing left to call afterwards.
     */
    public static GTRegistrate WF_MACHINES = GTRegistrate.create(WFCore.MOD_ID);

    /** RegisterEvent fires once per registry; our GT content all registers on the first one. */
    private boolean registeredGTContent;

    public WFCore(IEventBus modEventBus, ModContainer container) {
        // NeoForge-native TOML config. Registration + baking replace the old snakeyaml wfcore.yaml/radar.yaml.
        // ModLoadingContext is gone; the container is injected into the constructor instead.
        container.registerConfig(ModConfig.Type.COMMON, WFCoreConfig.SPEC, "wfcore.toml");
        container.registerConfig(ModConfig.Type.COMMON, RadarConfig.SPEC, "wfcore-radar.toml");
        modEventBus.addListener(this::onModConfig);

        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(RoadRegistries::register);
        modEventBus.addListener(WorldgenPackProvider::addPackFinders);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::loadComplete);
        modEventBus.addListener(this::onGatherData);
        modEventBus.addListener(this::modifyMaterials);

        // GregTech 8.0.0's sync system resolves a field's transformer the first time it sees the type, so
        // wfcore's own @SaveField element types have to be registered before any machine is constructed.
        ValueTransformers.registerTransformer(ResearchUnitMachine.Job.class,
                new CodecTransformer<>(ResearchUnitMachine.Job.CODEC));

        WFFeatures.init(modEventBus);
        WFParticles.PARTICLE_TYPES.register(modEventBus);
        WFDataComponents.DATA_COMPONENTS.register(modEventBus);
        WFSounds.SOUNDS.register(modEventBus);

        // Everything below is on the game bus. Note there is no `register(this)`: WFCore's own listeners
        // are all lambdas on the MOD bus above, so it has no @SubscribeEvent methods — and NeoForge 21.1
        // throws IllegalArgumentException on registering a listener object with none, where Forge quietly
        // accepted it.
        NeoForge.EVENT_BUS.register(new com.norwood.wfcore.radar.RadarRegistryHandler());
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.radar.Retrofitter.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.radar.RadarCommands.INSTANCE);
        NeoForge.EVENT_BUS.register(com.norwood.wfcore.common.deposit.DepositCommands.INSTANCE);
        NeoForge.EVENT_BUS.register(VeinBackfillCommand.INSTANCE);
        NeoForge.EVENT_BUS.register(VeinBackfillRunner.INSTANCE);
        NeoForge.EVENT_BUS.register(DepositRetrofitHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(RoadIndexManager.INSTANCE);
        NeoForge.EVENT_BUS.register(RoadCommands.INSTANCE);
        NeoForge.EVENT_BUS.register(new WFComputeTooltips());
        NeoForge.EVENT_BUS.register(new WFBlockResistanceTooltip());
        NeoForge.EVENT_BUS.register(new WFLootEvents());
        NeoForge.EVENT_BUS.register(new BallisticsEvents());
        NeoForge.EVENT_BUS.register(DarknessServerHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(ShaderServerHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(ChatModeration.INSTANCE);
        NeoForge.EVENT_BUS.register(MaintenanceService.INSTANCE);

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

    /**
     * GT 8.0.0 turned materials, recipe types and machines into real NeoForge registries, so the old
     * per-content {@code modEventBus.addGenericListener(GTRecipeType.class, ...)} hooks and
     * {@code MaterialRegistryEvent} / {@code MaterialEvent} are gone. Everything now registers from one
     * {@link RegisterEvent} listener, exactly as GregTech's own {@code CommonProxy#onRegister} does —
     * {@code GTRegistries.register} buffers entries until the matching registry is unfrozen.
     *
     * <p>Materials no longer live in a per-addon registry either; they go into the single
     * {@code GTRegistries.MATERIALS} under this mod's namespace, which {@link WFCore#id} already supplies.
     *
     * <p>Ordering: {@code neoforge.mods.toml} declares {@code ordering = "AFTER"} on gtceu, so GregTech's
     * own listener has already run by the time this one does and every GT material/block it references exists.
     */
    private void onRegister(RegisterEvent event) {
        if (registeredGTContent) {
            return;
        }
        registeredGTContent = true;

        WFMaterials.init();
        // GregTech registers its own recipe conditions from RegisterEvent too (CommonProxy#onRegister);
        // registering later would leave them buffered in GTRegistries and never flushed.
        WFRecipeConditions.init();
        VehicleFactoryRecipes.init();
        WFRecipeTypes.init();
        WFMachines.init();
    }


    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            WFDeposits.registerDefaults();
            LOGGER.info("Deposits: {} type(s), {} node(s), {} region(s); worldgen enabled={} scatter={} rarity={} heal={}",
                    WFDeposits.all().size(), WFDeposits.nodeCount(), WFDeposits.regionCount(),
                    WFCoreConfig.isDepositWorldgenEnabled(),
                    WFCoreConfig.isDepositScatterEnabled(),
                    WFCoreConfig.getDepositWorldgenRarity(),
                    WFCoreConfig.isDepositHealEnabled());
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
            LOGGER.info("Bolt gun conversions: {} entry(ies)", BoltGunConversions.size());
            WFRecipeTypeTweaks.init();
            com.norwood.wfcore.integration.tacz.TaczBallisticsIntegration.register();
            com.norwood.wfcore.integration.wfweight.TaczWeightIntegration.register();
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

    /**
     * (Optional) Used to modify pre-existing materials from GregTech. This is the one material event that
     * survived 8.0.0 — it fires after every material is registered and before the registry freezes.
     */
    private void modifyMaterials(PostMaterialEvent event) {
        // CustomMaterials.modify();
    }
}
