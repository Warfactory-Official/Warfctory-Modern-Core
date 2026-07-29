package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.atsuishio.superbwarfare.tools.VectorTool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(VectorTool.class)
public abstract class VectorToolChunkSafeClipMixin {

    @Redirect(method = "checkNoClip",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private static BlockHitResult wfcore$chunkSafeClip(Level level, ClipContext context) {
        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context,
                (ctx, pos) -> {
                    // Never force-load a chunk from the server thread: an unloaded position is
                    // treated as air so the ray keeps going instead of calling getChunkBlocking.
                    if (!level.hasChunkAt(pos)) {
                        return null;
                    }
                    BlockState blockState = level.getBlockState(pos);
                    FluidState fluidState = level.getFluidState(pos);
                    Vec3 from = ctx.getFrom();
                    Vec3 to = ctx.getTo();
                    VoxelShape blockShape = ctx.getBlockShape(blockState, level, pos);
                    BlockHitResult blockHit = level.clipWithInteractionOverride(from, to, pos, blockShape, blockState);
                    VoxelShape fluidShape = ctx.getFluidShape(fluidState, level, pos);
                    BlockHitResult fluidHit = fluidShape.clip(from, to, pos);
                    double blockDist = blockHit == null ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
                    double fluidDist = fluidHit == null ? Double.MAX_VALUE : from.distanceToSqr(fluidHit.getLocation());
                    return blockDist <= fluidDist ? blockHit : fluidHit;
                },
                (ctx) -> {
                    Vec3 diff = ctx.getFrom().subtract(ctx.getTo());
                    return BlockHitResult.miss(ctx.getTo(), Direction.getNearest(diff.x, diff.y, diff.z), BlockPos.containing(ctx.getTo()));
                });
    }
}
