package com.norwood.wfcore.mixin;

import com.norwood.wfcore.config.WFCoreConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import nl.smartstreamlabs.sbwdroneconfig.DroneSpotlightSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = DroneSpotlightSystem.class, remap = false)
public abstract class DroneSpotlightSyncDedupMixin {

    @Inject(method = "setSpotlightActive", at = @At("HEAD"), cancellable = true)
    private static void wfcore$skipUnchangedSpotlightBroadcast(
        Entity drone,
        boolean active,
        ServerPlayer target,
        CallbackInfo ci
    ) {
        if (!WFCoreConfig.isSbwDroneHotPathCacheEnabled() || drone == null || target != null) {
            return;
        }

        if (DroneSpotlightSystem.isSpotlightActive(drone) == active) {
            ci.cancel();
        }
    }
}
