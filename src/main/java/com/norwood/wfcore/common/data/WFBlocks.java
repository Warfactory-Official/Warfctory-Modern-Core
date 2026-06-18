package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.common.data.models.GTModels;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.generators.ConfiguredModel;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.block.BoltableCasingBlock;
import com.tterrag.registrate.util.entry.BlockEntry;

import static com.norwood.wfcore.WFCore.EXAMPLE_REGISTRATE;

/**
 * WFCore casing blocks. Mirrors GTCEu's "one block per casing" convention (see GTBlocks).
 */
public class WFBlocks {

    public static BlockEntry<Block> ALUMINIUM_SHEET_CASING;
    public static BlockEntry<BoltableCasingBlock> BOLTABLE_CASING;

    public static void init() {
        ALUMINIUM_SHEET_CASING = createCasingBlock("aluminium_sheet_casing",
                WFCore.id("block/casings/aluminium_sheet_casing"));

        BOLTABLE_CASING = EXAMPLE_REGISTRATE
                .block("boltable_casing", BoltableCasingBlock::new)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.isValidSpawn((state, level, pos, ent) -> false))
                .addLayer(() -> RenderType::solid)
                .blockstate((ctx, prov) -> prov.getVariantBuilder(ctx.get()).forAllStates(state -> {
                    String suffix = state.getValue(BoltableCasingBlock.BOLTED) ? "_bolted" : "";
                    return ConfiguredModel.builder()
                            .modelFile(prov.models().cubeAll("boltable_casing" + suffix,
                                    WFCore.id("block/casings/boltable_casing" + suffix)))
                            .build();
                }))
                .tag(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH)
                .item(BlockItem::new)
                .build()
                .register();
    }

    private static BlockEntry<Block> createCasingBlock(String name, ResourceLocation texture) {
        return EXAMPLE_REGISTRATE.block(name, Block::new)
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.isValidSpawn((state, level, pos, ent) -> false))
                .addLayer(() -> RenderType::solid)
                .exBlockstate(GTModels.cubeAllModel(texture))
                .tag(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH)
                .item(BlockItem::new)
                .build()
                .register();
    }
}
