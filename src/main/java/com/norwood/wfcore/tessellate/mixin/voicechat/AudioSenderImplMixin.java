package com.norwood.wfcore.tessellate.mixin.voicechat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** {@code containsKey} then {@code put} stays 2 calls: same-UUID double register can both succeed (patch: {@code putIfAbsent}). */
@Mixin(targets = "de.maxhenkel.voicechat.plugins.impl.audiosender.AudioSenderImpl", remap = false)
public abstract class AudioSenderImplMixin {

    @Shadow
    @Mutable
    private static Map<UUID, Object> AUDIO_SENDERS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void wfcore$concurrentSenders(CallbackInfo ci) {
        AUDIO_SENDERS = new ConcurrentHashMap<>();
    }
}
