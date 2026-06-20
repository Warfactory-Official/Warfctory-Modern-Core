package com.norwood.wfcore.common.pipenet.ac.net;

import com.gregtechceu.gtceu.api.pipenet.IAttachData;
import com.gregtechceu.gtceu.api.pipenet.IRoutePath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.capability.WFCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A resolved point-to-point route from one AC port to the AC port at the other end of a cable run. */
public class ACRoutePath implements IRoutePath<IACEnergyContainer>, IAttachData {

    private final BlockPos targetPipePos;
    private final Direction targetFacing;
    private final int distance;
    private final long minThroughput;
    byte connections;

    public ACRoutePath(BlockPos targetPipePos, Direction targetFacing, int distance, long minThroughput) {
        this.targetPipePos = targetPipePos;
        this.targetFacing = targetFacing;
        this.distance = distance;
        this.minThroughput = minThroughput;
    }

    @Override
    public BlockPos getTargetPipePos() {
        return targetPipePos;
    }

    @NotNull
    @Override
    public Direction getTargetFacing() {
        return targetFacing;
    }

    @Override
    public int getDistance() {
        return distance;
    }

    /** Thinnest cable along the run - the run can carry no more than this. */
    public long getMinThroughput() {
        return minThroughput;
    }

    @Nullable
    @Override
    public IACEnergyContainer getHandler(Level level) {
        BlockEntity be = level.getBlockEntity(targetPipePos.relative(targetFacing));
        if (be == null) return null;
        return be.getCapability(WFCapabilities.CAPABILITY_AC_ENERGY, targetFacing.getOpposite()).resolve().orElse(null);
    }

    @Override
    public boolean canAttachTo(Direction side) {
        return (connections & (1 << side.ordinal())) != 0 && side.getAxis() == this.targetFacing.getAxis();
    }

    @Override
    public boolean setAttached(Direction side, boolean attach) {
        var result = canAttachTo(side);
        if (result != attach) {
            if (attach) {
                connections |= (1 << side.ordinal());
            } else {
                connections &= ~(1 << side.ordinal());
            }
        }
        return result != attach;
    }
}
