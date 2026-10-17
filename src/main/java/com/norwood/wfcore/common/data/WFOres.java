package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import com.norwood.wfcore.WFCore;

/** GT ore stone types. Must exist before GT's ore-block generation (BLOCK RegisterEvent, LOW): call at construction. */
public final class WFOres {

    // Hosted by veins of GT's deepslate layer: deeprock is in #minecraft:deepslate_ore_replaceables.
    public static final TagPrefix oreDeeprock = TagPrefix
            .oreTagPrefix(WFCore.id("deeprock"), BlockTags.MINEABLE_WITH_PICKAXE)
            .langValue("Deeprock %s Ore")
            .registerOre(WFOres::deeprock, () -> GTMaterials.Deepslate,
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).requiresCorrectToolForDrops()
                            .strength(WFBlocks.DEEPROCK_HARDNESS, WFBlocks.DEEPROCK_RESISTANCE)
                            .sound(SoundType.DEEPSLATE),
                    WFCore.id("block/deeprock"), false, false, true);

    private WFOres() {}

    public static void init() {}

    // gtceu registers its ore blocks before wfcore's blocks exist: GT reads AIR as "not yet registered".
    private static BlockState deeprock() {
        return WFBlocks.DEEPROCK.isBound() ? WFBlocks.DEEPROCK.getDefaultState() : Blocks.AIR.defaultBlockState();
    }
}
