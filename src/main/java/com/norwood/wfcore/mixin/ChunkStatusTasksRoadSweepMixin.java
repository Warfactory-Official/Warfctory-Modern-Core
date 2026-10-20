package com.norwood.wfcore.mixin;

import net.minecraft.util.StaticCache2D;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;

import com.norwood.wfcore.common.worldgen.road.RoadSweep;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkStatusTasks.class)
public abstract class ChunkStatusTasksRoadSweepMixin {

    @Inject(method = "light", at = @At("HEAD"))
    private static void wfcore$roadSweep(WorldGenContext context, ChunkStep step,
                                         StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk,
                                         CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        RoadSweep.sweep(context.level(), chunk);
    }
}
