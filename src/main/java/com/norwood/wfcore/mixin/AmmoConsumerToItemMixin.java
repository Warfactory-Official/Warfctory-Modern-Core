package com.norwood.wfcore.mixin;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer.AmmoConsumeType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables Superb Warfare's out-of-inventory player-ammo "supply" pool for hand guns.
 */
@Mixin(value = AmmoConsumer.class, remap = false)
public abstract class AmmoConsumerToItemMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void wfcore$forceInventoryAmmo(CallbackInfo ci) {
        AmmoConsumer self = (AmmoConsumer) (Object) this;
        if (self.getType() == AmmoConsumeType.PLAYER_AMMO && self.getPlayerAmmoType() != null) {
            // stack is already the ammo item's stack; ITEM path will consume it from the inventory.
            self.setType(AmmoConsumeType.ITEM);
        }
    }
}
