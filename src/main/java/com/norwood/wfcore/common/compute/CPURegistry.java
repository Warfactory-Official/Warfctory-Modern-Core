package com.norwood.wfcore.common.compute;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Maps CPU items to their compute stats. Keyed by item (modern items have no meta). */
public final class CPURegistry {

    private static final Map<Item, CPUEntry> REGISTRY = new HashMap<>();

    private CPURegistry() {}

    public static void register(ItemStack stack, CPUEntry entry) {
        if (stack != null && !stack.isEmpty()) register(stack.getItem(), entry);
    }

    public static void register(Item item, CPUEntry entry) {
        REGISTRY.put(item, entry);
    }

    @Nullable
    public static CPUEntry getEntry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return REGISTRY.get(stack.getItem());
    }

    public static boolean isCPU(ItemStack stack) {
        return stack != null && !stack.isEmpty() && REGISTRY.containsKey(stack.getItem());
    }

    public static int size() {
        return REGISTRY.size();
    }

    public static void register() {
        register(GTItems.INTEGRATED_CIRCUIT_MV.asItem(),
                new CPUEntry(0.5, GTValues.V[GTValues.HV], GTValues.VH[GTValues.MV]));
    }

    public record CPUEntry(
                           double efficiency, // 0.0 to 1.0 (lower = more heat)
                           long maxPower,     // Max power draw (EU/t)
                           long minPower      // Idle/baseline power draw (EU/t)
    ) {

        private static final double EU_TO_HEAT_RATIO = 0.05;

        public double getCurrentEfficency(long power) {
            if (power < minPower) return 0;
            double load = (double) (power - minPower) / (maxPower - power);
            double dropoff = 0.2 * Math.pow(load, 2);
            return Math.max(0.05, efficiency - dropoff);
        }

        public long getCWU(long power) {
            return (long) (power * getCurrentEfficency(power));
        }

        public long getMaxCWU() {
            return (long) (maxPower * efficiency);
        }

        public double getHeat(long power) {
            long wasteEU = power - getCWU(power);
            return wasteEU * EU_TO_HEAT_RATIO;
        }
    }
}
