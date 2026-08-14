package com.norwood.wfcore.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.norwood.wfcore.config.WFCoreConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nl.smartstreamlabs.sbwdroneconfig.SbwItemStackingRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = SbwItemStackingRules.class, remap = false)
public abstract class SbwStackingRuleLookupCacheMixin {

    @Unique
    private static final Map<Item, Boolean> WFCORE$IS_STACKABLE = new ConcurrentHashMap<>();

    @Inject(method = "isSupportedStackableStack", at = @At("HEAD"), cancellable = true)
    private static void wfcore$serveCachedStackingRule(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!WFCoreConfig.isSbwDroneHotPathCacheEnabled() || stack == null || stack.isEmpty()) {
            return;
        }

        Boolean cached = WFCORE$IS_STACKABLE.get(stack.getItem());
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "isSupportedStackableStack", at = @At("RETURN"))
    private static void wfcore$recordStackingRule(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!WFCoreConfig.isSbwDroneHotPathCacheEnabled() || stack == null || stack.isEmpty()) {
            return;
        }

        WFCORE$IS_STACKABLE.putIfAbsent(stack.getItem(), cir.getReturnValue());
    }
}
