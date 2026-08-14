package com.norwood.wfcore.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.norwood.wfcore.config.WFCoreConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import nl.smartstreamlabs.sbwdroneconfig.SbwCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = SbwCompat.class, remap = false)
public abstract class SbwMonitorLookupCacheMixin {

    @Unique
    private static final Map<Item, Boolean> WFCORE$IS_MONITOR = new ConcurrentHashMap<>();

    @Inject(method = "isMonitor", at = @At("HEAD"), cancellable = true)
    private static void wfcore$serveCachedIsMonitor(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!WFCoreConfig.isSbwDroneHotPathCacheEnabled() || stack == null || stack.isEmpty()) {
            return;
        }

        Boolean cached = WFCORE$IS_MONITOR.get(stack.getItem());
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(method = "isMonitor", at = @At("RETURN"))
    private static void wfcore$recordIsMonitor(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!WFCoreConfig.isSbwDroneHotPathCacheEnabled() || stack == null || stack.isEmpty()) {
            return;
        }

        WFCORE$IS_MONITOR.putIfAbsent(stack.getItem(), cir.getReturnValue());
    }
}
