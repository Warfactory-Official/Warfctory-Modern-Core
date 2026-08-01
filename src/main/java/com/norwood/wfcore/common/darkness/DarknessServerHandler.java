package com.norwood.wfcore.common.darkness;

import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.diagnostics.DiagNet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Server half of the True Darkness enforcement: on join, push the server's authoritative {@code [trueDarkness]}
 * settings to the client so its local config cannot override them. True Darkness is a client-only render mod, so
 * this packet is the only way the server can dictate a client's darkness.
 */
public final class DarknessServerHandler {

    public static final DarknessServerHandler INSTANCE = new DarknessServerHandler();

    private DarknessServerHandler() {}

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DiagNet.sendDarknessEnforce(player, new DarknessEnforceMessage(
                WFCoreConfig.isTrueDarknessEnabled(),
                WFCoreConfig.isTrueDarknessBlockLightOnly(),
                WFCoreConfig.isTrueDarknessIgnoreMoonPhase(),
                WFCoreConfig.isTrueDarknessDarkOverworld(),
                WFCoreConfig.isTrueDarknessDarkNether(),
                WFCoreConfig.isTrueDarknessDarkEnd(),
                WFCoreConfig.isTrueDarknessDarkDefault(),
                WFCoreConfig.isTrueDarknessDarkSkyless(),
                WFCoreConfig.getTrueDarknessNetherFog(),
                WFCoreConfig.getTrueDarknessEndFog()));
    }
}
