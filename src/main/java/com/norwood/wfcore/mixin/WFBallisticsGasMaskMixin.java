package com.norwood.wfcore.mixin;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.HazardProperty;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.norwood.wfcore.integration.tacz.TaczGasMaskCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes wfballistics gas clouds ignore players who are protected against gas: those wearing a full set of
 * GregTech PPE / hazmat armor (any full PPE suit — hazmat, QuarkTech, etc.), or a working TACZ Tactical
 * Breaching gas mask.
 */
@Mixin(targets = "com.wf.wfballistics.entity.MistEntity", remap = false)
public class WFBallisticsGasMaskMixin {

    @Inject(method = "isAffectable", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wfcore$gasProtectedPlayersAreImmune(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player
                && (HazardProperty.ProtectionType.FULL.isProtected(player)
                        || TaczGasMaskCompat.hasWorkingGasMask(player))) {
            cir.setReturnValue(false);
        }
    }
}
