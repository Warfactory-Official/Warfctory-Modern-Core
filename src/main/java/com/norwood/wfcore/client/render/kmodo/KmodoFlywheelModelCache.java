package com.norwood.wfcore.client.render.kmodo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import com.norwood.wfcore.WFCore;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Kmodo Accelerator (Flywheel path) — bakes each vehicle bone's bone-local geometry into a Flywheel
 * {@link Model}, keyed by GeckoLib model resource, and caches it per model. Mirrors {@link KmodoMeshCache}: the
 * geometry is captured by driving GeckoLib's own {@code renderCubesOfBone} at identity into a NEW_ENTITY
 * {@link BufferBuilder}, so it is bone-local and pose-independent — the bone's live transform is applied by the
 * instance each frame.
 * <p>
 * The MC {@code RenderedBuffer} bytes are copied into a Flywheel {@link FullVertexView} over off-heap
 * {@link MemoryBlock} memory (Flywheel's own {@code MeshHelper} bridge is package-private), wrapped as a
 * {@link SimpleQuadMesh} + {@link SingleMeshModel} with a per-texture {@link SimpleMaterial}. All of this is pure
 * CPU/off-heap work (no GL), so it runs on {@link Util#backgroundExecutor()}; only the instancer upload (in the
 * visual) touches the GPU on the render thread.
 * <p>
 * A per-model lock ({@link #lockFor}) guards the shared GeckoLib bone tree while the visual runs
 * {@code handleAnimations} + the bone walk, because Flywheel may run visuals' {@code beginFrame} in parallel.
 */
public final class KmodoFlywheelModelCache {

    private KmodoFlywheelModelCache() {}

    private static final Map<ResourceLocation, ModelState> STATES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Object> LOCKS = new ConcurrentHashMap<>();

    private static final class ModelState {
        static final int BAKING = 0;
        static final int READY = 1;
        static final int FAILED = 2;

        volatile int status = BAKING;
        volatile Map<String, Model> models;
        final List<MemoryBlock> blocks = new ArrayList<>(); // keep the mesh backing memory alive until reload
    }

    /** Per-model lock so same-model vehicles serialise their shared-bone-tree animation; different models run free. */
    public static Object lockFor(ResourceLocation res) {
        return LOCKS.computeIfAbsent(res, k -> new Object());
    }

    /** The per-bone Flywheel models for this entity's model, or {@code null} if not baked yet. Triggers the bake. */
    public static Map<String, Model> getModels(GeoRenderer<?> renderer, GeoVehicleEntity entity) {
        ResourceLocation res = modelRes(renderer, entity);
        if (res == null) {
            return null;
        }
        ModelState state = STATES.get(res);
        if (state == null) {
            state = new ModelState();
            STATES.put(res, state);
            BakedGeoModel baked = bakedModel(renderer, res);
            ResourceLocation texture = texture(renderer, entity);
            if (baked == null || baked.topLevelBones().isEmpty() || texture == null) {
                state.status = ModelState.FAILED;
                return null;
            }
            final ModelState st = state;
            final BakedGeoModel model = baked;
            final GeoRenderer<?> geoRenderer = renderer;
            final ResourceLocation tex = texture;
            Util.backgroundExecutor().execute(() -> buildAsync(res, st, model, geoRenderer, tex));
            return null;
        }
        return state.status == ModelState.READY ? state.models : null;
    }

    /** True once this entity's model is baked and ready — the gate for suppressing the vanilla/retained render. */
    public static boolean isReady(Entity entity) {
        try {
            EntityRenderer<?> er = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(er instanceof GeoRenderer<?> renderer) || !(entity instanceof GeoVehicleEntity vehicle)) {
                return false;
            }
            ResourceLocation res = modelRes(renderer, vehicle);
            ModelState state = res == null ? null : STATES.get(res);
            return state != null && state.status == ModelState.READY;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void buildAsync(ResourceLocation res, ModelState state, BakedGeoModel baked,
                                   GeoRenderer<?> renderer, ResourceLocation texture) {
        try {
            Material material = new SimpleMaterial.Builder().copyFrom(Materials.CUTOUT_MIPPED_BLOCK)
                    .texture(texture).build();
            Map<String, Model> out = new HashMap<>();
            for (GeoBone top : baked.topLevelBones()) {
                buildBoneRec(renderer, top, out, state.blocks, material);
            }
            state.models = out;
            state.status = ModelState.READY;
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore] Kmodo Flywheel model bake failed for {}", res, t);
            state.status = ModelState.FAILED;
        }
    }

    private static void buildBoneRec(GeoRenderer<?> renderer, GeoBone bone, Map<String, Model> out,
                                     List<MemoryBlock> blocks, Material material) {
        String name = bone.getName();
        if (name != null && !name.endsWith("_dogTag") && !bone.isHidden() && !bone.getCubes().isEmpty()) {
            try {
                BufferBuilder builder = new BufferBuilder(512);
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                renderer.renderCubesOfBone(new PoseStack(), bone, builder, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
                BufferBuilder.RenderedBuffer rendered = builder.end();
                Model model = toModel(rendered, material, name, blocks);
                rendered.release();
                if (model != null) {
                    out.put(name, model);
                }
            } catch (Throwable ignored) {
                // skip this bone; it just won't be instanced
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            buildBoneRec(renderer, child, out, blocks, material);
        }
    }

    /** Copies one NEW_ENTITY {@code RenderedBuffer} into a Flywheel {@link FullVertexView} → mesh → model. */
    private static Model toModel(BufferBuilder.RenderedBuffer rendered, Material material, String boneName,
                                 List<MemoryBlock> blocks) {
        BufferBuilder.DrawState draw = rendered.drawState();
        int count = draw.vertexCount();
        if (count == 0) {
            return null;
        }
        int stride = draw.format().getVertexSize();
        ByteBuffer bytes = rendered.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        int origin = bytes.position();

        MemoryBlock memory = MemoryBlock.mallocTracked(FullVertexView.STRIDE * count);
        blocks.add(memory);
        FullVertexView view = new FullVertexView();
        view.ptr(memory.ptr());
        view.vertexCount(count);

        for (int i = 0; i < count; i++) {
            int base = origin + i * stride;
            // NEW_ENTITY layout: pos 3f@0, color 4ub@12, uv0 2f@16, uv1(overlay) 2s@24, uv2(light) 2s@28, normal 3b@32
            view.x(i, bytes.getFloat(base));
            view.y(i, bytes.getFloat(base + 4));
            view.z(i, bytes.getFloat(base + 8));
            view.r(i, (bytes.get(base + 12) & 0xFF) / 255f);
            view.g(i, (bytes.get(base + 13) & 0xFF) / 255f);
            view.b(i, (bytes.get(base + 14) & 0xFF) / 255f);
            view.a(i, (bytes.get(base + 15) & 0xFF) / 255f);
            view.u(i, bytes.getFloat(base + 16));
            view.v(i, bytes.getFloat(base + 20));
            view.overlay(i, (bytes.getShort(base + 24) & 0xFFFF) | ((bytes.getShort(base + 26) & 0xFFFF) << 16));
            view.light(i, (bytes.getShort(base + 28) & 0xFFFF) | ((bytes.getShort(base + 30) & 0xFFFF) << 16));
            view.normalX(i, bytes.get(base + 32) / 127f);
            view.normalY(i, bytes.get(base + 33) / 127f);
            view.normalZ(i, bytes.get(base + 34) / 127f);
        }

        Mesh mesh = new SimpleQuadMesh(view, "wfcore_vehicle_bone:" + boneName);
        return new SingleMeshModel(mesh, material);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceLocation modelRes(GeoRenderer<?> renderer, GeoVehicleEntity entity) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceLocation texture(GeoRenderer<?> renderer, GeoVehicleEntity entity) {
        try {
            return ((EntityRenderer) renderer).getTextureLocation(entity);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Frees the off-heap mesh memory and clears the caches. Call on resource reload. */
    public static void invalidateAll() {
        for (ModelState state : STATES.values()) {
            for (MemoryBlock block : state.blocks) {
                if (!block.isFreed()) {
                    block.free();
                }
            }
        }
        STATES.clear();
        LOCKS.clear();
    }
}
