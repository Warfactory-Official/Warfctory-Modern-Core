package com.norwood.wfcore.mixin;

import net.minecraft.core.Direction;

import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium/Embeddium chunk-mesh path of the render mask (Embeddium 0.3.31 keeps Sodium's
 * {@code me.jellysquid.mods.sodium} packages, so this one mixin covers Sodium, Embeddium and Rubidium).
 * The block renderer meshes per-block on worker threads, so {@link RenderMaskManager} publishes its
 * masked set as an immutable snapshot that is safe to read here.
 * <p>
 * If no Sodium-family renderer is installed the target class is absent and Mixin silently skips this
 * mixin, leaving the vanilla path ({@code BlockRenderDispatcherMixin}) in charge.
 */
@SuppressWarnings("UnresolvedMixinReference")
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
       remap = false)
public class EmbeddiumBlockRendererMixin {

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void wfcore$skipMaskedBlock(BlockRenderContext ctx, ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (RenderMaskManager.isModelDisabledRaw(ctx.pos())) {
            ci.cancel();
        }
    }

    @Inject(method = "isFaceVisible", at = @At("HEAD"), cancellable = true)
    private void wfcore$showFaceTowardMasked(BlockRenderContext ctx, Direction face,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (RenderMaskManager.isModelDisabledRaw(ctx.pos().relative(face))) {
            cir.setReturnValue(true);
        }
    }
}
