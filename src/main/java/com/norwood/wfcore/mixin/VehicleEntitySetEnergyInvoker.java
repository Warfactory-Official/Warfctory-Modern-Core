package com.norwood.wfcore.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = VehicleEntity.class, remap = false)
public interface VehicleEntitySetEnergyInvoker {
    @Invoker("setEnergy")
    void wfcore$invokeSetEnergy(int energy);
}
