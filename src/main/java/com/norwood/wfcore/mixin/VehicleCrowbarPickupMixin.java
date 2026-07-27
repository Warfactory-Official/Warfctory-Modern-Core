package com.norwood.wfcore.mixin;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModTags;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(VehicleEntity.class)
public abstract class VehicleCrowbarPickupMixin {

    @Redirect(method = "interact",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z",
                       ordinal = 1))
    private boolean wfcore$blockGtCrowbarVehiclePickup(ItemStack stack, TagKey<Item> tag) {
        if (tag == ModTags.Items.TOOLS_CROWBAR && GTToolType.CROWBAR.is(stack)) {
            return false;
        }
        return stack.is(tag);
    }
}
