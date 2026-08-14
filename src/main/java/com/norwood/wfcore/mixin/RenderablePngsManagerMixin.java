package com.norwood.wfcore.mixin;

import blackoutInteractive.ema_08_.rendering.twoToThreeD.RenderablePngsManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.util.Optional;

@Mixin(value = RenderablePngsManager.class, remap = false)
public class RenderablePngsManagerMixin {

    @Inject(method = "wh", at = @At("HEAD"), cancellable = true)
    private static void patch$safeTextureDims(ResourceLocation location, CallbackInfoReturnable<int[]> cir) {
        try {
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(location);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    cir.setReturnValue(new int[]{img.getWidth(), img.getHeight()});
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        cir.setReturnValue(new int[]{1, 1});
    }
}