package com.norwood.wfcore.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.norwood.wfcore.common.data.FoundryMolds;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.machine.FoundryCastingBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The Foundry Casting Basin / Foundry Mold Caster block (ported from HBM's {@code FoundryCastingBase}); the
 * two differ only in {@link #moldSize}, shape and model, so this one class serves both (the mining charges'
 * two-tier pattern). Interactions mirror HBM: right-click takes the finished casting; right-click with a
 * matching-size GregTech casting mold ({@link FoundryMolds}) installs it (returning the old one); sneak +
 * empty hand extracts the mold — only while no melt is inside. The static model renders from the chunk mesh;
 * {@code FoundryCastingRenderer} draws the installed mold, casting and molten surface on top.
 */
public class FoundryCastingBlock extends Block implements EntityBlock {

    private final int moldSize;
    private final VoxelShape shape;

    public FoundryCastingBlock(Properties properties, int moldSize, VoxelShape shape) {
        super(properties);
        this.moldSize = moldSize;
        this.shape = shape;
    }

    /** Which {@link FoundryMolds.Mold#size()} fits this block (0 = mold caster, 1 = basin). */
    public int getMoldSize() {
        return moldSize;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoundryCastingBlockEntity(WFBlocks.FOUNDRY_CASTING_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != WFBlocks.FOUNDRY_CASTING_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> FoundryCastingBlockEntity.serverTick(lvl, pos, st,
                (FoundryCastingBlockEntity) be);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FoundryCastingBlockEntity be)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS; // server decides; client just swings
        }

        // 1. A finished casting is waiting: take it (highest priority, HBM parity).
        ItemStack output = be.takeOutput();
        if (!output.isEmpty()) {
            giveOrDrop(player, output);
            return InteractionResult.SUCCESS;
        }

        // 2. Holding a mold of this block's size: install it, handing back whatever was installed.
        ItemStack held = player.getItemInHand(hand);
        FoundryMolds.Mold heldMold = FoundryMolds.get(held);
        if (heldMold != null) {
            if (heldMold.size() != moldSize || !be.isTankEmpty()) {
                return InteractionResult.SUCCESS; // wrong size / melt inside: consume the click, do nothing
            }
            ItemStack previous = be.getMoldStack();
            ItemStack installed = held.copyWithCount(1);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            be.setMold(installed);
            if (!previous.isEmpty()) {
                giveOrDrop(player, previous);
            }
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // 3. Sneak + empty hand: extract the mold (only while no melt would be orphaned).
        if (player.isShiftKeyDown() && held.isEmpty() && !be.getMoldStack().isEmpty() && be.isTankEmpty()) {
            giveOrDrop(player, be.getMoldStack());
            be.setMold(ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) &&
                level.getBlockEntity(pos) instanceof FoundryCastingBlockEntity be) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.getMoldStack());
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), be.getOutputStack());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
