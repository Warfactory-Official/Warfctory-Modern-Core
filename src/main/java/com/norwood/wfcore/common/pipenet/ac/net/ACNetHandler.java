package com.norwood.wfcore.common.pipenet.ac.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The per-side AC capability a cable exposes; routes pushed energy to the AC port at the other end. */
public class ACNetHandler implements IACEnergyContainer {

    private ACPipeNet net;
    private final ACPipeBlockEntity pipe;
    private final Direction facing;
    private final Level world;

    public ACNetHandler(ACPipeNet net, @NotNull ACPipeBlockEntity pipe, @Nullable Direction facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
        this.world = pipe.getLevel();
    }

    public void updateNetwork(ACPipeNet net) {
        this.net = net;
    }

    public ACPipeNet getNet() {
        return net;
    }

    @Nullable
    private ACRoutePath getRoute() {
        if (net == null || pipe == null || pipe.isInValid() || facing == null || pipe.isBlocked(facing)) {
            return null;
        }
        return net.getNetData(pipe.getPipePos(), facing);
    }

    private void setPipesActive() {
        if (net == null) return;
        for (BlockPos pos : net.getAllNodes().keySet()) {
            if (world.getBlockEntity(pos) instanceof ACPipeBlockEntity acPipe) {
                acPipe.setActive(true, 100);
            }
        }
    }

    @Override
    public long acceptEnergy(Direction side, long amount) {
        ACRoutePath route = getRoute();
        if (route == null) return 0;
        IACEnergyContainer handler = route.getHandler(world);
        if (handler == null) return 0;
        long limit = Math.min(amount, Math.min(getThroughput(), route.getMinThroughput()));
        if (limit <= 0) return 0;
        long accepted = handler.acceptEnergy(route.getTargetFacing().getOpposite(), limit);
        if (accepted > 0) setPipesActive();
        return accepted;
    }

    @Override
    public long getThroughput() {
        return pipe.getNodeData().throughput;
    }

    @Override
    public boolean inputsAC(Direction side) {
        return true;
    }

    @Override
    public boolean outputsAC(Direction side) {
        return true;
    }
}
