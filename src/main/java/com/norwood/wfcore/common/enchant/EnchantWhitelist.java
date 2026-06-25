package com.norwood.wfcore.common.enchant;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * A level-sensitive enchantment whitelist. While at least one enchant is whitelisted the list is authoritative:
 * any enchant not on it is disabled (effective level 0), and whitelisted ones are capped at their declared max
 * level. Declared from Java or KubeJS ({@code WFEnchant}) and enforced by the enchant mixins on every read site.
 */
public final class EnchantWhitelist {

    // id -> max level; a negative cap means "the enchant's own natural max".
    private static final Map<ResourceLocation, Integer> CAPS = new HashMap<>();

    private EnchantWhitelist() {}

    public static void allow(ResourceLocation id, int maxLevel) {
        CAPS.put(id, maxLevel);
    }

    public static void remove(ResourceLocation id) {
        CAPS.remove(id);
    }

    /** True once the whitelist has any entry; while empty the system is inert (vanilla behaviour). */
    public static boolean active() {
        return !CAPS.isEmpty();
    }

    /** Clamps {@code level} for the enchant {@code id}: 0 when not whitelisted, else {@code min(level, cap)}. */
    public static int clamp(ResourceLocation id, int level) {
        if (CAPS.isEmpty()) return level;
        Integer cap = CAPS.get(id);
        if (cap == null) return 0;
        int max = cap;
        if (max < 0) {
            Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(id);
            max = ench != null ? ench.getMaxLevel() : level;
        }
        return Math.min(level, max);
    }

    public static int clamp(Enchantment ench, int level) {
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(ench);
        return id == null ? level : clamp(id, level);
    }
}
