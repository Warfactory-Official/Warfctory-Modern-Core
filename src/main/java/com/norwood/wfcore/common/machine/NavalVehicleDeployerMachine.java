package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

/**
 * Boat-dock deployer. Reuses {@link LightGroundVehicleFactoryMachine#deploy} but launches the vehicle
 * into the water instead of onto a light-concrete bed.
 *
 * <p>Geometry (from {@code boat_dock.litematic}): the controller sits in the landward wall and the harbour
 * slip — a 5-wide (right -2..2) open water channel — extends <em>behind</em> the controller in the
 * {@code -front} direction, opening to the sea at its far end. So the boat deploys behind the controller
 * (centre of the slip) and faces out toward that open mouth.
 */
public class NavalVehicleDeployerMachine extends LightGroundVehicleFactoryMachine {


    /** How far behind the controller (into the slip, {@code -front}) the boat spawns — the slip's centre. */
    private static final int SLIP_DEPTH = 6;
    /** Vertical band (relative to the controller) searched for the water surface in that column. */
    private static final int SLIP_SCAN_UP = 2;
    private static final int SLIP_SCAN_DOWN = -3;

    // Slip footprint relative to the controller, used for the "keep clear" overlay.
    private static final int SLIP_BACK_NEAR = 2;
    private static final int SLIP_BACK_FAR = 10;
    private static final int SLIP_HALF_WIDTH = 2;
    private static final int SLIP_DOWN = -1;
    private static final int SLIP_TOP = 2;

    public NavalVehicleDeployerMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    /** Face the boat out of the harbour (toward the open sea at the far {@code -front} end of the slip). */
    @Override
    public float getSpawnYaw() {
        return getFrontFacing().getOpposite().toYRot();
    }

    @Override
    @Nullable
    protected BlockPos computeSpawnPos() {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        Direction back = getFrontFacing().getOpposite();
        // Centre column of the slip: behind the controller, on its centre line.
        BlockPos centre = getBlockPos().relative(back, SLIP_DEPTH);
        // Prefer sitting on the actual water surface if the slip has been flooded.
        for (int dy = SLIP_SCAN_UP; dy >= SLIP_SCAN_DOWN; dy--) {
            BlockPos p = centre.offset(0, dy, 0);
            if (level.getFluidState(p).is(FluidTags.WATER)
                    && !level.getFluidState(p.above()).is(FluidTags.WATER)) {
                return p.above();
            }
        }
        // No water located (slip not flooded yet) — still deploy in the slip centre.
        return centre;
    }

    /** The slip is only {@value #SLIP_TOP} blocks of open air above the waterline. */
    @Override
    protected int getClearanceHeight() {
        return SLIP_TOP;
    }

    /** The harbour slip volume behind the controller — drawn as the client "keep clear" overlay. */
    @Override
    public AABB getClearanceBox() {
        BlockPos near = slipCorner(SLIP_BACK_NEAR, -SLIP_HALF_WIDTH, SLIP_DOWN);
        BlockPos far = slipCorner(SLIP_BACK_FAR, SLIP_HALF_WIDTH, SLIP_TOP);
        return new AABB(
                Math.min(near.getX(), far.getX()), Math.min(near.getY(), far.getY()), Math.min(near.getZ(), far.getZ()),
                Math.max(near.getX(), far.getX()) + 1, Math.max(near.getY(), far.getY()) + 1, Math.max(near.getZ(), far.getZ()) + 1);
    }

    /** Controller-relative offset: {@code back} into the slip, {@code right} across it, {@code up} vertically. */
    private BlockPos slipCorner(int back, int right, int up) {
        Direction backDir = getFrontFacing().getOpposite();
        Direction rightDir = RelativeDirection.RIGHT.getRelativeFacing(getFrontFacing(), getUpwardsFacing(), isFlipped());
        return getBlockPos().relative(backDir, back).relative(rightDir, right).above(up);
    }
}
