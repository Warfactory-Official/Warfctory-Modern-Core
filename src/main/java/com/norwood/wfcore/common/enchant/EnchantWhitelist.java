package com.norwood.wfcore.common.enchant;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.HashMap;
import java.util.Map;

/**
 * A level-sensitive enchantment whitelist. While at least one enchant is whitelisted the list is authoritative:
 * any enchant not on it is disabled (effective level 0), and whitelisted ones are capped at their declared max
 * level. Declared from Java or KubeJS ({@code WFEnchant}) and enforced by {@link EnchantWhitelistHandler}.
 *
 * <p>1.21 made enchantments a data registry, so they are addressed as {@link Holder}s rather than as
 * registry objects. The whitelist itself still keys on {@link ResourceLocation}, which is what scripts write.
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
        return cap < 0 ? level : Math.min(level, cap);
    }

    /**
     * Holder-aware clamp. A negative cap resolves against the enchantment's own {@code getMaxLevel()}, which is
     * only reachable through the holder now that enchantments live in a datapack registry.
     */
    public static int clamp(Holder<Enchantment> enchantment, int level) {
        if (CAPS.isEmpty()) return level;
        ResourceLocation id = enchantment.unwrapKey().map(ResourceKey::location).orElse(null);
        if (id == null) return level;
        Integer cap = CAPS.get(id);
        if (cap == null) return 0;
        int max = cap < 0 ? enchantment.value().getMaxLevel() : cap;
        return Math.min(level, max);
    }
}
