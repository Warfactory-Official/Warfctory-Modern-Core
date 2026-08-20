package com.norwood.wfcore.mixin;

import net.minecraft.world.item.ItemStack;

import com.norwood.wfcore.integration.wfweight.WeightGate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Tier-1 logistics enforcement: the GregTech Ender Item Link cover moves items across a wireless,
 * cross-dimensional "ender channel" (an ender-chest-as-a-cover). Its {@code doTransferItems} routes every
 * move through {@code GTTransferUtils.transferItemsFiltered(source, dest, filter, max)} for both IO
 * directions; we AND the filter with "not weighted" so weighted combat items never cross the channel while
 * ordinary items still do.
 */
@Mixin(targets = "com.gregtechceu.gtceu.common.cover.ender.EnderItemLinkCover", remap = false)
public class EnderItemLinkCoverWeightMixin {

    @ModifyArg(
            method = "doTransferItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/utils/GTTransferUtils;transferItemsFiltered(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraftforge/items/IItemHandler;Ljava/util/function/Predicate;I)I"),
            index = 2,
            remap = false)
    private Predicate<ItemStack> wfcore$excludeWeighted(Predicate<ItemStack> original) {
        return original.and(stack -> !WeightGate.isWeighted(stack));
    }
}
