package com.norwood.wfcore.tessellate.mixin.vanilla;

import net.minecraft.server.ServerScoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** {@code trackedObjectives} is read on every score change from any region. */
@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardMixin {

    @Shadow
    @Mutable
    @Final
    @SuppressWarnings("rawtypes")
    private Set trackedObjectives;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$concurrentTracked(CallbackInfo ci) {
        trackedObjectives = ConcurrentHashMap.newKeySet();
    }
}
