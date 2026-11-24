package com.norwood.wfcore.tessellate.mixin.vanilla;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** {@code /bossbar add/remove} registry. */
@Mixin(CustomBossEvents.class)
public abstract class BossEventsMixin {

    @Shadow
    @Mutable
    @Final
    private Map<ResourceLocation, CustomBossEvent> events;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$concurrentEvents(CallbackInfo ci) {
        events = new ConcurrentHashMap<>();
    }
}
