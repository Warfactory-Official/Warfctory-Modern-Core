package com.norwood.wfcore.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;

import com.norwood.wfcore.common.maintenance.MaintenanceService;

import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;


@Mixin(PlayerList.class)
public abstract class PlayerListMaintenanceGateMixin {

    @Shadow @Final private MinecraftServer server;

    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void wfcore$maintenanceGate(SocketAddress address, GameProfile profile,
                                        CallbackInfoReturnable<Component> cir) {
        if (MaintenanceService.isLoginRefused(this.server, profile)) {
            cir.setReturnValue(MaintenanceService.maintenanceKickMessage());
        }
    }
}
