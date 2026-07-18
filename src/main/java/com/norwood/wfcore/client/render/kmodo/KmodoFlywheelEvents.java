package com.norwood.wfcore.client.render.kmodo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import com.norwood.wfcore.WFCore;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visualization.VisualManager;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KmodoFlywheelEvents {

    private KmodoFlywheelEvents() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        boolean justRegistered = KmodoFlywheelRegistrar.ensureRegistered();

        if (!KmodoConfig.flywheelEnabled() || !BackendManager.isBackendOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        VisualManager<Entity> entities = null;
        if (justRegistered && VisualizationManager.supportsVisualization(mc.level)) {
            VisualizationManager manager = VisualizationManager.get(mc.level);
            if (manager != null) {
                entities = manager.entities();
            }
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof GeoVehicleEntity vehicle)) {
                continue;
            }
            EntityRenderer<?> er = mc.getEntityRenderDispatcher().getRenderer(vehicle);
            if (er instanceof GeoRenderer<?> renderer) {
                KmodoFlywheelModelCache.getModels(renderer, vehicle);
            }
            if (entities != null) {
                entities.queueAdd(vehicle);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            purge();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            purge();
        }
    }

    private static void purge() {
        KmodoMeshCache.invalidateAll();
        KmodoFlywheelModelCache.invalidateAll();
        KmodoDebug.invalidateAll();
    }
}
