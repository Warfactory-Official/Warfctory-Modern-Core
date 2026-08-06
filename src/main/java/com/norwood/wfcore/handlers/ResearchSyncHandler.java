package com.norwood.wfcore.handlers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.DiagNet;


@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
public final class ResearchSyncHandler {

    private ResearchSyncHandler() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) {
            DiagNet.sendResearchRegistry(player);
        } else {
            for (ServerPlayer online : event.getPlayerList().getPlayers()) {
                DiagNet.sendResearchRegistry(online);
            }
        }
    }
}
