package com.norwood.wfcore.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.common.block.IDetonatable;
import com.norwood.wfcore.common.block.MiningChargeBlock;
import com.norwood.wfcore.common.sound.WFSounds;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Wireless detonator. Sneak + right-click an {@link IDetonatable} block to link it (up to
 * {@value #MAX_CHARGES}); right-click (in air or on a block) to set off every linked charge at once. Linked
 * positions are kept in the stack's NBT.
 */
public class DetonatorItem extends Item {

    public static final int MAX_CHARGES = 5;
    private static final String CHARGES_KEY = "Charges";

    public DetonatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            BlockState state = level.getBlockState(context.getClickedPos());
            if (!IDetonatable.isExplosive(state)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                link(level, context.getItemInHand(), context.getClickedPos(), player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            detonateAll(context.getItemInHand(), (ServerLevel) level, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            return InteractionResultHolder.pass(stack); // sneaking links via useOn; in air it does nothing
        }
        if (!level.isClientSide) {
            detonateAll(stack, (ServerLevel) level, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void link(Level level, ItemStack stack, BlockPos pos, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = tag.getList(CHARGES_KEY, Tag.TAG_LONG);
        long packed = pos.asLong();
        for (int i = 0; i < list.size(); i++) {
            if (((LongTag) list.get(i)).getAsLong() == packed) {
                player.displayClientMessage(Component.translatable("wfcore.tool.detonator.already"), true);
                return;
            }
        }
        if (list.size() >= MAX_CHARGES) {
            player.displayClientMessage(Component.translatable("wfcore.tool.detonator.full", MAX_CHARGES), true);
            return;
        }
        list.add(LongTag.valueOf(packed));
        tag.put(CHARGES_KEY, list);
        arm(level, pos);
        level.playSound(null, pos, WFSounds.DETONATOR_ARM.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        player.displayClientMessage(
                Component.translatable("wfcore.tool.detonator.linked", list.size(), MAX_CHARGES), true);
    }

    /** Flip a charge to its armed blockstate (visual feedback that it's wired up). */
    private static void arm(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(MiningChargeBlock.ARMED) && !state.getValue(MiningChargeBlock.ARMED)) {
            level.setBlock(pos, state.setValue(MiningChargeBlock.ARMED, true), Block.UPDATE_CLIENTS);
        }
    }

    private static void detonateAll(ItemStack stack, ServerLevel level, @Nullable Player player) {
        CompoundTag tag = stack.getTag();
        ListTag list = tag == null ? null : tag.getList(CHARGES_KEY, Tag.TAG_LONG);
        if (list == null || list.isEmpty()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("wfcore.tool.detonator.empty"), true);
            }
            return;
        }
        if (player != null) {
            level.playSound(null, player.blockPosition(), WFSounds.DETONATOR_DETONATE.get(),
                    SoundSource.PLAYERS, 0.9F, 1.0F);
        }
        int fired = 0;
        for (int i = 0; i < list.size(); i++) {
            BlockPos pos = BlockPos.of(((LongTag) list.get(i)).getAsLong());
            if (IDetonatable.tryDetonate(level, pos, player)) {
                fired++;
            }
        }
        stack.removeTagKey(CHARGES_KEY);
        if (player != null) {
            player.displayClientMessage(Component.translatable("wfcore.tool.detonator.detonated", fired), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        int count = tag == null ? 0 : tag.getList(CHARGES_KEY, Tag.TAG_LONG).size();
        tooltip.add(Component.translatable("wfcore.tooltip.detonator.count", count, MAX_CHARGES)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("wfcore.tooltip.detonator.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
