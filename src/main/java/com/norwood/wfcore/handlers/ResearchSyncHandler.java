package com.norwood.wfcore.handlers;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.net.WFNet;


@EventBusSubscriber(modid = WFCore.MOD_ID)
public final class ResearchSyncHandler {

    private ResearchSyncHandler() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            WFNet.sendResearchRegistry(player);
        } else {
            for (ServerPlayer online : event.getPlayerList().getPlayers()) {
                WFNet.sendResearchRegistry(online);
            }
        }
    }
}
