package com.norwood.wfcore.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.wfcore.client.NamePlateVisibility;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EntityRenderer.class)
public class NamePlateMixin {


    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void wfcore$hideDistantNameTag(
            Entity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            CallbackInfo ci) {
        if (NamePlateVisibility.isHidden(entity)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderNameTag",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/Font$DisplayMode;SEE_THROUGH:Lnet/minecraft/client/gui/Font$DisplayMode;",
                    opcode = Opcodes.GETSTATIC))
    private Font.DisplayMode wfcore$nameTagDisplayMode(
            Entity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        return NamePlateVisibility.displayMode(entity);
    }
}
