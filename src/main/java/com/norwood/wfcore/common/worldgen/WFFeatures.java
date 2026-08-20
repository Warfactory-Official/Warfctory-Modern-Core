package com.norwood.wfcore.common.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.worldgen.road.RoadFeature;

/** Registers WFCore's worldgen features. The {@code wfcore:deposit} feature is referenced by datapack JSON. */
public final class WFFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE,
            WFCore.MOD_ID);

    public static final DeferredHolder<Feature<?>, DepositFeature> DEPOSIT = FEATURES.register("deposit",
            DepositFeature::new);

    public static final DeferredHolder<Feature<?>, RoadFeature> ROAD = FEATURES.register("road",
            RoadFeature::new);

    private WFFeatures() {}

    public static void init(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
