package com.norwood.wfcore.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fully disables the third-person camera by forcing {@link Options#getCameraType()} to always report
 * {@link CameraType#FIRST_PERSON}. Every code path that reads the camera type - vanilla world rendering,
 * the F5 toggle's own before/after comparison in {@code Minecraft#handleKeybinds}, and third-party mods -
 * therefore sees first person, so the view can never leave first person no matter what value gets stored
 * by {@code setCameraType}.
 *
 * <p>This is intentionally global (as requested): it also forces first person during ReplayMod replays
 * and spectator mode. If gameplay-only behaviour is ever wanted instead, neutralise the perspective
 * keybind in {@code Minecraft#handleKeybinds} rather than overriding this getter.
 */
@Mixin(Options.class)
public abstract class ForceFirstPersonCameraMixin {

    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    private void wfcore$forceFirstPerson(CallbackInfoReturnable<CameraType> cir) {
        cir.setReturnValue(CameraType.FIRST_PERSON);
    }
}
