package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.item.PipeBlockItem;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeType;

import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.data.models.GTModels;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.norwood.wfcore.common.block.DepositBlock;
import com.norwood.wfcore.common.block.FoundryCastingBlock;
import com.norwood.wfcore.common.machine.DepositBlockEntity;
import com.norwood.wfcore.common.machine.FoundryCastingBlockEntity;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlock;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlockEntity;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlockItem;
import com.norwood.wfcore.common.pipenet.ac.ACPipeType;
import com.norwood.wfcore.common.pipenet.optical.CopperNetworkCableBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.tags.TagKey;

import static com.norwood.wfcore.integration.gt.GTBootstrap.WF_MACHINES;

/** The blocks that need GregTech: its casing art, its pipe nets, its fluids. Registered only when GregTech is loaded ({@link WFBlocks#init}). */
final class WFBlocksGT {
    private WFBlocksGT() {
    }

    static void init() {
        if (ACPipeType.VALUES.length != WFBlocks.AC_PIPE_TYPES) {
            throw new IllegalStateException("WFBlocks.AC_PIPE_TYPES is " + WFBlocks.AC_PIPE_TYPES + " but there are " + ACPipeType.VALUES.length + " AC pipe types");
        }
        // Bedrock-floor deposit block: unbreakable, drops nothing, drawn by its block-entity renderer.
        WFBlocks.DEPOSIT = WF_MACHINES.block("deposit", DepositBlock::new)
                .initialProperties(() -> Blocks.BEDROCK)
                .properties(p -> p.strength(-1.0F, 3_600_000.0F)
                        .noLootTable()
                        .isValidSpawn((state, level, pos, ent) -> false))
                .exBlockstate(GTModels.cubeAllModel(ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock")))
                .register();
        WFBlocks.DEPOSIT_BE = WF_MACHINES.blockEntity("deposit", DepositBlockEntity::new)
                .validBlock(WFBlocks.DEPOSIT)
                .register();

        // Drill head: the 'F' of the drilling-rig structure. Placeholder steel-casing art for now.
        WFBlocks.DRILL_HEAD = createCasingBlock("drill_head",
                GTCEu.id("block/casings/solid/machine_casing_solid_steel"));

        // Placeholder art reusing GTCEu's steel/tungstensteel casing textures until this mod has its own.
        WFBlocks.HARDENED_STEEL = createArmorBlock("hardened_steel", Blocks.IRON_BLOCK, SoundType.METAL, 10.0F, 75.0F,
                GTCEu.id("block/casings/solid/machine_casing_solid_steel"),
                CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH);
        WFBlocks.TUNGSTEN_PLATING = createArmorBlock("tungsten_plating", Blocks.IRON_BLOCK, SoundType.METAL, 20.0F, 130.0F,
                GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
                CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WRENCH);

        // HBM-ported foundry casting: slot a mold in, pipe molten metal (GT material fluids) in, take the
        // casting out. Basin = large size-1 molds (OBJ model), mold caster = small size-0 molds (elements
        // model); blockstate/model JSON is hand-authored under resources/assets/wfcore, so datagen is nooped.
        WFBlocks.FOUNDRY_BASIN = WF_MACHINES
                .block("foundry_basin", p -> new FoundryCastingBlock(p, FoundryMolds.SIZE_BASIN,
                        Block.box(0, 0, 0, 16, 14, 16)))
                .initialProperties(() -> Blocks.BRICKS)
                .properties(p -> p.strength(2.5F).sound(SoundType.STONE).noOcclusion()
                        .isValidSpawn((state, level, pos, ent) -> false))
                .lang("Foundry Casting Basin")
                .blockstate(NonNullBiConsumer.noop())
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .item(BlockItem::new)
                .model(NonNullBiConsumer.noop())
                .build()
                .register();
        WFBlocks.FOUNDRY_MOLD_CASTER = WF_MACHINES
                .block("foundry_mold_caster", p -> new FoundryCastingBlock(p, FoundryMolds.SIZE_CASTER,
                        Block.box(0, 0, 0, 16, 6, 16)))
                .initialProperties(() -> Blocks.BRICKS)
                .properties(p -> p.strength(2.5F).sound(SoundType.STONE).noOcclusion()
                        .isValidSpawn((state, level, pos, ent) -> false))
                .lang("Foundry Mold Caster")
                .blockstate(NonNullBiConsumer.noop())
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .item(BlockItem::new)
                .model(NonNullBiConsumer.noop())
                .build()
                .register();
        // One BE type serves both blocks; each reads its mold size from the owning FoundryCastingBlock.
        WFBlocks.FOUNDRY_CASTING_BE = WF_MACHINES.blockEntity("foundry_casting", FoundryCastingBlockEntity::new)
                .validBlocks(WFBlocks.FOUNDRY_BASIN, WFBlocks.FOUNDRY_MOLD_CASTER)
                .register();

        for (int i = 0; i < ACPipeType.VALUES.length; i++) {
            registerACPipe(i);
        }
        WFBlocks.AC_PIPE_BE = WF_MACHINES.blockEntity("ac_pipe", ACPipeBlockEntity::new)
                .onRegister(ACPipeBlockEntity::onBlockEntityRegister)
                .validBlocks(WFBlocks.AC_PIPES)
                .register();

        registerCopperNetworkCable();
        // Reuses GregTech's own OpticalPipeBlockEntity, so it shares the LevelOpticalPipeNet (see the block javadoc).
        WFBlocks.COPPER_NETWORK_CABLE_BE = WF_MACHINES.blockEntity("copper_network_cable", OpticalPipeBlockEntity::new)
                .validBlocks(WFBlocks.COPPER_NETWORK_CABLE)
                .register();

    }

    @SuppressWarnings("unchecked")
    private static void registerCopperNetworkCable() {
        WFBlocks.COPPER_NETWORK_CABLE = (BlockEntry<CopperNetworkCableBlock>) (BlockEntry<?>) WF_MACHINES
                .block("copper_network_cable", p -> new CopperNetworkCableBlock(p, OpticalPipeType.NORMAL))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.dynamicShape().noOcclusion().forceSolidOn())
                .gtBlockstate(GTModels::createPipeBlockModel)
                .defaultLoot()
                .tag(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WIRE_CUTTER)
                .addLayer(() -> RenderType::cutoutMipped)
                .item((block, props) -> new PipeBlockItem((PipeBlock) block, props))
                .model(NonNullBiConsumer.noop())
                .build()
                .register();
    }

    @SuppressWarnings("unchecked")
    private static void registerACPipe(int index) {
        ACPipeType type = ACPipeType.VALUES[index];
        WFBlocks.AC_PIPES[index] = (BlockEntry<ACPipeBlock>) (BlockEntry<?>) WF_MACHINES
                .block("%s_ac_pipe".formatted(type.getSerializedName()), p -> new ACPipeBlock(p, type))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.dynamicShape().noOcclusion().forceSolidOn())
                .gtBlockstate(GTModels::createPipeBlockModel)
                .defaultLoot()
                .tag(CustomTags.MINEABLE_WITH_CONFIG_VALID_PICKAXE_WIRE_CUTTER)
                .addLayer(() -> RenderType::cutoutMipped)
                .addLayer(() -> RenderType::translucent)
                .color(() -> ACPipeBlock::tintedColor)
                .item(ACPipeBlockItem::new)
                .model(NonNullBiConsumer.noop())
                .color(() -> ACPipeBlockItem::tintColor)
                .build()
                .register();
    }


    /** A solid fortification block with GregTech's casing art. */
    private static BlockEntry<Block> createArmorBlock(String name, Block base, SoundType sound, float hardness,
                                                      float resistance, ResourceLocation texture,
                                                      TagKey<Block> tool) {
        return WF_MACHINES.block(name, Block::new)
                .initialProperties(() -> base)
                .properties(p -> p.strength(hardness, resistance).sound(sound)
                        .isValidSpawn((state, level, pos, ent) -> false))
                .addLayer(() -> RenderType::solid)
                .exBlockstate(GTModels.cubeAllModel(texture))
                .tag(tool)
                .item(BlockItem::new)
                .build()
                .register();
    }

    private static BlockEntry<Block> createCasingBlock(String name, ResourceLocation texture) {
        return WF_MACHINES.block(name, Block::new)
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
