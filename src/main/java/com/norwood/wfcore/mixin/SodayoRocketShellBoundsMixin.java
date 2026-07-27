package com.norwood.wfcore.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;



@Mixin(targets = "com.atsuishio.superbwarfare.client.model.entity.SodayoPickUpRocketModel", remap = false)
public abstract class SodayoRocketShellBoundsMixin {

    @Redirect(
            method = "collectTransform$lambda$2",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"),
            require = 0
    )
    private static Object wfcore$wrapLoadedAmmoGet(List<?> loadedAmmo, int index) {
        int size = loadedAmmo.size();
        if (size == 0) {
            return -1;
        }
        return loadedAmmo.get(Math.floorMod(index, size));
    }
}
