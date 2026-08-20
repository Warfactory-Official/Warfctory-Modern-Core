package com.norwood.wfcore.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.common.machine.DepositBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A bedrock-floor resource deposit. Unbreakable by hand and drops nothing — only the drilling rig extracts from
 * it (and turns it to bedrock when drained). The chunk mesh draws nothing ({@link RenderShape#ENTITYBLOCK_ANIMATED});
 * {@code DepositBlockEntityRenderer} draws the cube with the deposit type's texture.
 */
public class DepositBlock extends Block implements EntityBlock {

    public DepositBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepositBlockEntity(DepositBlockEntity.type(), pos, state);
    }
}
