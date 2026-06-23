package com.norwood.wfcore.mixin;

import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla chunk-mesh path of the render mask: skip masked structure blocks so the machine's GLTF model
 * replaces them. Only the leading {@code (state, pos)} arguments are captured so this stays correct
 * regardless of the extra Forge parameters on {@code renderBatched}. (Sodium/Embeddium replace this
 * path entirely — see {@code EmbeddiumBlockRendererMixin}.)
 */
@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {

    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true)
    private void wfcore$skipMaskedBlock(BlockState state, BlockPos pos, CallbackInfo ci) {
        if (RenderMaskManager.isModelDisabledRaw(pos)) {
            ci.cancel();
        }
    }
}
