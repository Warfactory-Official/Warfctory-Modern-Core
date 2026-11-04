package com.norwood.wfcore.integration.warforge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import com.flansmod.warforge.api.WarforgeAPI;
import com.norwood.wfcore.integration.wfweight.WeightGate;

/** Behind {@link WarforgeIntegration#isLoaded()}. */
public final class WarforgeFobGuard {

    private WarforgeFobGuard() {}

    public static void install() {
        WarforgeAPI.addFobWarpVeto((player, fob) -> WeightGate.isOverEncumbered(player)
                ? Component.literal("You are too overencumbered to warp.").withStyle(ChatFormatting.RED) : null);
    }
}
