package com.norwood.wfcore.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import com.norwood.wfcore.common.darkness.DarknessEnforcement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "grondag.darkness.Darkness", remap = false)
public class DarknessConfigLockMixin {

    @Shadow
    static boolean darkOverworld;
    @Shadow
    static boolean darkDefault;
    @Shadow
    static boolean darkNether;
    @Shadow
    static double darkNetherFogEffective;
    @Shadow
    static double darkNetherFogConfigured;
    @Shadow
    static boolean darkEnd;
    @Shadow
    static double darkEndFogEffective;
    @Shadow
    static double darkEndFogConfigured;
    @Shadow
    static boolean darkSkyless;
    @Shadow
    static boolean blockLightOnly;
    @Shadow
    static boolean ignoreMoonPhase;

    @Inject(method = "updateLuminance", at = @At("HEAD"), remap = false)
    private static void wfcore$enforceDarkness(float tickDelta, Minecraft client, GameRenderer worldRenderer,
            float prevFlicker, CallbackInfo ci) {
        if (!DarknessEnforcement.active()) {
            return;
        }
        blockLightOnly = DarknessEnforcement.blockLightOnly();
        ignoreMoonPhase = DarknessEnforcement.ignoreMoonPhase();
        darkOverworld = DarknessEnforcement.darkOverworld();
        darkNether = DarknessEnforcement.darkNether();
        darkEnd = DarknessEnforcement.darkEnd();
        darkDefault = DarknessEnforcement.darkDefault();
        darkSkyless = DarknessEnforcement.darkSkyless();
        // Mirror computeConfigValues(): the effective fog is the configured value only while that dim is dark.
        darkNetherFogConfigured = DarknessEnforcement.darkNetherFog();
        darkEndFogConfigured = DarknessEnforcement.darkEndFog();
        darkNetherFogEffective = darkNether ? darkNetherFogConfigured : 1.0;
        darkEndFogEffective = darkEnd ? darkEndFogConfigured : 1.0;
    }
}
