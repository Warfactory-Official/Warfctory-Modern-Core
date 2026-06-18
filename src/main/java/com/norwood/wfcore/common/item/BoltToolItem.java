package com.norwood.wfcore.common.item;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.common.block.BoltableCasingBlock;

/**
 * Functional replacement for the HBM boltgun: right-click an unbolted boltable casing to bolt it,
 * consuming {@value #BOLTS_REQUIRED} stainless steel bolts. No special rendering (per request).
 */
public class BoltToolItem extends Item {

    private static final int BOLTS_REQUIRED = 8;

    public BoltToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof BoltableCasingBlock) || state.getValue(BoltableCasingBlock.BOLTED)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (consumeBolts(player)) {
            level.setBlockAndUpdate(context.getClickedPos(), state.setValue(BoltableCasingBlock.BOLTED, true));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    private static boolean consumeBolts(Player player) {
        if (player.isCreative()) {
            return true;
        }
        ItemStack reference = ChemicalHelper.get(TagPrefix.bolt, GTMaterials.StainlessSteel, 1);
        if (reference.isEmpty()) {
            return true; // bolt item unavailable in this pack; don't block assembly
        }
        Item boltItem = reference.getItem();

        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(boltItem)) {
                available += stack.getCount();
            }
        }
        if (available < BOLTS_REQUIRED) {
            return false;
        }

        int remaining = BOLTS_REQUIRED;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(boltItem)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        return true;
    }
}
