package com.norwood.wfcore.client.renderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import com.norwood.wfcore.common.machine.RadarBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class RadarGeoRenderer extends GeoBlockRenderer<RadarBlockEntity> {

    public RadarGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(new RadarGeoModel());
    }
}
