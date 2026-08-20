package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Neighbour half of the vanilla render mask: a block bordering a masked (hidden) block must draw the
 * face pointing at it, otherwise the model would be seen through a hole left where the masked block was
 * culled. Forces {@link Block#shouldRenderFace} to return true when the neighbour is masked.
 */
@Mixin(Block.class)
public class BlockShouldRenderFaceMixin {

    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;" +
            "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;" +
            "Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private static void wfcore$showFaceTowardMasked(BlockState state, BlockGetter level, BlockPos pos,
                                                    Direction direction, BlockPos neighborPos,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (RenderMaskManager.isModelDisabledRaw(neighborPos)) {
            cir.setReturnValue(true);
        }
    }
}
