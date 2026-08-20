package com.norwood.wfcore.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.common.data.WFTags;
import org.jetbrains.annotations.Nullable;

/**
 * A block that can be set off by a {@code DetonatorItem}. The detonator links the positions of nearby
 * detonatables and fires them all at once; nothing else (fire, redstone, flint and steel) triggers them.
 */
public interface IDetonatable {

    /**
     * Set this block off. Called server-side by the detonator. The implementation is responsible for
     * removing/consuming the block as part of going off.
     *
     * @param detonator the player holding the detonator, or {@code null} if fired without one.
     */
    void detonate(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player detonator);

    /**
     * A recognised explosive: tagged {@code wfcore:explosive} (data-driven membership) and actually able to be
     * detonated in code (implements this interface). The tag is the filter; the interface is the capability.
     */
    static boolean isExplosive(BlockState state) {
        return state.is(WFTags.WF_EXPLOSIVE) && state.getBlock() instanceof IDetonatable;
    }

    /**
     * Detonate the block at {@code pos} if it's a recognised explosive (see {@link #isExplosive}).
     *
     * @return true if a charge was fired.
     */
    static boolean tryDetonate(ServerLevel level, BlockPos pos, @Nullable Player cause) {
        BlockState state = level.getBlockState(pos);
        if (state.is(WFTags.WF_EXPLOSIVE) && state.getBlock() instanceof IDetonatable detonatable) {
            detonatable.detonate(level, pos, state, cause);
            return true;
        }
        return false;
    }
}
