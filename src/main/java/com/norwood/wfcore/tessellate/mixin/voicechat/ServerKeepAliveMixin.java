package com.norwood.wfcore.tessellate.mixin.voicechat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.UUID;

/** Keepalive timeout: reconnect, disconnect event, plugin callback hop to the server thread, FIFO order kept. */
@Mixin(targets = "de.maxhenkel.voicechat.voice.server.Server", remap = false)
public abstract class ServerKeepAliveMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @WrapOperation(method = "lambda$sendKeepAlives$3", at = @At(value = "INVOKE", target =
            "Lde/maxhenkel/voicechat/voice/server/ServerVoiceEvents;initializePlayerConnection(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void wfcore$reconnectOnMain(@Coerce Object events, ServerPlayer player, Operation<Void> original) {
        server.execute(() -> original.call(events, player));
    }

    @WrapOperation(method = "lambda$sendKeepAlives$3", at = @At(value = "INVOKE", target =
            "Lde/maxhenkel/voicechat/intercompatibility/CommonCompatibilityManager;emitServerVoiceChatDisconnectedEvent(Ljava/util/UUID;)V"))
    private void wfcore$disconnectEventOnMain(@Coerce Object manager, UUID player, Operation<Void> original) {
        server.execute(() -> original.call(manager, player));
    }

    @WrapOperation(method = "lambda$sendKeepAlives$3", at = @At(value = "INVOKE", target =
            "Lde/maxhenkel/voicechat/plugins/PluginManager;onPlayerDisconnected(Ljava/util/UUID;)V"))
    private void wfcore$disconnectCallbackOnMain(@Coerce Object manager, UUID player, Operation<Void> original) {
        server.execute(() -> original.call(manager, player));
    }
}
