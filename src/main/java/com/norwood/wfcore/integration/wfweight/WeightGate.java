package com.norwood.wfcore.integration.wfweight;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fml.ModList;

import com.warfactory.ultimateweight.forge.capability.IPlayerWeightData1201;
import com.warfactory.ultimateweight.forge.capability.UltimateWeightCapabilities1201;
import com.warfactory.ultimateweight.v1201.WeightViews1201;



public final class WeightGate {

    private static final String MOD_ID = "ultimateweight";
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
            return WeightViews1201.weightOf(stack) > 0.0D;
        } catch (Throwable t) {
            return false;
        }
    }


    public static boolean isOverEncumbered(Player player) {
        if (!isLoaded() || player == null) {
            return false;
        }
        try {
            IPlayerWeightData1201 data = UltimateWeightCapabilities1201.get(player);
            if (data == null) {
                return false;
            }
            return data.getCurrentWeightKg() > data.getCarryCapacityKg();
        } catch (Throwable t) {
            return false;
        }
    }
}
