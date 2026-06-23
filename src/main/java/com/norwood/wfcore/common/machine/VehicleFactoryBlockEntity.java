package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the vehicle factories. No animated model is wired yet (the 1.12.2 GLTF model was
 * never authored), so this is a plain meta-machine block entity; it can be driven by
 * {@link com.norwood.wfcore.client.render.gltf.GltfMachineRenderer} once a model exists.
 */
public class VehicleFactoryBlockEntity extends MetaMachineBlockEntity {

    public VehicleFactoryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }
}
