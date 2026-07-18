package com.norwood.wfcore.client.render.vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.WFCore;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Bakes and caches each vehicle bone's <em>bone-local</em> geometry into a retained GPU {@link VertexBuffer},
 * keyed by the GeckoLib model resource. The geometry is captured by driving GeckoLib's own
 * {@code renderCubesOfBone} at identity into a {@link BufferBuilder}, so only cube-level transforms (pivot,
 * rotation, inflate, mirror, UVs) are baked in — the bone's own animated transform is applied live at draw
 * time. That makes the cache pose-independent: one bake per model serves every instance at any turret/wheel
 * angle.
 * <p>
 * <b>Async pipeline</b> (per the worker-pool request): {@code renderCubesOfBone} reads only immutable
 * {@code GeoCube} records and writes a thread-local {@link BufferBuilder}, so the CPU mesh build runs on
 * {@link Util#backgroundExecutor()}. The resulting {@code RenderedBuffer}s are then uploaded to GPU
 * {@link VertexBuffer}s on the render thread (GL calls must stay there). While a model is baking, callers get
 * {@code null} and the bone simply tessellates as usual — no hitch, graceful fallback.
 */
public final class VehicleMeshCache {

    private VehicleMeshCache() {}

    private static final Map<ResourceLocation, ModelState> STATES = new ConcurrentHashMap<>();

    private static final class ModelState {
        static final int BAKING = 0;   // worker building CPU geometry
        static final int BUILT = 1;    // geometry ready, awaiting GPU upload on the render thread
        static final int READY = 2;    // uploaded, usable
        static final int FAILED = 3;

        volatile int status = BAKING;
        volatile Map<String, BufferBuilder.RenderedBuffer> pending; // produced off-thread
        Map<String, VertexBuffer> vbos;                             // owned by the render thread
    }

    /**
     * The bone-local buffer for a named bone of this entity's model, or {@code null} if not yet baked (the
     * caller should tessellate that bone this frame). Kicks off the async bake on first request. Render thread
     * only.
     */
    public static VertexBuffer getBone(GeoRenderer<?> renderer, VehicleEntity entity, String boneName) {
        ResourceLocation res = modelRes(renderer, entity);
        if (res == null) {
            return null;
        }
        ModelState state = STATES.get(res);
        if (state == null) {
            state = new ModelState();
            STATES.put(res, state);
            BakedGeoModel baked = bakedModel(renderer, res);
            if (baked == null || baked.topLevelBones().isEmpty()) {
                state.status = ModelState.FAILED;
                return null;
            }
            final ModelState st = state;
            final BakedGeoModel model = baked;
            final GeoRenderer<?> geoRenderer = renderer;
            Util.backgroundExecutor().execute(() -> buildAsync(res, st, model, geoRenderer));
            return null;
        }
        if (state.status == ModelState.BUILT) {
            uploadPending(state);
        }
        if (state.status == ModelState.READY) {
            return state.vbos.get(boneName);
        }
        return null;
    }

    /** Worker thread: build a {@code RenderedBuffer} for every cube-bearing bone (immutable reads only). */
    private static void buildAsync(ResourceLocation res, ModelState state, BakedGeoModel baked,
                                   GeoRenderer<?> renderer) {
        try {
            Map<String, BufferBuilder.RenderedBuffer> out = new HashMap<>();
            for (GeoBone top : baked.topLevelBones()) {
                buildBoneRec(renderer, top, out);
            }
            state.pending = out;
            state.status = ModelState.BUILT;
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore] async vehicle mesh bake failed for {}", res, t);
            state.status = ModelState.FAILED;
        }
    }

    private static void buildBoneRec(GeoRenderer<?> renderer, GeoBone bone,
                                     Map<String, BufferBuilder.RenderedBuffer> out) {
        String name = bone.getName();
        // Skip SBW's dog-tag bones (rendered specially by SBW) and empty/hidden bones.
        if (name != null && !name.endsWith("_dogTag") && !bone.isHidden() && !bone.getCubes().isEmpty()) {
            BufferBuilder builder = new BufferBuilder(512);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            // Identity pose → only the cube-level transforms are baked; the bone matrix is applied live at draw.
            // renderCubesOfBone is a stateless default (reads immutable GeoCube data), safe off the render thread.
            renderer.renderCubesOfBone(new PoseStack(), bone, builder, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            try {
                out.put(name, builder.end());
            } catch (Throwable emptyOrBroken) {
                // no vertices emitted → skip; this bone falls back to tessellation
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            buildBoneRec(renderer, child, out);
        }
    }

    /** Render thread: upload the worker's {@code RenderedBuffer}s to GPU buffers and flip the state to READY. */
    private static void uploadPending(ModelState state) {
        Map<String, BufferBuilder.RenderedBuffer> pending = state.pending;
        Map<String, VertexBuffer> vbos = new HashMap<>();
        if (pending != null) {
            for (Map.Entry<String, BufferBuilder.RenderedBuffer> entry : pending.entrySet()) {
                VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vbo.bind();
                vbo.upload(entry.getValue());
                vbos.put(entry.getKey(), vbo);
            }
            VertexBuffer.unbind();
        }
        state.vbos = vbos;
        state.pending = null;
        state.status = ModelState.READY;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceLocation modelRes(GeoRenderer<?> renderer, VehicleEntity entity) {
        try {
            GeoModel model = renderer.getGeoModel();
            return model.getModelResource((GeoAnimatable) entity);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BakedGeoModel bakedModel(GeoRenderer<?> renderer, ResourceLocation res) {
        try {
            GeoModel model = renderer.getGeoModel();
            return model.getBakedModel(res);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Frees every cached GPU buffer and clears the caches. Call on resource reload (geometry may have changed). */
    public static void invalidateAll() {
        for (ModelState state : STATES.values()) {
            if (state.vbos != null) {
                state.vbos.values().forEach(VertexBuffer::close);
            }
        }
        STATES.clear();
    }
}
