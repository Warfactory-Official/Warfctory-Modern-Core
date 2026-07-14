package com.norwood.wfcore.common.item.behavior;

import com.gregtechceu.gtceu.api.pipenet.IPipeNode;

import net.minecraft.core.Direction;

import java.util.List;

/**
 * A single kind of operation the {@link PipeOperationWalker} can propagate along a straight pipe run.
 * <p>
 * Ported from Susy-Core ({@code supersymmetry.common.item.behavior.ITraverseOption}) for GTCEu Modern:
 * {@code EnumFacing} -&gt; {@link Direction}, {@code IPipeTile} -&gt; {@link IPipeNode}.
 */
public interface ITraverseOption {

    /**
     * @param from the side the walker entered this pipe from (or the initial direction for the root node)
     * @param pipe the pipe currently being visited
     * @return the side(s) the walker should continue into next
     */
    List<Direction> findNext(Direction from, IPipeNode<?, ?> pipe);

    /**
     * Applies this operation between {@code self} and the pipe found on {@code from}.
     *
     * @param from    the side of {@code self} that faces {@code other}
     * @param self    the pipe being operated on
     * @param other   the neighbouring pipe reached along {@code from}
     * @param reverse whether the walker reached this pipe walking against the initial direction
     */
    void operate(Direction from, IPipeNode<?, ?> self, IPipeNode<?, ?> other, boolean reverse);

    /**
     * Whether the walker may cross from {@code self} into the neighbouring {@code other} pipe. Used to keep the
     * connect/disconnect/block operations within a single painted colour (see {@link TraverseOptions}); the
     * whole-net colouring operation walks everything.
     *
     * @param self  the pipe currently being visited
     * @param other a same-type neighbouring pipe the walker could continue into
     * @return {@code true} to walk into {@code other}
     */
    default boolean canWalkInto(IPipeNode<?, ?> self, IPipeNode<?, ?> other) {
        return true;
    }
}
