package com.norwood.wfcore.client.render.kmodo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import com.norwood.wfcore.WFCore;
import dev.engine_room.flywheel.api.backend.BackendManager;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KmodoFlywheelEvents {

    private KmodoFlywheelEvents() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        KmodoFlywheelRegistrar.ensureRegistered();

        if (!KmodoConfig.flywheelEnabled() || !BackendManager.isBackendOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof GeoVehicleEntity vehicle) {
                EntityRenderer<?> er = mc.getEntityRenderDispatcher().getRenderer(vehicle);
                if (er instanceof GeoRenderer<?> renderer) {
                    KmodoFlywheelModelCache.getModels(renderer, vehicle);
                }
            }
        }
    }
}
