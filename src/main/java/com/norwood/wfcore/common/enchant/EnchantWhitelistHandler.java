package com.norwood.wfcore.common.enchant;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

import com.norwood.wfcore.WFCore;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces the {@link EnchantWhitelist} on every gameplay read of an item's enchantments.
 *
 * <p>On 1.20.1 this took two mixins — {@code EnchantmentHelperMixin} on
 * {@code EnchantmentHelper#getEnchantments} and {@code ItemStackEnchantMixin} on
 * {@code ItemStack#getEnchantmentTags}. Both target methods are gone in 1.21: enchantments moved into the
 * {@code ItemEnchantments} data component and out of NBT. NeoForge funnels every gameplay-facing read —
 * {@code ItemStack#getEnchantmentLevel} and {@code ItemStack#getAllEnchantments} — through
 * {@link GetEnchantmentLevelEvent}, so the whole feature is one listener and both mixins are dropped.
 *
 * <p><b>Behaviour delta:</b> the event explicitly does not fire for raw NBT/component reads, so a
 * filtered-out enchantment still appears in the item's tooltip even though it has no gameplay effect.
 * The old {@code getEnchantmentTags} mixin did hide it. Worth a follow-up if the visual matters.
 */
@EventBusSubscriber(modid = WFCore.MOD_ID)
public final class EnchantWhitelistHandler {

    private EnchantWhitelistHandler() {}

    @SubscribeEvent
    public static void onGetEnchantmentLevel(GetEnchantmentLevelEvent event) {
        if (!EnchantWhitelist.active()) return;

        ItemEnchantments.Mutable enchantments = event.getEnchantments();
        // Collect first: the map is mutated below and its key set is not safe to iterate while doing so.
        List<Holder<Enchantment>> present = new ArrayList<>(enchantments.keySet());
        for (Holder<Enchantment> enchantment : present) {
            if (!event.isTargetting(enchantment)) continue;
            int level = enchantments.getLevel(enchantment);
            int clamped = EnchantWhitelist.clamp(enchantment, level);
            if (clamped != level) {
                enchantments.set(enchantment, Math.max(0, clamped));
            }
        }
    }
}
