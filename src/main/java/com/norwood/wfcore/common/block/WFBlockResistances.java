package com.norwood.wfcore.common.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.mixin.BlockBehaviourAccessor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets any block's blast resistance be overridden at load time (see {@code WFBlocks} in KubeJS startup scripts).
 * Overrides are staged here and applied once, after every mod's blocks are registered.
 */
public final class WFBlockResistances {

    private static final Map<ResourceLocation, Float> OVERRIDES = new LinkedHashMap<>();

    private WFBlockResistances() {}

    public static void set(ResourceLocation blockId, float resistance) {
        OVERRIDES.put(blockId, resistance);
    }

    /** Built-in overrides to vanilla blocks. */
    public static void registerDefaults() {
        // Obsidian no longer no-sells every explosive in the pack; brought down near sandbag tier.
        set(ResourceLocation.tryParse("minecraft:obsidian"), 9.0F);
    }

    /** Applies every staged override to its block. Call once, after all blocks are registered. */
    public static void apply() {
        int applied = 0;
        for (Map.Entry<ResourceLocation, Float> entry : OVERRIDES.entrySet()) {
            // BuiltInRegistries.BLOCK is DEFAULTED (an unknown id yields AIR), so use getOptional to keep
            // the null-means-missing contract the warning below relies on.
            Block block = BuiltInRegistries.BLOCK.getOptional(entry.getKey()).orElse(null);
            if (block == null) {
                WFCore.LOGGER.warn("WFBlockResistances: no such block '{}', skipping resistance override",
                        entry.getKey());
                continue;
            }
            ((BlockBehaviourAccessor) block).wfcore$setExplosionResistance(entry.getValue());
            applied++;
        }
        WFCore.LOGGER.info("WFBlockResistances: applied {} blast resistance override(s)", applied);
    }
}
