package com.norwood.wfcore.tessellate.mixin.vanilla;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** {@code /bossbar set players} + per-tick {@code removeUnseenPlayers} from different threads. */
@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin {

    @Shadow
    @Mutable
    @Final
    private Set<ServerPlayer> players;

    @Shadow
    @Mutable
    @Final
    private Set<ServerPlayer> unmodifiablePlayers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$concurrentPlayers(CallbackInfo ci) {
        players = ConcurrentHashMap.newKeySet();
        unmodifiablePlayers = Collections.unmodifiableSet(players);
    }
}
