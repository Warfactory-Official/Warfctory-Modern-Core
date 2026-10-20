package com.norwood.wfcore.common.worldgen.density;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.worldgen.rock.RockLayerCondition;
import com.norwood.wfcore.common.worldgen.rock.RockLayers;

/** Worldgen types the wfworld datapack references: density functions, surface conditions, rock layers. */
public final class WFDensityFunctions {

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES = DeferredRegister.create(
            Registries.DENSITY_FUNCTION_TYPE, WFCore.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends SurfaceRules.ConditionSource>> CONDITIONS =
            DeferredRegister.create(Registries.MATERIAL_CONDITION, WFCore.MOD_ID);

    static {
        TYPES.register("continent_skeleton", () -> ContinentSkeleton.DATA_CODEC);
        TYPES.register("distance", () -> Distance.DATA_CODEC);
        CONDITIONS.register("rock_layer", () -> RockLayerCondition.DATA_CODEC);
    }

    private WFDensityFunctions() {}

    public static void init(IEventBus modEventBus) {
        TYPES.register(modEventBus);
        CONDITIONS.register(modEventBus);
        modEventBus.addListener(RockLayers::register);
    }
}
