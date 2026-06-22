package com.norwood.wfcore.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.profiling.ProfileResults;

import com.norwood.wfcore.WFCore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class GuiDebugRemover {

    @Inject(method = "renderFpsMeter", at = @At("HEAD"), cancellable = true)
    private void wfcore$renderFpsMeter(GuiGraphics guiGraphics, ProfileResults results, CallbackInfo ci) {
        if (!WFCore.DEBUG)
            ci.cancel();
    }
}
