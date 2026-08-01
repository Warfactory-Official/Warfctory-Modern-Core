package com.norwood.wfcore.mixin;

import net.minecraft.world.entity.Entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import nl.smartstreamlabs.sbwdroneconfig.DronePayloadSystem;
import nl.smartstreamlabs.sbwdroneconfig.SbwCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes the Drone Warfare (sbwdroneconfig) anti-vehicle mortar — and any other drone payload — dealing the
 * weak "entity" damage to vehicles instead of its dedicated "vehicle" damage.
 * This entire mod is just 100s of fixes to superb warfare and its addons...
  */
@Mixin(value = DronePayloadSystem.class, remap = false)
public class DronePayloadVehicleDetectionMixin {

    @Inject(method = "isVehicleLike", at = @At("HEAD"), cancellable = true)
    private static void wfcore$detectBaseVehicle(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof VehicleEntity && !SbwCompat.isDrone(entity)) {
            cir.setReturnValue(true);
        }
    }
}
