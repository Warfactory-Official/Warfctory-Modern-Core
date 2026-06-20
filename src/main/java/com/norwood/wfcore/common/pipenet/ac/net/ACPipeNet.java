package com.norwood.wfcore.common.pipenet.ac.net;

import com.gregtechceu.gtceu.api.pipenet.PipeNet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

import com.norwood.wfcore.common.pipenet.ac.ACPipeProperties;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ACPipeNet extends PipeNet<ACPipeProperties> {

    private final Map<BlockPos, ACRoutePath> netData = new Object2ObjectOpenHashMap<>();

    public ACPipeNet(LevelACPipeNet world) {
        super(world);
    }

    @Nullable
    public ACRoutePath getNetData(BlockPos pipePos, Direction facing) {
        ACRoutePath data = netData.get(pipePos);
        if (data == null) {
            data = ACNetWalker.createNetData(this, pipePos, facing);
            if (data == ACNetWalker.FAILED_MARKER) {
                return null;
            }
            netData.put(pipePos, data);
        }
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        netData.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        netData.clear();
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
