package com.norwood.wfcore.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.norwood.wfcore.common.enchant.EnchantWhitelist;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filters {@link EnchantmentHelper#getEnchantments(ItemStack)} through the {@link EnchantWhitelist}. This covers
 * the enchanted-book path (anvil transfers) and any consumer that iterates an item's whole enchant map.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @Inject(method = "getEnchantments", at = @At("RETURN"), cancellable = true)
    private static void wfcore$filterEnchantments(ItemStack stack,
                                                  CallbackInfoReturnable<Map<Enchantment, Integer>> cir) {
        if (!EnchantWhitelist.active()) return;
        Map<Enchantment, Integer> map = cir.getReturnValue();
        if (map == null || map.isEmpty()) return;

        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> e : map.entrySet()) {
            int clamped = EnchantWhitelist.clamp(e.getKey(), e.getValue());
            if (clamped <= 0) {
                changed = true;
            } else {
                out.put(e.getKey(), clamped);
                if (clamped != e.getValue()) changed = true;
            }
        }
        if (changed) cir.setReturnValue(out);
    }
}
