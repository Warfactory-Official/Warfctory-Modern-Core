package com.norwood.wfcore.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.norwood.wfcore.common.data.WFTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Blocks GregTech crowbars from picking up SBW vehicles unless the vehicle's entity type is in the
 * {@code wfcore:gt_crowbar_pickup_allowed} tag.
 *
 * <p>Retargeted for the rolling SBW snapshot: 0.8.9-final handled crowbar pickup inline in
 * {@code VehicleEntity.interact} (this used to be a {@code @Redirect} on the 2nd {@code ItemStack.is(TagKey)}
 * call there). The rolling snapshot extracted that logic into the {@code open fun onCrowbarInteract} hook,
 * which {@code interact} only calls when the held stack is already in {@code ModTags.Items.TOOLS_CROWBAR}.
 * We inject at its head and cancel (return {@code null} == "no crowbar result, fall through, no pickup") when
 * the stack is specifically a GregTech crowbar and this vehicle type is not pickup-allowed.
 */
@Mixin(VehicleEntity.class)
public abstract class VehicleCrowbarPickupMixin {

    @Inject(method = "onCrowbarInteract", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$blockGtCrowbarVehiclePickup(ItemStack stack, Player player, InteractionHand hand,
                                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (GTToolType.CROWBAR.is(stack)) {
            Entity self = (Entity) (Object) this;
            if (!self.getType().is(WFTags.EntityTypes.GT_CROWBAR_PICKUP_ALLOWED)) {
                cir.setReturnValue(null);
            }
        }
    }
}
