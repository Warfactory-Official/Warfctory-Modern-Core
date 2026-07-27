package com.norwood.wfcore.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

 // Disables all potion brewing pack-wide.

@Mixin(BrewingStandBlockEntity.class)
public class NoBrewingMixin {

    @Inject(method = "isBrewable", at = @At("HEAD"), cancellable = true)
    private static void wfcore$disableBrewing(NonNullList<ItemStack> items, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
