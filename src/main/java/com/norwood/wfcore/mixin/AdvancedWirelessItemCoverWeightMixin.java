package com.norwood.wfcore.mixin;

import net.minecraft.world.item.ItemStack;

import net.minecraftforge.items.IItemHandler;

import com.norwood.wfcore.integration.wfweight.WeightGate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(targets = "com.hepdd.gtmthings.common.cover.AdvancedWirelessTransferCover", remap = false)
public class AdvancedWirelessItemCoverWeightMixin {

    @Redirect(
            method = "moveInventoryItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/items/IItemHandler;extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;"),
            remap = false)
    private ItemStack wfcore$skipWeightedExtract(IItemHandler handler, int slot, int amount, boolean simulate) {
        ItemStack peek = handler.extractItem(slot, amount, true);
        if (WeightGate.isWeighted(peek)) {
            return ItemStack.EMPTY;
        }
        return handler.extractItem(slot, amount, simulate);
    }
}
