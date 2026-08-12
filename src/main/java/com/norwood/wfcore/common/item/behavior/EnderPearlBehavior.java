package com.norwood.wfcore.common.item.behavior;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;



@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
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
