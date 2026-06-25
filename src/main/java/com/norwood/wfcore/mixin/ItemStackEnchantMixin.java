package com.norwood.wfcore.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import com.norwood.wfcore.common.enchant.EnchantWhitelist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the {@link EnchantWhitelist} to {@code getEnchantmentTags()} — the list every effect, tooltip and anvil
 * read funnels through. The write path ({@code ItemStack#enchant}) uses the raw tag, so enchanting still works.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEnchantMixin {

    @Inject(method = "getEnchantmentTags", at = @At("RETURN"), cancellable = true)
    private void wfcore$filterEnchantments(CallbackInfoReturnable<ListTag> cir) {
        if (!EnchantWhitelist.active()) return;
        ListTag original = cir.getReturnValue();
        if (original == null || original.isEmpty()) return;

        ListTag filtered = new ListTag();
        boolean changed = false;
        for (int i = 0; i < original.size(); i++) {
            CompoundTag entry = original.getCompound(i);
            ResourceLocation id = EnchantmentHelper.getEnchantmentId(entry);
            int level = EnchantmentHelper.getEnchantmentLevel(entry);
            int clamped = id == null ? level : EnchantWhitelist.clamp(id, level);
            if (clamped <= 0) {
                changed = true;
            } else if (clamped != level) {
                filtered.add(EnchantmentHelper.storeEnchantment(id, clamped));
                changed = true;
            } else {
                filtered.add(entry);
            }
        }
        if (changed) cir.setReturnValue(filtered);
    }
}
