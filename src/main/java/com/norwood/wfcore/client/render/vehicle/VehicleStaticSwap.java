package com.norwood.wfcore.client.render.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.WFCore;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Strategy 1 — activity-gated static swap. Called from {@code VehicleStaticSwapMixin} at the head of Superb
 * Warfare's {@code VehicleRenderer.render}. When the vehicle is idle it draws a single baked rest-pose
 * {@link VertexBuffer} (no per-vertex tessellation) and reports {@code true} so the mixin cancels GeckoLib's
 * own render; otherwise it returns {@code false} and the vehicle renders normally.
 * <p>
 * Positioning reuses SBW's own public {@link VehicleRenderer#vehicleAxis} (which each vehicle may override), so
 * the static mesh sits exactly where the animated one would — no transform is re-derived here. The pose stack
 * arrives translated to the entity origin (untransformed), exactly as SBW's {@code render} sees it.
 */
public final class VehicleStaticSwap {

    private VehicleStaticSwap() {}

    private static volatile boolean logged;

    /**
     * @return true if the idle static mesh was drawn (the caller must cancel the stock render)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean tryRenderIdle(VehicleRenderer renderer, VehicleEntity entity, float yaw,
                                        float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!WFVehicleRenderConfig.staticSwapEnabled(entity)
                || !WFVehicleRenderConfig.rawDrawAllowed()
                || VehicleActivity.isActive(entity)) {
            return false;
        }

        VertexBuffer vbo = VehicleMeshCache.getWhole((GeoRenderer<?>) renderer, entity);
        if (vbo == null) {
            return false; // bake unavailable → let GeckoLib render
        }

        ResourceLocation texture;
        try {
            texture = ((EntityRenderer) renderer).getTextureLocation(entity);
        } catch (Throwable t) {
            return false;
        }

        if (!logged) {
            logged = true;
            WFCore.LOGGER.info("[wfcore] vehicle static-swap engaged (first idle vehicle: {})",
                    entity.getType());
        }

        Level level = entity.level();
        pose.pushPose();
        try {
            renderer.vehicleAxis(entity, pose, yaw, partialTick);
            BakedMeshRenderer.draw(vbo, pose, texture, level, entity.blockPosition());
        } finally {
            pose.popPose();
        }

        // Preserve SBW's custom parts (loaded shell, antenna, dog tags, ...) exactly as its own render does:
        // at the base pose after popPose, with the same arguments. Cheap while idle (most parts are inactive).
        try {
            renderer.renderCustomPart(entity, yaw, partialTick, pose, buffers, light);
        } catch (Throwable ignored) {
            // never let an optional custom-part failure break the frame
        }
        return true;
    }
}
