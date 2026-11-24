package com.norwood.wfcore.common.pipenet.ac.net;

import com.gregtechceu.gtceu.api.pipenet.PipeNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import com.norwood.wfcore.common.pipenet.ac.ACPipeProperties;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ACPipeNet extends PipeNet<ACPipeProperties> {

    // invalidated by replacement: a walk that began before the clear writes into the dead map
    private volatile Map<BlockPos, ACRoutePath> netData = new ConcurrentHashMap<>();

    public ACPipeNet(LevelACPipeNet world) {
        super(world);
    }

    @Nullable
    public ACRoutePath getNetData(BlockPos pipePos, Direction facing) {
        Map<BlockPos, ACRoutePath> cache = netData;
        ACRoutePath data = cache.get(pipePos);
        if (data == null) {
            data = ACNetWalker.createNetData(this, pipePos, facing);
            if (data == ACNetWalker.FAILED_MARKER) {
                return null;
            }
            cache.put(pipePos, data);
        }
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        netData = new ConcurrentHashMap<>();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        netData = new ConcurrentHashMap<>();
    }

    @Override
    protected void writeNodeData(ACPipeProperties nodeData, CompoundTag tagCompound) {
        tagCompound.putLong("throughput", nodeData.throughput);
    }

    @Override
    protected ACPipeProperties readNodeData(CompoundTag tagCompound) {
        return new ACPipeProperties(tagCompound.getLong("throughput"));
    }
}
