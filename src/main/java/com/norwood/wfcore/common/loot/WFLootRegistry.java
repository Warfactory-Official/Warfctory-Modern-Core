package com.norwood.wfcore.common.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** The per-table loot overrides and keep-list declared from Java/KubeJS, consulted by {@link WFLootEvents}. */
public final class WFLootRegistry {

    private static final Map<ResourceLocation, Supplier<LootTable>> OVERRIDES = new HashMap<>();
    private static final Set<ResourceLocation> KEPT = new HashSet<>();

    private WFLootRegistry() {}

    /** Replaces the table at {@code id} with whatever {@code table} builds at datapack-load time. */
    public static void set(ResourceLocation id, Supplier<LootTable> table) {
        KEPT.remove(id);
        OVERRIDES.put(id, table);
    }

    /** Whitelists {@code id} so the clear-all wipe leaves its original loot intact. */
    public static void keep(ResourceLocation id) {
        OVERRIDES.remove(id);
        KEPT.add(id);
    }

    public static Supplier<LootTable> override(ResourceLocation id) {
        return OVERRIDES.get(id);
    }

    public static boolean isKept(ResourceLocation id) {
        return KEPT.contains(id);
    }
}
