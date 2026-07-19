package com.norwood.wfcore.mixin;

import java.util.Map;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SBW's VehicleEntity.getGunDataMap() rebuilds the entire gun-data map on EVERY call - a new
 * {@code LinkedHashMap}, an ItemStack copy plus GunData.from() per weapon, then a defensive
 * toMap() and baseTick (plus isFiring / updateBackupAmmoCount / etc.) calls it many
 * times per tick, per vehicle.
 * Holy fucking shit I want to blow my brains out
 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleGunDataCacheMixin {

    @Shadow
    @Final
    private static EntityDataAccessor<Map<String, GunData>> GUN_DATA_MAP;

    @Unique
    private Object wfcore$cachedRawGunMap;

    @Unique
    private Map<String, GunData> wfcore$cachedGunDataMap;

    @Inject(method = "getGunDataMap", at = @At("HEAD"), cancellable = true)
    private void wfcore$returnCachedGunDataMap(CallbackInfoReturnable<Map<String, GunData>> cir) {
        Object raw = ((Entity) (Object) this).getEntityData().get(GUN_DATA_MAP);
        if (this.wfcore$cachedGunDataMap != null && raw == this.wfcore$cachedRawGunMap) {
            cir.setReturnValue(this.wfcore$cachedGunDataMap);
        }
    }

    @Inject(method = "getGunDataMap", at = @At("RETURN"))
    private void wfcore$storeCachedGunDataMap(CallbackInfoReturnable<Map<String, GunData>> cir) {
        this.wfcore$cachedRawGunMap = ((Entity) (Object) this).getEntityData().get(GUN_DATA_MAP);
        this.wfcore$cachedGunDataMap = cir.getReturnValue();
    }
}
