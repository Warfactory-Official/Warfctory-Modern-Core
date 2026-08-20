package com.norwood.wfcore.mixin;

import com.gregtechceu.gtceu.data.loader.GTOreLoader;

import com.norwood.wfcore.common.worldgen.transplant.VeinTransplant;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(GTOreLoader.class)
public class GTOreLoaderTransplantMixin {

    @Inject(method = "buildVeinGenerator", at = @At("TAIL"), remap = false)
    private static void wfcore$transplantNetherVeins(CallbackInfo ci) {
        VeinTransplant.registerOverworldCopies();
    }
}
