package com.norwood.wfcore.client.render.gltf;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FacingPos;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class GltfMachineLightSampler implements ILightSampler<MetaMachineBlockEntity> {
    private final List<BlockPos> lightOffsets;

    /**
     * Offsets assume that the machine is facing North.
     * @param lightOffsets
     */
    public GltfMachineLightSampler(BlockPos... lightOffsets) {
        this.lightOffsets = List.of(lightOffsets);
    }

    @Override
    public int getLightLevel(MetaMachineBlockEntity be) {
        int light = 0;
        Level level = be.getLevel();
        BlockPos bePos = be.getBlockPos();
        Direction direction = be.getMetaMachine().getFrontFacing();
        for (BlockPos lightOffset : lightOffsets) {
            light = Math.max(LevelRenderer.getLightColor(level,bePos.offset(rotated(lightOffset,direction))),light);
        }
        return light;
    }

    private BlockPos rotated(BlockPos pos, Direction direction){
        return switch (direction) {
            case SOUTH -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case EAST -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case WEST -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
            default -> pos;
        };
    }
}
