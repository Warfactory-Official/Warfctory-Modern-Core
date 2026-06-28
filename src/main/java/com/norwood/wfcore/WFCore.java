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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.gui.VehicleUIFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(com.norwood.wfcore.common.capability.WFCapabilities::register);

        modEventBus.addListener(this::addMaterialRegistries);
        modEventBus.addListener(this::addMaterials);
        modEventBus.addListener(this::modifyMaterials);

        modEventBus.addGenericListener(GTRecipeType.class, this::registerRecipeTypes);
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);
        modEventBus.addGenericListener(SoundEntry.class, this::registerSounds);

        com.norwood.wfcore.common.worldgen.WFFeatures.init(modEventBus);
        com.norwood.wfcore.common.particle.WFParticles.PARTICLE_TYPES.register(modEventBus);
        com.norwood.wfcore.common.sound.WFSounds.SOUNDS.register(modEventBus);

        // Most other events are fired on Forge's bus.
        // If we want to use annotations to register event listeners,
        // we need to register our object like this!
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.radar.RadarRegistryHandler());
        MinecraftForge.EVENT_BUS.register(com.norwood.wfcore.radar.Retrofitter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.common.compute.WFComputeTooltips());
        MinecraftForge.EVENT_BUS.register(new com.norwood.wfcore.common.loot.WFLootEvents());

        WF_MACHINES.registerRegistrate();

        com.norwood.wfcore.common.data.WFContent.init();
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

    private void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, x -> {
            x.register(new ResourceLocation(MOD_ID, "superb_fluid_stack"), FLUID_STACK_ENTITY_DATA_SERIALIZER);
        });
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            WFCoreConfig.load();
            com.norwood.wfcore.common.deposit.WFDeposits.registerDefaults();
            com.norwood.wfcore.radar.RadarConfig.load();
            com.norwood.wfcore.common.fluid.CoolantRegistry.register();
            com.norwood.wfcore.common.compute.CPURegistry.register();
            com.norwood.wfcore.common.compute.RAMRegistry.register();
            LOGGER.info("Compute registries: {} CPU item(s), {} RAM item(s)",
                    com.norwood.wfcore.common.compute.CPURegistry.size(),
                    com.norwood.wfcore.common.compute.RAMRegistry.size());
            // com.norwood.wfcore.common.research.WFResearches.registerTest();
            com.norwood.wfcore.common.recipe.condition.WFRecipeConditions.init();
            UIFactory.register(VehicleUIFactory.INSTANCE);
            LOGGER.info("Hello from common setup! This is *after* registries are done, so we can do this:");
            LOGGER.info("Look, I found a {}!", Items.DIAMOND);
        });
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
        com.norwood.wfcore.common.data.WFMaterials.init();
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
