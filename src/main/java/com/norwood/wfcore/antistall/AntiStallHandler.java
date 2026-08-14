package com.norwood.wfcore.antistall;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;



@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
public final class AntiStallHandler {

    private AntiStallHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!WFCoreConfig.isAntiStallEnabled()) {
            return;
        }
        AircraftAntiStall.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PilotLink.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PilotLink.reset();
        AircraftAntiStall.reset();
    }
}
