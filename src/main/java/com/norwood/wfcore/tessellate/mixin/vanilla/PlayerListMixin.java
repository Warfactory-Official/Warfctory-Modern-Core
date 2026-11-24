package com.norwood.wfcore.tessellate.mixin.vanilla;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** {@code broadcastAll}/{@code getPlayers} iterate on workers while login/logout mutate on the server thread. */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Shadow
    @Mutable
    @Final
    private List<ServerPlayer> players;

    @Shadow
    @Mutable
    @Final
    private List<ServerPlayer> playersView;

    @Shadow
    @Mutable
    @Final
    private Map<UUID, ServerPlayer> playersByUUID;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$concurrentPlayers(CallbackInfo ci) {
        players = new CopyOnWriteArrayList<>();
        playersView = Collections.unmodifiableList(players);
        playersByUUID = new ConcurrentHashMap<>();
    }
}
