package com.norwood.wfcore.common.item.behavior;

import com.gregtechceu.gtceu.api.pipenet.IPipeNode;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;


public enum TraverseOptions implements ITraverseOption {

    CONNECTING(Lambdas.FIND_TO_CONNECT, Lambdas.CONNECTOR),
    DISCONNECTING(Lambdas.FIND_CONNECTED, Lambdas.DISCONNECTOR),
    BLOCKING(Lambdas.FIND_CONNECTED, Lambdas.BLOCKER),
    UNBLOCKING(Lambdas.FIND_CONNECTED, Lambdas.UNBLOCKER),
    ;

    private final PathFinder pathFinder;
    private final PipeOperator pipeOperator;

    TraverseOptions(PathFinder pathFinder, PipeOperator pipeOperator) {
        this.pathFinder = pathFinder;
        this.pipeOperator = pipeOperator;
    }

    @Override
    public List<Direction> findNext(Direction from, IPipeNode<?, ?> pipe) {
        return pathFinder.findNext(from, pipe);
    }

    @Override
    public void operate(Direction from, IPipeNode<?, ?> self, IPipeNode<?, ?> other, boolean reverse) {
        pipeOperator.operate(from, self, other, reverse);
    }

    @Override
    public boolean canWalkInto(IPipeNode<?, ?> self, IPipeNode<?, ?> other) {
        return self.getPaintingColor() == other.getPaintingColor();
    }


    public static ITraverseOption coloring(int paintColor) {
        return new ColoringOption(paintColor);
    }

    private record ColoringOption(int paintColor) implements ITraverseOption {

        @Override
        public List<Direction> findNext(Direction from, IPipeNode<?, ?> pipe) {
            return Lambdas.FIND_ALL_CONNECTED.findNext(from, pipe);
        }

        @Override
        public void operate(Direction from, IPipeNode<?, ?> self, IPipeNode<?, ?> other, boolean reverse) {
            if (self.getPaintingColor() != paintColor) {
                self.setPaintingColor(paintColor);
            }
            other.setPaintingColor(paintColor);
        }
    }

    @FunctionalInterface
    private interface PathFinder {

        List<Direction> findNext(Direction from, IPipeNode<?, ?> pipe);
    }

    @FunctionalInterface
    private interface PipeOperator {

        void operate(Direction facingToOther, IPipeNode<?, ?> self, IPipeNode<?, ?> other, boolean reverse);
    }

    static class Lambdas {

        static final PathFinder FIND_TO_CONNECT = (from, pipe) -> {
            List<Direction> ret = new ArrayList<>(1);

            for (Direction facing : Direction.values()) {
                if (facing == from) continue;
                BlockEntity other = pipe.getNeighbor(facing);
                if (other instanceof IPipeNode<?, ?> otherPipe && pipe.getClass().isAssignableFrom(other.getClass()) &&
                        otherPipe.getConnections() == 0) {
                    if (ret.isEmpty()) {
                        ret.add(facing);
                    } else {
                        ret.clear();
                        return ret;
                    }
                }
            }
            return ret;
        };


        static final PathFinder FIND_CONNECTED = (from, pipe) -> {
            List<Direction> ret = new ArrayList<>(1);

            for (Direction facing : Direction.values()) {
                if (facing == from) continue;
                if (pipe.isConnected(facing)) {
                    if (ret.isEmpty()) {
                        ret.add(facing);
                    } else {
                        ret.clear();
                        return ret;
                    }
                }
            }
            return ret;
        };

        static final PathFinder FIND_ALL_CONNECTED = (from, pipe) -> {
            List<Direction> ret = new ArrayList<>(5);
            for (Direction facing : Direction.values()) {
                if (facing == from) continue;
                if (pipe.isConnected(facing)) {
                    ret.add(facing);
                }
            }
            return ret;
        };

        static final PipeOperator CONNECTOR = (facingToOther, self, other, reverse) -> self.setConnection(facingToOther,
                true, false);

        static final PipeOperator DISCONNECTOR = (facingToOther, self, other, reverse) -> self
                .setConnection(facingToOther, false, false);

        static final PipeOperator BLOCKER = (facingToOther, self, other, reverse) -> {
            if (reverse) {
                other.setBlocked(facingToOther.getOpposite(), true);
            } else {
                self.setBlocked(facingToOther, true);
            }
        };

        static final PipeOperator UNBLOCKER = (facingToOther, self, other, reverse) -> {
            if (reverse) {
                other.setBlocked(facingToOther.getOpposite(), false);
            } else {
                self.setBlocked(facingToOther, false);
            }
        };
    }
}
