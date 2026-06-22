package com.norwood.wfcore.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import com.norwood.wfcore.WFCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeGui.class, remap = false)
public class ForgeDebugTextRemover {

    @Inject(method = "renderHUDText", at = @At("HEAD"), cancellable = true)
    private void wfcore$renderHUDText(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!WFCore.DEBUG)
            ci.cancel();
    }

    @Inject(method = "renderFPSGraph", at = @At("HEAD"), cancellable = true)
    private void wfcore$renderFPSGraph(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (!WFCore.DEBUG)
            ci.cancel();
    }
}
