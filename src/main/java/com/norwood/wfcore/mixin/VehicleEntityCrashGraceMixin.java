package com.norwood.wfcore.mixin;

import net.minecraft.world.damagesource.DamageSource;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.norwood.wfcore.antistall.AircraftAntiStall;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;



@Mixin(VehicleEntity.class)
public abstract class VehicleEntityCrashGraceMixin {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void wfcore$forgiveCrashDuringLinkLoss(DamageSource source, float amount,
                                                   CallbackInfoReturnable<Boolean> cir) {
        VehicleEntity self = (VehicleEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (!source.is(ModDamageTypes.VEHICLE_STRIKE)) {
            return;
        }
        if (AircraftAntiStall.inCrashGrace(self)) {
            cir.setReturnValue(false);
        }
    }
}
