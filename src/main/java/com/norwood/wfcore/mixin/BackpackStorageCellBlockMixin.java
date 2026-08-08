package com.norwood.wfcore.mixin;

import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.items.ISpatialStorageCell;
import appeng.api.storage.StorageCells;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackInventoryHandler", remap = false)
public class BackpackStorageCellBlockMixin {

    @Inject(method = "isAllowed", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$blockStorageCells(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) {
            return;
        }
        if (StorageCells.isCellHandled(stack) || stack.getItem() instanceof ISpatialStorageCell) {
            cir.setReturnValue(false);
        }
    }
}
