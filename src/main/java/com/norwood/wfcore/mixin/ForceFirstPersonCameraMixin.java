package com.norwood.wfcore.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import com.norwood.wfcore.common.camera.ThirdPersonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Options.class)
public abstract class ForceFirstPersonCameraMixin {

    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    private void wfcore$forceFirstPerson(CallbackInfoReturnable<CameraType> cir) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (ThirdPersonPolicy.forcesFirstPerson(player != null && player.isPassenger())) {
            cir.setReturnValue(CameraType.FIRST_PERSON);
        }
        // Otherwise the server allows third person (/wfcore_thirdperson), or the player is riding an entity: let the
        // real (user-selected) camera type through.
    }
}
