package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.models.GTModels;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.client.model.generators.ConfiguredModel;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.block.BoltableCasingBlock;
import com.norwood.wfcore.common.block.DepositBlock;
import com.norwood.wfcore.common.block.MiningChargeBlock;
import com.norwood.wfcore.common.machine.DepositBlockEntity;
import com.norwood.wfcore.common.machine.MiningChargeBlockEntity;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlock;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlockEntity;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlockItem;
import com.norwood.wfcore.common.pipenet.ac.ACPipeType;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import static com.norwood.wfcore.WFCore.WF_MACHINES;

/**
 * WFCore casing blocks. Mirrors GTCEu's "one block per casing" convention (see GTBlocks).
 */
public class WFBlocks {

    public static BlockEntry<Block> ALUMINIUM_SHEET_CASING;
    public static BlockEntry<BoltableCasingBlock> BOLTABLE_CASING;
    public static BlockEntry<Block> DRILL_HEAD;
    public static BlockEntry<DepositBlock> DEPOSIT;
    public static BlockEntityEntry<DepositBlockEntity> DEPOSIT_BE;
    public static BlockEntry<MiningChargeBlock> MINING_CHARGE;
    public static BlockEntry<MiningChargeBlock> DEEP_MINING_CHARGE;
    public static BlockEntityEntry<MiningChargeBlockEntity> MINING_CHARGE_BE;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static final BlockEntry<ACPipeBlock>[] AC_PIPES = new BlockEntry[ACPipeType.VALUES.length];
    public static BlockEntityEntry<ACPipeBlockEntity> AC_PIPE_BE;

    public static void init() {
        ALUMINIUM_SHEET_CASING = createCasingBlock("aluminium_sheet_casing",
                WFCore.id("block/casings/aluminium_sheet_casing"));

        // Drill head: the 'F' of the drilling-rig structure. Placeholder steel-casing art for now.
        DRILL_HEAD = createCasingBlock("drill_head",
                GTCEu.id("block/casings/solid/machine_casing_solid_steel"));

        // Bedrock-floor deposit block: unbreakable, drops nothing, drawn by its block-entity renderer.
        DEPOSIT = WF_MACHINES.block("deposit", DepositBlock::new)
                .initialProperties(() -> Blocks.BEDROCK)
                .properties(p -> p.strength(-1.0F, 3_600_000.0F)
                        .noLootTable()
                        .isValidSpawn((state, level, pos, ent) -> false))
                .exBlockstate(GTModels.cubeAllModel(new ResourceLocation("minecraft", "block/bedrock")))
                .register();
        DEPOSIT_BE = WF_MACHINES.blockEntity("deposit", DepositBlockEntity::new)
                .validBlock(DEPOSIT)
                .register();

        // Demolition charge: cube blast (radius 3) that mines only natural blocks, fortune II on ores. Inert
        // to fire/flint and steel; only a detonator sets it off. Tier 1 breaks surface stone/dirt/ores.
        // Blockstate/models/item-model are hand-authored (top/side/bottom + armed variants), so datagen is
        // suppressed with noop and the JSON under resources/assets/wfcore drives rendering.
        MINING_CHARGE = WF_MACHINES.block("mining_charge", p -> new MiningChargeBlock(p, 3, 2, 1))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.strength(0.8F).sound(SoundType.METAL)
                        .isValidSpawn((state, level, pos, ent) -> false))
                .lang("Mining Charge")
                .blockstate(NonNullBiConsumer.noop())
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .item(BlockItem::new)
                .model(NonNullBiConsumer.noop())
                .build()
                .register();

        // Tier 2 charge: also chews through the deepslate/tuff matrix (see deep_blast_breakable tag).
        DEEP_MINING_CHARGE = WF_MACHINES.block("deep_mining_charge", p -> new MiningChargeBlock(p, 3, 2, 2))
                .initialProperties(() -> Blocks.IRON_BLOCK)
                .properties(p -> p.strength(0.8F).sound(SoundType.METAL)
                        .isValidSpawn((state, level, pos, ent) -> false))
                .lang("Deep Mining Charge")
                .blockstate(NonNullBiConsumer.noop())
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .item(BlockItem::new)
                .model(NonNullBiConsumer.noop())
                .build()
                .register();

        // Shared block entity for both charge tiers; stores the placer's UUID.
        MINING_CHARGE_BE = WF_MACHINES.blockEntity("mining_charge", MiningChargeBlockEntity::new)
                .validBlocks(MINING_CHARGE, DEEP_MINING_CHARGE)
                .register();

        for (int i = 0; i < ACPipeType.VALUES.length; i++) {
            registerACPipe(i);
        }
        AC_PIPE_BE = WF_MACHINES.blockEntity("ac_pipe", ACPipeBlockEntity::new)
                .onRegister(ACPipeBlockEntity::onBlockEntityRegister)
                .validBlocks(AC_PIPES)
                .register();

        BOLTABLE_CASING = WF_MACHINES
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

    @SuppressWarnings("unchecked")
    private static void registerACPipe(int index) {
        ACPipeType type = ACPipeType.VALUES[index];
        AC_PIPES[index] = (BlockEntry<ACPipeBlock>) (BlockEntry<?>) WF_MACHINES
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
