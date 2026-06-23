package com.norwood.wfcore.mixin;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla chunk-mesh path of the render mask: skip masked structure blocks so the machine's GLTF model
 * replaces them. Targets the 9-arg Forge {@code renderBatched} overload (the one the chunk rebuild task
 * actually calls); it is Forge-added so its name is stable in prod, hence {@code remap = false}.
 * (Sodium/Embeddium replace this path entirely — see {@code EmbeddiumBlockRendererMixin}.)
 */
@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {

    @Inject(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;" +
            "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;" +
            "Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Z" +
            "Lnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;" +
            "Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void wfcore$skipMaskedBlock(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                        PoseStack poseStack, VertexConsumer consumer, boolean checkSides,
                                        RandomSource random, ModelData modelData, RenderType renderType,
                                        CallbackInfo ci) {
        if (RenderMaskManager.isModelDisabledRaw(pos)) {
            ci.cancel();
        }
    }
}
