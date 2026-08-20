package com.norwood.wfcore.integration.wfweight;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;

import com.warfactory.ultimateweight.v1211.WeightViews1211;

/** Soft gate on Ultimate Weight: every call is a no-op when wfweight is absent. */
public final class WeightGate {

    private static final String MOD_ID = "wfweight";
    private static Boolean loaded;

    private WeightGate() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    public static boolean isWeighted(ItemStack stack) {
        if (!isLoaded() || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return WeightViews1211.weightOf(stack) > 0.0D;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isOverEncumbered(Player player) {
        if (!isLoaded() || player == null) {
            return false;
        }
        try {
            // 1.21.1 dropped the IPlayerWeightData capability for a view: carry capacity comes off the
            // player view and the current load is summed from the same inventory sources the mod uses.
            return WeightViews1211.totalWeight(player) > WeightViews1211.player(player).carryCapacityKg();
        } catch (Throwable t) {
            return false;
        }
    }
}
