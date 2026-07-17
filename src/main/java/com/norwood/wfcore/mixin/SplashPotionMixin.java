package com.norwood.wfcore.mixin;

import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables splash and lingering potions at the source.
 t*/
@Mixin(ThrownPotion.class)
public abstract class SplashPotionMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void wfcore$blockPotionEffects(HitResult result, CallbackInfo ci) {
        ((ThrownPotion) (Object) this).discard();
        ci.cancel();
    }
}
