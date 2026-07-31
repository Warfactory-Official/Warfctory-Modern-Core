package com.norwood.wfcore.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import nl.smartstreamlabs.sbwdroneconfig.LucasDroneRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


// Sister fix to CubedFpvDroneRendererMixin: LucasDroneRenderer.render(...) calls
// player.getMainHandItem().getOrCreateTag().getString("LinkedDrone") (and further getOrCreateTag() sites)
// with no guard. When the main hand is empty the held stack is ItemStack.EMPTY, whose getOrCreateTag()
// returns null (it won't mutate the shared singleton), so the following CompoundTag call NPEs and crashes
// the render thread. Redirect every getOrCreateTag() in render(...) to a null-safe read.
@Mixin(LucasDroneRenderer.class)
public class LucasDroneRendererMixin {

    // Full descriptor pins the injector to the geckolib render(T, ...) method (the vanilla override is the
    // separate m_7392_) and covers all getOrCreateTag() call sites within it. The @At target is a vanilla
    // method, so it keeps the default remap = true (getOrCreateTag -> m_41784_ via the refmap).
    @Redirect(
            method = "render(Lnl/smartstreamlabs/sbwdroneconfig/LucasDroneEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/item/ItemStack;getOrCreateTag()Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag wfcore$safeHeldItemTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag : new CompoundTag();
    }
}
