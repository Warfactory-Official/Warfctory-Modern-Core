package com.norwood.wfcore.client.renderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import com.norwood.wfcore.common.machine.VehicleFactoryBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class VehicleFactoryGeoRenderer extends GeoBlockRenderer<VehicleFactoryBlockEntity> {

    public VehicleFactoryGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(new VehicleFactoryGeoModel());
    }
}
