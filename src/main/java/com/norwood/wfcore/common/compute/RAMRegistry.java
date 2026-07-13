package com.norwood.wfcore.common.compute;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Maps RAM items to their CWU throughput. */
public final class RAMRegistry {

    private static final Map<Item, RAMEntry> REGISTRY = new HashMap<>();

    private RAMRegistry() {}

    public static void register(ItemStack stack, int throughput) {
        if (stack != null && !stack.isEmpty()) register(stack.getItem(), throughput);
    }

    public static void register(Item item, int throughput) {
        REGISTRY.put(item, new RAMEntry(throughput));
    }

    public static void unregister(Item item) {
        if (item != null) REGISTRY.remove(item);
    }

    public static boolean isRegistered(Item item) {
        return item != null && REGISTRY.containsKey(item);
    }

    @Nullable
    public static RAMEntry getEntry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return REGISTRY.get(stack.getItem());
    }

    public static boolean isRAM(ItemStack stack) {
        return stack != null && !stack.isEmpty() && REGISTRY.containsKey(stack.getItem());
    }

    public static int size() {
        return REGISTRY.size();
    }

    public static void register() {
        register(GTItems.RANDOM_ACCESS_MEMORY_WAFER.asItem(), 256);
    }

    /** @param throughput Max CWU this RAM can handle per tick */
    public record RAMEntry(int throughput) {}
}
