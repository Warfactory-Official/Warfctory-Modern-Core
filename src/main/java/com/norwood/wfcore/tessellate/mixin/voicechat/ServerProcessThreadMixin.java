package com.norwood.wfcore.tessellate.mixin.voicechat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Voice thread => server thread for connect event + plugin callback. Player lookup stays on the voice thread. */
@Mixin(targets = "de.maxhenkel.voicechat.voice.server.Server$ProcessThread", remap = false)
public abstract class ServerProcessThreadMixin {

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target =
            "Lde/maxhenkel/voicechat/intercompatibility/CommonCompatibilityManager;emitServerVoiceChatConnectedEvent(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void wfcore$connectedEventOnMain(@Coerce Object manager, ServerPlayer player, Operation<Void> original) {
        player.server.execute(() -> original.call(manager, player));
    }

    @WrapOperation(method = "run", at = @At(value = "INVOKE", target =
            "Lde/maxhenkel/voicechat/plugins/PluginManager;onPlayerConnected(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void wfcore$connectedCallbackOnMain(@Coerce Object manager, ServerPlayer player, Operation<Void> original) {
        player.server.execute(() -> original.call(manager, player));
    }
}
