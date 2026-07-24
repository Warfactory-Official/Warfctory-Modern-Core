package com.norwood.wfcore.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import com.flansmod.warforge.client.util.FullColorNameplate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.wfcore.client.NamePlateVisibility;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(FullColorNameplate.class)
public class WarforgeNamePlateMixin {

    @Redirect(
            method = "drawNameplate",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/Font$DisplayMode;SEE_THROUGH:Lnet/minecraft/client/gui/Font$DisplayMode;",
                    opcode = Opcodes.GETSTATIC))
    private static Font.DisplayMode wfcore$factionNameTagDisplayMode(
            Font font, Component name, Entity entity, PoseStack pose, MultiBufferSource buffers,
            int verticalShift, boolean isSneaking, int color, int darker, int packedLight) {
        return NamePlateVisibility.displayMode(entity);
    }
}
