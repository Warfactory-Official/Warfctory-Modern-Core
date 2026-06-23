package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.norwood.wfcore.client.render.mask.RenderMaskManager;

/**
 * Custom block entity for the radar so its animated GLTF dish (rendered by
 * {@link com.norwood.wfcore.client.render.gltf.GltfMachineRenderer}) is never frustum-culled: the dish
 * extends far beyond the controller block, so the render bounding box is infinite.
 */
public class RadarBlockEntity extends MetaMachineBlockEntity {

    public RadarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The dish spans the full ~47-block structure above the controller; inflate generously so the
        // model is never frustum-culled when only its top is on screen.
        return new AABB(getBlockPos()).inflate(64);
    }

    @Override
    public void setRemoved() {
        // Drop the render mask when the controller is broken or its chunk unloads (the BER, which would
        // otherwise unregister it, stops being called once this block entity is gone).
        if (level != null && level.isClientSide) {
            RenderMaskManager.removeDisableModel(getBlockPos());
        }
        super.setRemoved();
    }
}
