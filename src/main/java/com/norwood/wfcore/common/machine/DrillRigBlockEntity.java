package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.norwood.wfcore.client.render.mask.RenderMaskManager;

/**
 * Custom block entity for the drilling rig so its animated GLTF model (drawn by
 * {@link com.norwood.wfcore.client.render.gltf.GltfMachineRenderer}) is never frustum-culled: the rig tower
 * extends well beyond the controller block, so the render bounding box is inflated. Mirrors
 * {@link RadarBlockEntity}.
 */
public class DrillRigBlockEntity extends MetaMachineBlockEntity {

    public DrillRigBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The rig spans the full ~19-block-tall structure around the controller; inflate generously so the
        // model is never frustum-culled when only part of it is on screen.
        return new AABB(getBlockPos()).inflate(48);
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
