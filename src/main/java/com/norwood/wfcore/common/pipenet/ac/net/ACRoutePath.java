package com.norwood.wfcore.common.pipenet.ac.net;

import com.gregtechceu.gtceu.api.pipenet.IRoutePath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.capability.WFCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A resolved point-to-point route from one AC port to the AC port at the other end of a cable run.
 *
 * <p>
 * GregTech 8.0.0's pipenet dropped {@code IAttachData}; its own route paths (ItemRoutePath,
 * EnergyRoutePath, OpticalRoutePath) implement {@link IRoutePath} alone. The connection bitmask this
 * class carried existed only to satisfy that interface — nothing in wfcore ever read it — so it is gone
 * with the interface rather than reimplemented.
 */
public class ACRoutePath implements IRoutePath<IACEnergyContainer> {

    private final BlockPos targetPipePos;
    private final Direction targetFacing;
    private final int distance;
    private final long minThroughput;

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
        // NeoForge resolves capabilities through the level by position rather than off the BlockEntity.
        return level.getCapability(WFCapabilities.CAPABILITY_AC_ENERGY,
                targetPipePos.relative(targetFacing), targetFacing.getOpposite());
    }
}
