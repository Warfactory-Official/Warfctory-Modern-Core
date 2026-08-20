package com.norwood.wfcore.common.item.behavior;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;



@EventBusSubscriber(modid = WFCore.MOD_ID)
public final class EnderPearlBehavior {

    private EnderPearlBehavior() {}

    @SubscribeEvent
    public static void onThrowPearl(PlayerInteractEvent.RightClickItem event) {
        if (!WFCoreConfig.isEnderPearlsDisabled()) return;
        if (!event.getItemStack().is(Items.ENDER_PEARL)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onPearlLand(EntityTeleportEvent.EnderPearl event) {
        if (WFCoreConfig.isEnderPearlsDisabled()) {
            event.setCanceled(true);
        }
    }
}
