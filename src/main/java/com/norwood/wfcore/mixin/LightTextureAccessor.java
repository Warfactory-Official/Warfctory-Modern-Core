package com.norwood.wfcore.mixin;

import net.minecraft.client.renderer.LightTexture;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the 16x16 lightmap pixels so a GLTF model can be lit by the world light at its own position. */
@Mixin(LightTexture.class)
public interface LightTextureAccessor {

    @Accessor("lightPixels")
    NativeImage wfcore$getLightPixels();
}
