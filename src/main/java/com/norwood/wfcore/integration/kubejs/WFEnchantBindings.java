package com.norwood.wfcore.integration.kubejs;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.common.enchant.EnchantWhitelist;

/**
 * KubeJS binding exposed as {@code WFEnchant} in startup scripts. A level-sensitive enchantment whitelist: once
 * you allow any enchant, only allowed enchants work (everything else is disabled), each capped at the level you
 * give. Enforcement is global — enchanting table, anvil, loot, trades and {@code /give} all respect it.
 *
 * <pre>{@code
 * WFEnchant.allow('minecraft:protection', 2)   // protection works, capped at level 2
 * WFEnchant.allow('minecraft:unbreaking')      // allowed at its natural max level
 * WFEnchant.allow('minecraft:efficiency', 5)
 * // mending is simply never allowed -> disabled everywhere
 * }</pre>
 */
public class WFEnchantBindings {

    /** Whitelists {@code id} at its natural max level. */
    public void allow(String id) {
        EnchantWhitelist.allow(new ResourceLocation(id), -1);
    }

    /** Whitelists {@code id}, capping its effective level at {@code maxLevel}. */
    public void allow(String id, int maxLevel) {
        EnchantWhitelist.allow(new ResourceLocation(id), Math.max(1, maxLevel));
    }

    /** Drops {@code id} from the whitelist (it becomes disabled again while the whitelist is active). */
    public void remove(String id) {
        EnchantWhitelist.remove(new ResourceLocation(id));
    }
}
