package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import com.norwood.wfcore.common.worldgen.road.RoadSetback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Configured, not placed: selectors nest trees as inner placed features. */
@Mixin(ConfiguredFeature.class)
public abstract class ConfiguredFeatureRoadSetbackMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void wfcore$roadSetback(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (RoadSetback.refuses(level, ((ConfiguredFeature<?, ?>) (Object) this).feature(), pos)) {
            cir.setReturnValue(false);
        }
    }
}
