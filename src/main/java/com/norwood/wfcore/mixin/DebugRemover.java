package com.norwood.wfcore.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.wfcore.WFCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class DebugRemover {

    @Inject(method = "shouldRenderHitBoxes", at = @At("HEAD"), cancellable = true)
    private void wfcore$shouldRenderHitBoxes(CallbackInfoReturnable<Boolean> cir) {
        if (!WFCore.DEBUG)
            cir.setReturnValue(false);
    }

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void wfcore$renderHitbox(PoseStack poseStack, VertexConsumer consumer, Entity entity,
                                            float partialTicks, CallbackInfo ci) {
        if (!WFCore.DEBUG)
            ci.cancel();
    }
}
