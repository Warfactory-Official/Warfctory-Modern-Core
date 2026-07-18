package com.norwood.wfcore.client.render.kmodo;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;

/**
 * Kmodo Accelerator (Flywheel path) — client Forge-bus hook that runs the visualizer registration lazily once
 * the entity renderer map is populated. Registration must happen after {@code EntityRenderersEvent.RegisterRenderers}
 * (so the dispatcher's renderer map exists), not from client setup where the ordering isn't guaranteed; the first
 * render stages satisfy that. {@link KmodoFlywheelRegistrar#ensureRegistered()} is idempotent and retries until
 * the map is ready.
 */
@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KmodoFlywheelEvents {

    private KmodoFlywheelEvents() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            KmodoFlywheelRegistrar.ensureRegistered();
        }
    }
}
