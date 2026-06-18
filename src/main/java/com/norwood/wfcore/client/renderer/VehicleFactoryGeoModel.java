package com.norwood.wfcore.client.renderer;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.machine.VehicleFactoryBlockEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * Points GeckoLib at the vehicle factory's hand-authored model/texture/animation assets.
 */
public class VehicleFactoryGeoModel extends GeoModel<VehicleFactoryBlockEntity> {

    @Override
    public ResourceLocation getModelResource(VehicleFactoryBlockEntity animatable) {
        return WFCore.id("geo/vehicle_factory.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(VehicleFactoryBlockEntity animatable) {
        return WFCore.id("textures/block/vehicle_factory.png");
    }

    @Override
    public ResourceLocation getAnimationResource(VehicleFactoryBlockEntity animatable) {
        return WFCore.id("animations/vehicle_factory.animation.json");
    }
}
