package com.norwood.wfcore.common.item.behavior;

import com.gregtechceu.gtceu.api.pipenet.IPipeNode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import com.norwood.wfcore.WFCore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks a straight run of same-type pipes from a source pipe, applying a {@link ITraverseOption} operation
 * (connect / disconnect / block / unblock) to every pipe along the way. Branches are followed recursively via
 * sub-walkers, mirroring GTCEu's own {@code PipeNetWalker}, but this walker operates directly on world
 * {@link BlockEntity block entities} rather than a {@code PipeNet} so it needs no net data.
 * <p>
 * Ported from Susy-Core ({@code supersymmetry.common.item.behavior.PipeOperationWalker}) for GTCEu Modern:
 * {@code World}/{@code EnumFacing}/{@code IPipeTile} become {@link Level}/{@link Direction}/{@link IPipeNode},
 * and walked pipes are tracked by {@link BlockPos} instead of tile identity.
 */
public class PipeOperationWalker<T extends IPipeNode<?, ?>> {

    private final Level level;
    private final List<Direction> nextPipeFacings = new ArrayList<>();
    private final List<T> nextPipes = new ArrayList<>();
    private final BlockPos.MutableBlockPos currentPos;
    private final Class<T> basePipeClass;
    private boolean reverse = false;
    private PipeOperationWalker<T> root;
    private Set<BlockPos> walked;
    private List<PipeOperationWalker<T>> walkers;
    private T currentPipe;
    private Direction from = null;
    private int walkedBlocks;
    private boolean invalid;
    private boolean running;
    private boolean failed = false;
    /// Operation to run on every pipe
    private ITraverseOption option;
    /// Only set for the root walker
    @Nullable
    private Direction direction;

    private PipeOperationWalker(Level level, BlockPos sourcePipe, int walkedBlocks, Class<T> basePipeClass) {
        this.level = level;
        this.walkedBlocks = walkedBlocks;
        this.currentPos = sourcePipe.mutable();
        this.basePipeClass = basePipeClass;
        this.root = this;
    }

    @SuppressWarnings("unchecked")
    public static <T extends IPipeNode<?, ?>> int collectPipeNet(Level level, BlockPos sourcePipe, T pipe,
                                                                 Direction direction, ITraverseOption option,
                                                                 int maxWalks) {
        PipeOperationWalker<T> walker = new PipeOperationWalker<>(level, sourcePipe, 0, (Class<T>) pipe.getClass());
        walker.currentPipe = pipe;
        walker.direction = direction;
        walker.option = option;
        walker.traverse(maxWalks);
        return walker.failed ? 0 : walker.walkedBlocks;
    }

    private void traverse(int maxWalks) {
        if (invalid) throw new IllegalStateException("This walker already walked!");
        this.root = this;
        this.walked = new HashSet<>();
        this.running = true;

        int i = 0;
        // noinspection StatementWithEmptyBody
        while (running && !step() && i++ < maxWalks) {
            /* Do nothing */
        }

        this.walkedBlocks = i;
        this.running = false;
        this.walked = null;

        if (walkedBlocks >= maxWalks) {
            WFCore.LOGGER.warn("The pipe operation walker reached the maximum amount of walks {}", walkedBlocks);
        }
        invalid = true;
    }

    private boolean step() {
        if (walkers == null) {
            if (!checkCurrent()) {
                this.root.failed = true;
                return true;
            }

            if (nextPipeFacings.isEmpty()) return true;
            if (nextPipeFacings.size() == 1) {

                T next = nextPipes.get(0);
                Direction into = nextPipeFacings.get(0);

                this.root.option.operate(into, currentPipe, next, reverse);

                this.currentPos.set(next.getPipePos());
                this.currentPipe = next;
                this.from = into.getOpposite();
                this.walkedBlocks++;

                return !root.running;
            }

            walkers = new ArrayList<>();
            for (int i = 0; i < nextPipeFacings.size(); i++) {
                Direction into = nextPipeFacings.get(i);

                PipeOperationWalker<T> walker = createSubWalker(level, into, currentPos.relative(into),
                        walkedBlocks + 1);

                T nextPipe = nextPipes.get(i);

                root.option.operate(into, currentPipe, nextPipe, walker.reverse);

                walker.root = this.root;
                walker.currentPipe = nextPipe;
                walker.from = into.getOpposite();
                this.walkers.add(walker);
            }
        }

        walkers.removeIf(PipeOperationWalker::step);

        return !root.running || walkers.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private boolean checkCurrent() {
        this.nextPipeFacings.clear();
        this.nextPipes.clear();
        if (currentPipe == null) {
            BlockEntity thisPipe = level.getBlockEntity(currentPos);
            if (!(thisPipe instanceof IPipeNode)) {
                WFCore.LOGGER.warn("PipeOperationWalker expected a pipe, but found {} at {}", thisPipe, currentPos);
                return false;
            }
            if (!basePipeClass.isAssignableFrom(thisPipe.getClass())) {
                return false;
            }
            currentPipe = (T) thisPipe;
        }
        T pipeTile = currentPipe;

        this.root.walked.add(pipeTile.getPipePos());

        List<Direction> facings = root.option.findNext(from != null ? from : direction, pipeTile);

        if (walkedBlocks == 0) {
            facings.add(direction); // Special case for the root node
        }

        for (Direction side : facings) {
            BlockEntity tile = pipeTile.getNeighbor(side);
            if (tile != null && basePipeClass.isAssignableFrom(tile.getClass())) {
                T otherPipe = (T) tile;
                if (!isWalked(otherPipe) && root.option.canWalkInto(pipeTile, otherPipe)) {
                    nextPipeFacings.add(side);
                    nextPipes.add(otherPipe);
                }
            }
        }
        return true;
    }

    private boolean isWalked(T pipe) {
        return root.walked.contains(pipe.getPipePos());
    }

    private PipeOperationWalker<T> createSubWalker(Level level, Direction facingToNextPos, BlockPos nextPos,
                                                  int walkedBlocks) {
        boolean reverse = this.direction != null ? facingToNextPos != direction : this.reverse;
        PipeOperationWalker<T> subWalker = new PipeOperationWalker<>(level, nextPos, walkedBlocks, this.basePipeClass);
        subWalker.reverse = reverse;
        return subWalker;
    }
}
