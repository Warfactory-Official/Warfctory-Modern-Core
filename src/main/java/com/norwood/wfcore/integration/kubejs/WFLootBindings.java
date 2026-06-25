package com.norwood.wfcore.integration.kubejs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootTable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.norwood.wfcore.common.loot.WFLootBuilder;
import com.norwood.wfcore.common.loot.WFLootRegistry;

/**
 * KubeJS binding exposed as {@code WFLoot} in startup scripts. By default WFCore empties every chest and fishing
 * loot table (config {@code clearStructureLoot}); use this to repopulate, replace, or keep the ones you want.
 *
 * <pre>{@code
 * // fluent item builder
 * WFLoot.set('minecraft:chests/simple_dungeon')
 *     .rolls(1, 3)
 *     .item('minecraft:diamond', 1, 4, 10)        // item, min, max, weight
 *     .item('minecraft:iron_ingot', 2, 8, 30)
 *     .pool().rolls(1).item('minecraft:emerald')  // a second pool
 *
 * // raw vanilla loot-table JSON (escape hatch for anything the builder can't express)
 * WFLoot.json('minecraft:chests/igloo/chest', { type: 'minecraft:chest', pools: [ /* ... *\/ ] })
 *
 * // keep a table's original loot (skip the wipe)
 * WFLoot.keep('minecraft:chests/end_city_treasure')
 * }</pre>
 */
public class WFLootBindings {

    private static final Gson LOOT_GSON = Deserializers.createLootTableSerializer().create();

    /** Opens a fluent builder that replaces the loot at {@code id}; the override is registered immediately. */
    public WFLootBuilder set(String id) {
        ResourceLocation rl = new ResourceLocation(id);
        WFLootBuilder builder = new WFLootBuilder(rl);
        WFLootRegistry.set(rl, builder::build);
        return builder;
    }

    /** Replaces the loot at {@code id} with a full vanilla loot-table JSON object. */
    public void json(String id, JsonObject json) {
        ResourceLocation rl = new ResourceLocation(id);
        WFLootRegistry.set(rl, () -> LOOT_GSON.fromJson(json, LootTable.class));
    }

    /** Same as {@link #json(String, JsonObject)} but from a JSON string. */
    public void jsonString(String id, String json) {
        json(id, JsonParser.parseString(json).getAsJsonObject());
    }

    /** Whitelists {@code id} so the default clear-all leaves its loot untouched. */
    public void keep(String id) {
        WFLootRegistry.keep(new ResourceLocation(id));
    }

    /** Forces {@code id} empty regardless of its loot context (useful when clear-all is disabled). */
    public void clear(String id) {
        WFLootRegistry.set(new ResourceLocation(id), () -> LootTable.lootTable().build());
    }
}
