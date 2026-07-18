package com.norwood.wfcore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mixin(value = GeoEntityRenderer.class, remap = false)
public interface GeoEntityRendererAccessor {

    @Accessor("scaleWidth")
    float wfcore$getScaleWidth();

    @Accessor("scaleHeight")
    float wfcore$getScaleHeight();
}
