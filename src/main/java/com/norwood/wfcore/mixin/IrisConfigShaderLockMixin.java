package com.norwood.wfcore.mixin;

import com.norwood.wfcore.common.shader.ShaderEnforcement;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(targets = "net.irisshaders.iris.config.IrisConfig", remap = false)
public class IrisConfigShaderLockMixin {

    @Inject(method = "areShadersEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$blockShaders(CallbackInfoReturnable<Boolean> cir) {
        if (ShaderEnforcement.blockShaders()) {
            cir.setReturnValue(false);
        }
    }
}
