package com.norwood.wfcore.mixin;

import com.norwood.wfcore.client.FlashbackReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Jade draws its block / entity tooltip on the GUI event, which Flashback's replay and export do not hide (it only
 * swaps the game mode to spectator for the vanilla HUD), so the tooltip of whatever the replay camera points at sat
 * over the footage. {@code shouldShow} gates both the normal draw and the fade-out of the last tooltip.
 * <p>
 * Jade absent => the target class is missing and Mixin skips this mixin.
 */
@SuppressWarnings("UnresolvedMixinReference")
@Mixin(targets = "snownee.jade.overlay.OverlayRenderer", remap = false)
public class JadeReplayHideMixin {

    @Inject(method = "shouldShow", at = @At("HEAD"), cancellable = true)
    private static void wfcore$hideInReplay(CallbackInfoReturnable<Boolean> cir) {
        if (FlashbackReplay.active()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "shouldShowImmediately", at = @At("HEAD"), cancellable = true)
    private static void wfcore$hideLingerInReplay(CallbackInfoReturnable<Boolean> cir) {
        if (FlashbackReplay.active()) {
            cir.setReturnValue(false);
        }
    }
}
