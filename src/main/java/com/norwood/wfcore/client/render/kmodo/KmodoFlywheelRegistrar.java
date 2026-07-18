package com.norwood.wfcore.client.render.kmodo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.mixin.EntityRenderDispatcherAccessor;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.lib.visualization.SimpleEntityVisualizer;

/**
 * Kmodo Accelerator (Flywheel path) — one-time generic registration of a Flywheel visualizer for every entity
 * type whose renderer is a Superb Warfare {@code VehicleRenderer} (covers SBW and any addon that reuses the base
 * renderer, mirroring how the retained-path mixins target the single {@code VehicleRenderer} base). No hard-coded
 * vehicle list.
 * <p>
 * {@code skipVanillaRender} returns true only when Flywheel is enabled, its backend is on, AND the vehicle's
 * per-bone model is already baked — so a vehicle keeps rendering through the vanilla/Kmodo-retained path while
 * its Flywheel model bakes (sub-second) or if the bake ever fails. Flywheel's own {@code LevelRendererMixin}
 * cancels the vanilla render when this predicate is true, so no wfcore suppression mixin is needed.
 */
public final class KmodoFlywheelRegistrar {

    private KmodoFlywheelRegistrar() {}

    private static final Set<EntityType<?>> REGISTERED = new HashSet<>();
    private static boolean done;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void ensureRegistered() {
        if (done) {
            return;
        }
        Object dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Map<EntityType<?>, EntityRenderer<?>> renderers =
                ((EntityRenderDispatcherAccessor) dispatcher).wfcore$getRenderers();
        if (renderers == null || renderers.isEmpty()) {
            return; // renderer map not populated yet — retry on a later frame
        }

        int count = 0;
        for (Map.Entry<EntityType<?>, EntityRenderer<?>> entry : renderers.entrySet()) {
            EntityType<?> type = entry.getKey();
            if (REGISTERED.contains(type) || !(entry.getValue() instanceof VehicleRenderer)) {
                continue;
            }
            SimpleEntityVisualizer.builder((EntityType) type)
                    .factory((ctx, entity, partialTick) ->
                            new KmodoFlywheelVehicleVisual(ctx, (GeoVehicleEntity) entity, partialTick))
                    .skipVanillaRender(entity -> KmodoConfig.flywheelEnabled()
                            && BackendManager.isBackendOn()
                            && KmodoFlywheelModelCache.isReady((net.minecraft.world.entity.Entity) entity))
                    .apply();
            REGISTERED.add(type);
            count++;
        }

        if (count > 0) {
            done = true;
            WFCore.LOGGER.info("[wfcore] Kmodo Flywheel registered visualizers for {} vehicle type(s)", count);
        }
    }
}
