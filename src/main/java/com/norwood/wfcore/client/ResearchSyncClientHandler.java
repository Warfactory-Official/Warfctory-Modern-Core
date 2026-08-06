package com.norwood.wfcore.client;

import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.api.research.ResearchCategoryRegistry;
import com.norwood.wfcore.api.research.ResearchRegistry;


@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class ResearchSyncClientHandler {

    private ResearchSyncClientHandler() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Connection connection = event.getConnection();
        if (connection != null && connection.isMemoryConnection()) {
            return;
        }
        ResearchRegistry.clear();
        ResearchCategoryRegistry.clear();
    }
}
