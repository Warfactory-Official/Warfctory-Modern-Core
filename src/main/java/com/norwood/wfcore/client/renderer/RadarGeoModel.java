package com.norwood.wfcore.client.renderer;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.machine.RadarBlockEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * Points GeckoLib at the radar's hand-authored model/texture/animation assets.
 */
public class RadarGeoModel extends GeoModel<RadarBlockEntity> {

    @Override
    public ResourceLocation getModelResource(RadarBlockEntity animatable) {
        return WFCore.id("geo/radar.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RadarBlockEntity animatable) {
        return WFCore.id("textures/block/radar.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RadarBlockEntity animatable) {
        return WFCore.id("animations/radar.animation.json");
    }
}
