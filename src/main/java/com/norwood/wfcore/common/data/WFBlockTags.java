package com.norwood.wfcore.common.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** GregTech's mineable tags by id, so blocks can name them without GregTech being there (the tags are just data). */
public final class WFBlockTags {
    public static final TagKey<Block> MINEABLE_PICKAXE_OR_WRENCH = gt("mineable/pickaxe_or_wrench");
    public static final TagKey<Block> MINEABLE_PICKAXE_OR_WIRE_CUTTER = gt("mineable/pickaxe_or_wire_cutter");

    private WFBlockTags() {
    }

    private static TagKey<Block> gt(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("gtceu", path));
    }
}
