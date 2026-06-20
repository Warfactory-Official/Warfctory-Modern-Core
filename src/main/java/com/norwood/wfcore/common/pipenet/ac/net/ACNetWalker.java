package com.norwood.wfcore.common.pipenet.ac.net;

import com.gregtechceu.gtceu.api.pipenet.PipeNetWalker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.capability.WFCapabilities;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlockEntity;
import com.norwood.wfcore.common.pipenet.ac.ACPipeProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Walks an AC cable run in a straight line (like laser pipes) from a source port until it finds the AC port
 * at the other end, tracking the thinnest cable along the way.
 */
public class ACNetWalker extends PipeNetWalker<ACPipeBlockEntity, ACPipeProperties, ACPipeNet> {

    public static final ACRoutePath FAILED_MARKER = new ACRoutePath(null, null, 0, 0);

    private static final Direction[] X_AXIS_FACINGS = { Direction.WEST, Direction.EAST };
    private static final Direction[] Y_AXIS_FACINGS = { Direction.UP, Direction.DOWN };
    private static final Direction[] Z_AXIS_FACINGS = { Direction.NORTH, Direction.SOUTH };

    private ACRoutePath routePath;
    private long minThroughput = Long.MAX_VALUE;
    private BlockPos sourcePipe;
    private Direction facingToHandler;
    private Direction.Axis axis;

    @Nullable
    public static ACRoutePath createNetData(ACPipeNet world, BlockPos sourcePipe, Direction faceToSourceHandler) {
        try {
            ACNetWalker walker = new ACNetWalker(world, sourcePipe, 1);
            walker.sourcePipe = sourcePipe;
            walker.facingToHandler = faceToSourceHandler;
            walker.axis = faceToSourceHandler.getAxis();
            walker.traversePipeNet();
            return walker.routePath;
        } catch (Exception e) {
            return FAILED_MARKER;
        }
    }

    protected ACNetWalker(ACPipeNet world, BlockPos sourcePipe, int distance) {
        super(world, sourcePipe, distance);
    }

    @NotNull
    @Override
    protected PipeNetWalker<ACPipeBlockEntity, ACPipeProperties, ACPipeNet> createSubWalker(ACPipeNet net,
                                                                                            Direction facingToNextPos,
                                                                                            BlockPos nextPos,
                                                                                            int walkedBlocks) {
        ACNetWalker walker = new ACNetWalker(net, nextPos, walkedBlocks);
        walker.facingToHandler = facingToHandler;
        walker.sourcePipe = sourcePipe;
        walker.axis = axis;
        return walker;
    }

    @Override
    protected Class<ACPipeBlockEntity> getBasePipeClass() {
        return ACPipeBlockEntity.class;
    }

    @Override
    protected void checkPipe(ACPipeBlockEntity pipeTile, BlockPos pos) {
        long throughput = pipeTile.getNodeData().throughput;
        ACNetWalker root = (ACNetWalker) this.root;
        if (throughput < root.minThroughput) {
            root.minThroughput = throughput;
        }
    }

    @Override
    protected Direction[] getSurroundingPipeSides() {
        return switch (axis) {
            case X -> X_AXIS_FACINGS;
            case Y -> Y_AXIS_FACINGS;
            case Z -> Z_AXIS_FACINGS;
        };
    }

    @Override
    protected void checkNeighbour(ACPipeBlockEntity pipeNode, BlockPos pipePos, Direction faceToNeighbour,
                                  @Nullable BlockEntity neighbourTile) {
        if (neighbourTile == null || (pipePos.equals(sourcePipe) && faceToNeighbour == facingToHandler)) {
            return;
        }
        ACNetWalker root = (ACNetWalker) this.root;
        if (root.routePath == null) {
            IACEnergyContainer handler = neighbourTile.getCapability(
                    WFCapabilities.CAPABILITY_AC_ENERGY, faceToNeighbour.getOpposite()).resolve().orElse(null);
            if (handler != null && handler.inputsAC(faceToNeighbour.getOpposite())) {
                long min = root.minThroughput == Long.MAX_VALUE ? 0 : root.minThroughput;
                root.routePath = new ACRoutePath(pipePos.immutable(), faceToNeighbour, getWalkedBlocks(), min);
                stop();
            }
        }
    }
}
