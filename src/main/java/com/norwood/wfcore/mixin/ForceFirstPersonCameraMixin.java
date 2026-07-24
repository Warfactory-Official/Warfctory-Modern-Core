package com.norwood.wfcore.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Options.class)
public abstract class ForceFirstPersonCameraMixin {

    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    private void wfcore$forceFirstPerson(CallbackInfoReturnable<CameraType> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isPassenger()) {
            cir.setReturnValue(CameraType.FIRST_PERSON);
        }
        // Otherwise the player is riding an entity: let the real (user-selected) camera type through.
    }
}
