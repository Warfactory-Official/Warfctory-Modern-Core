package com.norwood.wfcore.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-disables eating and drinking at the source.
 */
@Mixin(ItemStack.class)
public abstract class ConsumableUseMixin {

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void wfcore$blockEatingAndDrinking(Level level, LivingEntity entity,
                                               CallbackInfoReturnable<ItemStack> cir) {
        ItemStack self = (ItemStack) (Object) this;
        UseAnim anim = self.getUseAnimation();
        if (anim == UseAnim.EAT || anim == UseAnim.DRINK) {
            cir.setReturnValue(self);
        }
    }
}
