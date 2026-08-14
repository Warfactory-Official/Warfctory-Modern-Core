package com.norwood.wfcore.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import com.norwood.wfcore.antistall.PilotLink;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketPilotLinkMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V",
            at = @At("HEAD"))
    private void wfcore$stampMovePlayer(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer sender = this.player;
        if (sender != null) {
            PilotLink.heard(sender.getUUID());
        }
    }

    @Inject(method = "handleMoveVehicle(Lnet/minecraft/network/protocol/game/ServerboundMoveVehiclePacket;)V",
            at = @At("HEAD"))
    private void wfcore$stampMoveVehicle(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        ServerPlayer sender = this.player;
        if (sender != null) {
            PilotLink.heard(sender.getUUID());
        }
    }
}
