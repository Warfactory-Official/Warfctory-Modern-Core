package com.norwood.wfcore.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.norwood.wfcore.integration.wfweight.WeightGate;

import com.flansmod.warforge.server.fob.FobManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(FobManager.class)
public class WarforgeFobWarpGuardMixin {

    @Inject(method = "requestFobWarp", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$blockOverencumberedWarp(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (WeightGate.isOverEncumbered(player)) {
            player.displayClientMessage(
                    Component.literal("§cYou are too overencumbered to warp — drop some weight first."), false);
            cir.setReturnValue(false);
        }
    }
}
