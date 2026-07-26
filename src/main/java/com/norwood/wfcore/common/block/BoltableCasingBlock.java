package com.norwood.wfcore.common.block;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import com.norwood.wfcore.common.tool.BoltGunConversions;

import java.util.ArrayList;
import java.util.List;

/**
 * A casing the bolt gun can convert. Bolted and unbolted are separate blocks (post-flattening: each has its
 * own item and shows in the creative menu); the {@link com.norwood.wfcore.common.item.BoltToolItem bolt gun}
 * swaps one for the other per the data-driven {@link BoltGunConversions} map. Breaking a block that a
 * conversion produced refunds whatever that conversion consumed, matching the 1.12.2 drop behaviour.
 */
public class BoltableCasingBlock extends Block {

    public BoltableCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        List<ItemStack> refund = BoltGunConversions.costForOutput(state);
        if (refund.isEmpty()) {
            return drops;
        }
        List<ItemStack> withRefund = new ArrayList<>(drops);
        for (ItemStack stack : refund) {
            withRefund.add(stack.copy());
        }
        return withRefund;
    }
}
