package com.norwood.wfcore.common.worldgen;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.norwood.wfcore.WFCore;

/** Registers WFCore's worldgen features. The {@code wfcore:deposit} feature is referenced by datapack JSON. */
public final class WFFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES,
            WFCore.MOD_ID);

    public static final RegistryObject<DepositFeature> DEPOSIT = FEATURES.register("deposit", DepositFeature::new);

    private WFFeatures() {}

    public static void init(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
