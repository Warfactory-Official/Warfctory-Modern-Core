package com.norwood.wfcore.mixin;

import net.minecraft.world.item.ItemStack;

import com.norwood.wfcore.integration.wfweight.WeightGate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "com.gregtechceu.gtceu.common.pipelike.item.longdistance.LDItemEndpointMachine$ItemHandlerWrapper", remap = false)
public class LongDistanceItemPipeWeightMixin {

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$blockWeightedInsert(
            int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (WeightGate.isWeighted(stack)) {
            cir.setReturnValue(stack);
        }
    }
}
