package com.norwood.wfcore.mixin;

import net.minecraft.world.item.ItemStack;

import com.norwood.wfcore.integration.wfweight.WeightGate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;



@Mixin(targets = "com.hepdd.gtmthings.common.cover.WirelessTransferCover", remap = false)
public class WirelessItemCoverWeightMixin {

    @ModifyArg(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/lowdragmc/lowdraglib/side/item/ItemTransferHelper;exportToTarget(Lcom/lowdragmc/lowdraglib/side/item/IItemTransfer;ILjava/util/function/Predicate;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)V"),
            index = 2,
            remap = false)
    private Predicate<ItemStack> wfcore$excludeWeighted(Predicate<ItemStack> original) {
        return original.and(stack -> !WeightGate.isWeighted(stack));
    }
}
