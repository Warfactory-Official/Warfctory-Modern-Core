package com.norwood.wfcore.mixin;

import java.util.Map;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.config.WFCoreConfig;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleGunDataMapCacheMixin {

    @Unique
    private Map<String, GunData> wfcore$gunDataMapCache;
    @Unique
    private long wfcore$gunDataMapCacheTick = Long.MIN_VALUE;

    @Unique
    private boolean wfcore$gunDataCacheUsable() {
        Entity self = (Entity) (Object) this;
        return WFCoreConfig.isVehicleGunDataCacheEnabled() && !self.level().isClientSide();
    }

    @Inject(method = "getGunDataMap", at = @At("HEAD"), cancellable = true)
    private void wfcore$serveCachedGunDataMap(CallbackInfoReturnable<Map<String, GunData>> cir) {
        if (!wfcore$gunDataCacheUsable()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        if (wfcore$gunDataMapCache != null && wfcore$gunDataMapCacheTick == self.level().getGameTime()) {
            cir.setReturnValue(wfcore$gunDataMapCache);
        }
    }

    @Inject(method = "getGunDataMap", at = @At("RETURN"))
    private void wfcore$storeGunDataMap(CallbackInfoReturnable<Map<String, GunData>> cir) {
        if (!wfcore$gunDataCacheUsable()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        wfcore$gunDataMapCache = cir.getReturnValue();
        wfcore$gunDataMapCacheTick = self.level().getGameTime();
    }

    @Inject(method = "setGunDataMap", at = @At("TAIL"))
    private void wfcore$invalidateGunDataMap(Map<String, GunData> value, CallbackInfo ci) {
        wfcore$gunDataMapCache = null;
        wfcore$gunDataMapCacheTick = Long.MIN_VALUE;
    }
}
