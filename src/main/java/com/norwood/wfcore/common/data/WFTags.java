package com.norwood.wfcore.common.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import com.norwood.wfcore.WFCore;

public final class WFTags {

    public static final class EntityTypes {
        /** Vehicles in this tag are NOT blocked from GT crowbar pickup. */
        public static final TagKey<EntityType<?>> GT_CROWBAR_PICKUP_ALLOWED =
            TagKey.create(Registries.ENTITY_TYPE, WFCore.id("gt_crowbar_pickup_allowed"));

        private EntityTypes() {}
    }

    /**
     * Surface (tier 1) blocks a mining charge may break: vanilla stone/granite/dirt/sand families, ores (via
     * {@code forge:ores}) and other near-surface terrain. Crafted blocks (planks, bricks, metal blocks) are
     * deliberately absent. Defined as a datapack tag so packs can extend it.
     */
    public static final TagKey<Block> NATURAL_BLAST_BREAKABLE = BlockTags.create(WFCore.id("natural_blast_breakable"));

    /**
     * Deep (tier 2) blocks: the deepslate/tuff matrix found below the surface stone layer. Only charges of
     * tier 2 or higher break these, on top of everything in {@link #NATURAL_BLAST_BREAKABLE}.
     */
    public static final TagKey<Block> DEEP_BLAST_BREAKABLE = BlockTags.create(WFCore.id("deep_blast_breakable"));

    // Deepslate-hosted ores
    public static final TagKey<Block> DEEP_ORES = BlockTags.create(WFCore.id("deep_ores"));

    // WF explosives
    public static final TagKey<Block> WF_EXPLOSIVE = BlockTags.create(WFCore.id("explosive"));

    private WFTags() {}
}
