package com.norwood.wfcore.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.norwood.wfcore.integration.wfweight.WeightGate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalMenuHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(targets = "appeng.menu.me.common.MEStorageMenu", remap = false)
public abstract class MEStorageMenuWirelessWithdrawMixin {

    @Shadow
    public abstract ITerminalHost getHost();

    @Inject(method = "handleNetworkInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$blockWirelessWeightedTransfer(
            ServerPlayer player, AEKey key, InventoryAction action, CallbackInfo ci) {
        if (!(getHost() instanceof WirelessTerminalMenuHost)) {
            return;
        }
        if (!wfcore$isItemTransfer(action)) {
            return;
        }
        if (key instanceof AEItemKey itemKey && WeightGate.isWeighted(itemKey.toStack())) {
            player.displayClientMessage(
                    Component.literal("§cToo heavy to move through a wireless terminal, haul it physically."),
                    true);
            ci.cancel();
        }
    }

    @Unique
    private static boolean wfcore$isItemTransfer(InventoryAction action) {
        return action == InventoryAction.PICKUP_OR_SET_DOWN
                || action == InventoryAction.SPLIT_OR_PLACE_SINGLE
                || action == InventoryAction.PICKUP_SINGLE
                || action == InventoryAction.SHIFT_CLICK
                || action == InventoryAction.MOVE_REGION
                || action == InventoryAction.CREATIVE_DUPLICATE;
    }
}
