package com.norwood.wfcore.client.render.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.WFCore;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Strategy 2 — retained per-bone draw of <em>active</em> vehicles. Called from {@code GeoCubeRetainMixin}, which
 * redirects the {@code renderCubesOfBone} call inside GeckoLib's {@code renderRecursively}. When the animatable
 * is an allow-listed, active vehicle, this draws the bone's cached bone-local {@link VertexBuffer} using the
 * pose stack GeckoLib has already transformed for this bone (so the live animation matrix is applied on the
 * GPU, with no per-vertex CPU tessellation). Otherwise it reports {@code false} and the mixin runs the original
 * tessellating call.
 * <p>
 * Idle vehicles are intentionally excluded here — they are handled earlier and more cheaply by Strategy 1,
 * which short-circuits before GeckoLib ever recurses the bone tree.
 */
public final class VehicleBoneRetain {

    private VehicleBoneRetain() {}

    private static volatile boolean logged;

    /**
     * @return true if the bone's retained buffer was drawn (the caller must skip the original tessellation)
     */
    @SuppressWarnings({"rawtypes"})
    public static boolean drawBone(GeoRenderer<?> renderer, Entity animatable, PoseStack pose, GeoBone bone) {
        if (!(animatable instanceof VehicleEntity vehicle)) {
            return false;
        }
        if (!WFVehicleRenderConfig.perBoneEnabled(vehicle)
                || !WFVehicleRenderConfig.rawDrawAllowed()
                || !VehicleActivity.isActive(vehicle)) {
            return false;
        }

        VertexBuffer vbo = VehicleMeshCache.getBone(renderer, vehicle, bone.getName());
        if (vbo == null) {
            return false;
        }

        ResourceLocation texture;
        try {
            texture = ((EntityRenderer) renderer).getTextureLocation(vehicle);
        } catch (Throwable t) {
            return false;
        }

        if (!logged) {
            logged = true;
            WFCore.LOGGER.info("[wfcore] vehicle per-bone retained draw engaged (first: {})", vehicle.getType());
        }

        BakedMeshRenderer.draw(vbo, pose, texture, vehicle.level(), vehicle.blockPosition());
        return true;
    }
}
