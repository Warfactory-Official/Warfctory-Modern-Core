package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * Allocated placed features run {@code floor(m)} + Bernoulli(frac m) full placement passes per chunk, m sampled at
 * the chunk centre. m = 1 => untouched, no extra RNG draw. Painted / assigned chunks run round(richness) passes
 * instead.
 */
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureAllocationMixin {

    @Shadow
    private boolean placeWithContext(PlacementContext context, RandomSource source, BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void wfcore$allocate(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos pos,
                                 CallbackInfoReturnable<Boolean> cir) {
        PlacedFeature self = (PlacedFeature) (Object) this;
        AllocationIndex index = AllocationIndexManager.get(level.getLevel());
        String decided = index.decidedFeature(self);
        List<AllocationIndex.Pick> picks = decided == null ? null
                : index.picks(ResourceSystem.PLACED_FEATURE, pos.getX() >> 4, pos.getZ() >> 4);
        AllocationIndex.Pick pick = picks == null ? null
                : picks.stream().filter(p -> p.target().equals(decided)).findFirst().orElse(null);
        int passes;
        if (pick != null) {
            passes = (int) Math.round(pick.richness());
        } else {
            AllocationIndex.Target rule = index.feature(self);
            if (rule == null) {
                return;
            }
            double m = rule.at(pos.getX() + 8.0, pos.getZ() + 8.0);
            if (m == 1.0) {
                return;
            }
            passes = (int) m;
            if (random.nextDouble() < m - passes) {
                passes++;
            }
        }
        PlacementContext context = new PlacementContext(level, generator, Optional.of(self));
        boolean placed = false;
        for (int i = 0; i < passes; i++) {
            placed |= placeWithContext(context, random, pos);
        }
        cir.setReturnValue(placed);
    }
}
