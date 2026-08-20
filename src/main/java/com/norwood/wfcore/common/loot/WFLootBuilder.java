package com.norwood.wfcore.common.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.core.registries.BuiltInRegistries;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import com.norwood.wfcore.WFCore;

import java.util.ArrayList;
import java.util.List;

/** Fluent loot-table builder for {@code WFLoot.set(id)}; emits a vanilla {@link LootTable} at datapack-load time. */
public final class WFLootBuilder {

    private record Entry(ResourceLocation item, int min, int max, int weight) {}

    private static final class Pool {

        private NumberProvider rolls = ConstantValue.exactly(1);
        private final List<Entry> entries = new ArrayList<>();
    }

    private final ResourceLocation id;
    private LootContextParamSet paramSet = LootContextParamSets.CHEST;
    private final List<Pool> pools = new ArrayList<>();

    public WFLootBuilder(ResourceLocation id) {
        this.id = id;
    }

    private Pool current() {
        if (pools.isEmpty()) pools.add(new Pool());
        return pools.get(pools.size() - 1);
    }

    /** Starts a fresh pool; subsequent {@code rolls}/{@code item} calls apply to it. */
    public WFLootBuilder pool() {
        pools.add(new Pool());
        return this;
    }

    /** Overrides the loot context (default {@code minecraft:chest}), e.g. {@code "minecraft:fishing"}. */
    public WFLootBuilder paramSet(String set) {
        // The name->set map is private in 1.21; its Codec is the supported lookup.
        LootContextParamSet resolved = LootContextParamSets.CODEC
                .parse(JsonOps.INSTANCE, new JsonPrimitive(set))
                .result().orElse(null);
        if (resolved != null) this.paramSet = resolved;
        return this;
    }

    public WFLootBuilder rolls(int rolls) {
        current().rolls = ConstantValue.exactly(rolls);
        return this;
    }

    public WFLootBuilder rolls(int min, int max) {
        current().rolls = UniformGenerator.between(min, max);
        return this;
    }

    public WFLootBuilder item(String item) {
        return item(item, 1, 1, 1);
    }

    public WFLootBuilder item(String item, int count) {
        return item(item, count, count, 1);
    }

    public WFLootBuilder item(String item, int min, int max) {
        return item(item, min, max, 1);
    }

    public WFLootBuilder item(String item, int min, int max, int weight) {
        current().entries.add(new Entry(ResourceLocation.parse(item), Math.max(0, min), Math.max(0, max),
                Math.max(1, weight)));
        return this;
    }

    public LootTable build() {
        LootTable.Builder table = LootTable.lootTable().setParamSet(paramSet);
        for (Pool p : pools) {
            LootPool.Builder pool = LootPool.lootPool().setRolls(p.rolls);
            for (Entry e : p.entries) {
                Item item = BuiltInRegistries.ITEM.getOptional(e.item()).orElse(null);
                if (item == null) {
                    WFCore.LOGGER.warn("WFLoot: unknown item {} in loot override {}", e.item(), id);
                    continue;
                }
                LootPoolSingletonContainer.Builder<?> entry = LootItem.lootTableItem(item).setWeight(e.weight());
                if (e.min() != 1 || e.max() != 1) {
                    entry.apply(SetItemCountFunction.setCount(count(e.min(), e.max())));
                }
                pool.add(entry);
            }
            table.withPool(pool);
        }
        return table.build();
    }

    private static NumberProvider count(int min, int max) {
        return min == max ? ConstantValue.exactly(min) : UniformGenerator.between(min, max);
    }
}
