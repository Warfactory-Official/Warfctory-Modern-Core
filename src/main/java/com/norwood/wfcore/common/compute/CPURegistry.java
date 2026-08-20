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

    public static void unregister(Item item) {
        if (item != null) REGISTRY.remove(item);
    }

    public static boolean isRegistered(Item item) {
        return item != null && REGISTRY.containsKey(item);
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

        public double getCurrentEfficency(long power) {
            if (power < minPower) return 0;
            long span = maxPower - minPower;
            // load = fraction of the power band in use (0 at idle, 1 at max); the dropoff makes a CPU run
            // less efficiently the harder it is pushed. Guard the degenerate span (maxPower == minPower).
            double load = span <= 0 ? 1.0 : (double) (power - minPower) / span;
            double dropoff = WFComputeConfig.efficiencyDropoff() * Math.pow(load, 2);
            return Math.max(WFComputeConfig.minEfficiency(), efficiency - dropoff);
        }

        public long getCWU(long power) {
            return (long) (power * getCurrentEfficency(power));
        }

        public long getMaxCWU() {
            return (long) (maxPower * efficiency);
        }

        public double getHeat(long power) {
            long wasteEU = power - getCWU(power);
            return wasteEU * WFComputeConfig.heatRatio();
        }
    }
}
