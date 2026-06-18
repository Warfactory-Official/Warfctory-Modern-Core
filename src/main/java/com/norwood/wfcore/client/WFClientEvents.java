package com.norwood.wfcore.client;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.renderer.RadarGeoRenderer;
import com.norwood.wfcore.client.renderer.VehicleFactoryGeoRenderer;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.machine.RadarBlockEntity;
import com.norwood.wfcore.common.machine.VehicleFactoryBlockEntity;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WFClientEvents {

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                (BlockEntityType<RadarBlockEntity>) WFMachines.RADAR.getBlockEntityType(),
                RadarGeoRenderer::new);
        event.registerBlockEntityRenderer(
                (BlockEntityType<VehicleFactoryBlockEntity>) WFMachines.LIGHT_GROUND_VEHICLE_FACTORY
                        .getBlockEntityType(),
                VehicleFactoryGeoRenderer::new);
    }
}
