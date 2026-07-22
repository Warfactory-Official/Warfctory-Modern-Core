package com.norwood.wfcore.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import nl.smartstreamlabs.sbwdroneconfig.CubedFpvDroneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(CubedFpvDroneRenderer.class)
public class CubedFpvDroneRendererMixin {

    // Full descriptor pins the injector to the geckolib render(T, ...) method (the vanilla override is the
    // separate m_7392_) and covers all three getOrCreateTag() call sites within it. The @At target is a vanilla
    // method, so it keeps the default remap = true (getOrCreateTag -> m_41784_ via the refmap).
    @Redirect(
            method = "render(Lnl/smartstreamlabs/sbwdroneconfig/CubedFpvDroneEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/item/ItemStack;getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag wfcore$safeHeldItemTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag : new CompoundTag();
    }
}
