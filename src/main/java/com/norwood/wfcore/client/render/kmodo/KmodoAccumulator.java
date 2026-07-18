package com.norwood.wfcore.client.render.kmodo;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.WFCore;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Kmodo Accelerator — collects the retained bone draws for one vehicle during GeckoLib's bone recursion, then
 * draws them all in a single render-state pass at the end of the vehicle's render.
 * <p>
 * {@code KmodoCubeRedirectMixin} calls {@link #tryRecord} in place of each {@code renderCubesOfBone}: for an
 * eligible vehicle whose bone mesh is baked, it records the bone's cached {@link VertexBuffer} together with the
 * live pose GeckoLib has already applied for that bone (so turret slew, barrels and wheels are all preserved on
 * the GPU with no per-vertex tessellation). {@code KmodoFlushMixin} then calls {@link #flush} at the end of
 * {@code VehicleRenderer.render}, so the whole vehicle costs one render-state setup, not one per bone.
 * <p>
 * Render thread only (Minecraft entity rendering is single-threaded), so the buffers are plain static lists.
 */
public final class KmodoAccumulator {

    private KmodoAccumulator() {}

    private static final List<VertexBuffer> BUFFERS = new ArrayList<>();
    private static final List<Matrix4f> MATRICES = new ArrayList<>();
    private static volatile boolean logged;
    /** The model RL of the vehicle currently being accumulated (set from tryRecord, cleared in clear). */
    private static ResourceLocation currentRes;

    /**
     * @return true if the bone's retained buffer was recorded (the caller must skip the original tessellation)
     */
    public static boolean tryRecord(GeoRenderer<?> renderer, Entity animatable, PoseStack pose, GeoBone bone) {
        if (!(animatable instanceof VehicleEntity vehicle)) {
            return false;
        }
        if (!KmodoConfig.retainEnabled() || !KmodoConfig.rawDrawAllowed()) {
            return false;
        }
        VertexBuffer vbo = KmodoMeshCache.getBone(renderer, vehicle, bone.getName());
        if (vbo == null) {
            return false; // not baked yet → tessellate this bone this frame
        }
        BUFFERS.add(vbo);
        MATRICES.add(new Matrix4f(pose.last().pose()));
        // Capture the model RL for KmodoDebug reporting (only on first bone per vehicle, rest are same model).
        if (KmodoDebug.enabled() && currentRes == null && animatable instanceof GeoAnimatable) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                GeoModel model = ((GeoRenderer) renderer).getGeoModel();
                currentRes = model.getModelResource((GeoAnimatable) animatable);
            } catch (Throwable ignored) {}
        }
        return true;
    }

    public static boolean isEmpty() {
        return BUFFERS.isEmpty();
    }

    public static void clear() {
        BUFFERS.clear();
        MATRICES.clear();
        currentRes = null;
    }

    /** Draws every recorded bone buffer for the vehicle in one render-state pass, then clears. */
    public static void flush(ResourceLocation texture, Level level, int packedLight) {
        if (BUFFERS.isEmpty()) {
            return;
        }
        if (!logged) {
            logged = true;
            WFCore.LOGGER.info("[wfcore] Kmodo Accelerator engaged — retained vehicle rendering "
                    + "({} bone buffers on first vehicle)", BUFFERS.size());
        }
        // Debug: record retained-path render before drawing (currentRes captured during tryRecord).
        if (KmodoDebug.enabled()) {
            KmodoDebug.onRetainedFlush(currentRes);
        }
        try {
            KmodoRenderer.drawBatch(BUFFERS, MATRICES, texture, level, packedLight);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore] Kmodo retained vehicle draw failed", t);
        } finally {
            clear();
        }
    }
}
