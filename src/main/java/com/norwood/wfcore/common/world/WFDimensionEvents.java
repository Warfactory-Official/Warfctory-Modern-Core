package com.norwood.wfcore.common.world;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;


@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
public final class WFDimensionEvents {

    private WFDimensionEvents() {}

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (WFCoreConfig.isNetherDisabled()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (WFCoreConfig.isNetherDisabled() && event.getDimension() == Level.NETHER) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("wfcore.message.nether_disabled"), true);
            }
        }
    }
}
