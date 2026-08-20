package com.norwood.wfcore.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.common.sound.WFSounds;
import com.norwood.wfcore.common.tool.BoltGunConversions;

import java.util.List;


public class BoltToolItem extends Item {

    public BoltToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BoltGunConversions.Conversion conversion = BoltGunConversions.get(state);
        if (conversion == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (consumeCost(player, conversion.cost())) {
            level.setBlockAndUpdate(pos, conversion.output());
            playBoltFeedback(level, pos, context.getClickLocation(), context.getClickedFace());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    private static void playBoltFeedback(Level level, BlockPos pos, Vec3 hit, Direction face) {
        if (level instanceof ServerLevel server) {
            double off = 0.25;
            server.sendParticles(ParticleTypes.EXPLOSION,
                    hit.x + face.getStepX() * off,
                    hit.y + face.getStepY() * off,
                    hit.z + face.getStepZ() * off,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, pos, WFSounds.BOLT_TOOL_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Consume the conversion's item cost from the player, or return false if they can't afford it. */
    private static boolean consumeCost(Player player, List<ItemStack> cost) {
        if (player.isCreative() || cost.isEmpty()) {
            return true;
        }
        for (ItemStack required : cost) {
            int available = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(required.getItem())) {
                    available += stack.getCount();
                }
            }
            if (available < required.getCount()) {
                return false;
            }
        }
        for (ItemStack required : cost) {
            int remaining = required.getCount();
            for (ItemStack stack : player.getInventory().items) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.is(required.getItem())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
        }
        return true;
    }
}
