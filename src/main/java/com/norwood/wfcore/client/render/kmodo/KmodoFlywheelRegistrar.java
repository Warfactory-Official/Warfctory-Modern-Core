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

public final class KmodoFlywheelRegistrar {

    private KmodoFlywheelRegistrar() {}

    private static final Set<EntityType<?>> REGISTERED = new HashSet<>();
    private static boolean done;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean ensureRegistered() {
        if (done) {
            return false;
        }
        Object dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Map<EntityType<?>, EntityRenderer<?>> renderers =
                ((EntityRenderDispatcherAccessor) dispatcher).wfcore$getRenderers();
        if (renderers == null || renderers.isEmpty()) {
            return false;
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
            return true;
        }
        return false;
    }
}
