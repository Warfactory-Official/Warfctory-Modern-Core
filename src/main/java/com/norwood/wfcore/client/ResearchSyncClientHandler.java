package com.norwood.wfcore.client;

import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.api.research.ResearchCategoryRegistry;
import com.norwood.wfcore.api.research.ResearchRegistry;


@EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
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
