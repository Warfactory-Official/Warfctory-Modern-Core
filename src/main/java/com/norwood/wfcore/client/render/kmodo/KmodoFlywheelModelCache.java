package com.norwood.wfcore.client.render.kmodo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import software.bernie.geckolib.util.RenderUtils;

/**
 * Kmodo Accelerator (Flywheel path) — bakes a vehicle's geometry into Flywheel {@link Model}s, keyed by GeckoLib
 * model resource.
 * <p>
 * To avoid z-fighting between coplanar faces of different bones (which GeckoLib masks by drawing the whole model
 * in one pass, but per-bone instanced draws expose), all <em>static</em> bones (hull, decals, fixed detail) are
 * merged into a single "body" mesh baked with their bind transforms relative to the root — one draw, resolved
 * exactly like GeckoLib. Only genuinely-animated bones (turret, wheels, barrels, …) are kept as separate
 * bone-local models so they can move via per-instance transforms. A bone is treated as animated if its name (or
 * an ancestor's) matches {@link #DYNAMIC_PATTERNS}; anything else merges into the body.
 * <p>
 * Everything is baked off-thread ({@link Util#backgroundExecutor()}): {@code renderCubesOfBone} reads only
 * immutable {@code GeoCube} data, and static bones are never animated so reading their bind transforms is safe.
 * Bytes are copied from the MC {@code RenderedBuffer} into a Flywheel {@link FullVertexView} over off-heap
 * {@link MemoryBlock} memory. A per-model lock guards the shared bone tree during the visual's animate+walk.
 */
public final class KmodoFlywheelModelCache {

    private KmodoFlywheelModelCache() {}

    /** Bone-name substrings (lower-case) that mark a bone — and its subtree — as animated (kept separate). */
    private static final Set<String> DYNAMIC_PATTERNS = Set.of(
            "wheel", "track", "turret", "barrel", "cannon", "gun", "muzzle", "recoil", "rotor", "prop", "blade",
            "mantlet", "elevation", "traverse", "hatch", "rudder", "elevator", "aileron", "flap", "steer",
            "suspension", "radar", "antenna", "launcher", "missile", "gear", "swivel", "dish");

    private static final Map<ResourceLocation, ModelState> STATES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Object> LOCKS = new ConcurrentHashMap<>();

    /** The merged static body model plus the per-bone models for the animated bones of one vehicle model. */
    public static final class VehicleModels {
        public final Model body; // nullable (a vehicle with no static geometry)
        public final Map<String, Model> dynamicBones;

        VehicleModels(Model body, Map<String, Model> dynamicBones) {
            this.body = body;
            this.dynamicBones = dynamicBones;
        }
    }

    private static final class ModelState {
        static final int BAKING = 0;
        static final int READY = 1;
        static final int FAILED = 2;

        volatile int status = BAKING;
        volatile VehicleModels models;
        final List<MemoryBlock> blocks = new ArrayList<>();
    }

    /** Per-model lock so same-model vehicles serialise their shared-bone-tree animation. */
    public static Object lockFor(ResourceLocation res) {
        return LOCKS.computeIfAbsent(res, k -> new Object());
    }

    private static boolean isDynamic(String boneName) {
        if (boneName == null) {
            return false;
        }
        String name = boneName.toLowerCase(Locale.ROOT);
        for (String pattern : DYNAMIC_PATTERNS) {
            if (name.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /** The merged-body + animated-bone models for this entity's model, or {@code null} if not baked yet. */
    public static VehicleModels getModels(GeoRenderer<?> renderer, GeoVehicleEntity entity) {
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

            BufferBuilder body = new BufferBuilder(4096);
            body.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            Map<String, Model> dynamicBones = new HashMap<>();
            // Parallel vertex-count tracking for KmodoDebug (indexed by same positions as dynamicBones).
            Map<String, Integer> dynamicBoneVertCounts = new HashMap<>();
            boolean[] anyBody = {false};

            PoseStack pose = new PoseStack();
            for (GeoBone top : baked.topLevelBones()) {
                bakeWalk(renderer, pose, top, false, body, dynamicBones, material, state.blocks, anyBody,
                        dynamicBoneVertCounts);
            }

            Model bodyModel = null;
            int bodyVertices = 0;
            if (anyBody[0]) {
                BufferBuilder.RenderedBuffer rendered = body.end();
                bodyVertices = rendered.drawState().vertexCount();
                bodyModel = toModel(rendered, material, "body", state.blocks);
                rendered.release();
            }

            state.models = new VehicleModels(bodyModel, dynamicBones);
            state.status = ModelState.READY;

            // Notify KmodoDebug of the bake result (off-thread call, guarded by enabled() inside).
            if (KmodoDebug.enabled()) {
                int dynVerts = dynamicBoneVertCounts.values().stream().mapToInt(Integer::intValue).sum();
                long gpuBytes = state.blocks.stream().mapToLong(dev.engine_room.flywheel.lib.memory.MemoryBlock::size).sum();
                KmodoDebug.onFlywheelBaked(res, bodyVertices, dynamicBones.size(), dynVerts, gpuBytes);
            }
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore] Kmodo Flywheel model bake failed for {}", res, t);
            state.status = ModelState.FAILED;
        }
    }

    /**
     * Walk the bone tree: static bones (no animated ancestor and no animated name) are emitted into the shared
     * {@code body} builder at their bind transform; animated bones are baked bone-local as their own model.
     * {@code dynamicBoneVertCounts} is populated (when debug is enabled) with the vertex count for each
     * dynamic-bone model, for {@link KmodoDebug} stats.
     */
    private static void bakeWalk(GeoRenderer<?> renderer, PoseStack pose, GeoBone bone, boolean dynamicAncestor,
                                 BufferBuilder body, Map<String, Model> dynamicBones, Material material,
                                 List<MemoryBlock> blocks, boolean[] anyBody,
                                 Map<String, Integer> dynamicBoneVertCounts) {
        boolean dynamic = dynamicAncestor || isDynamic(bone.getName());
        boolean drawable = bone.getName() != null && !bone.getName().endsWith("_dogTag")
                && !bone.isHidden() && !bone.getCubes().isEmpty();

        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);

        if (dynamic) {
            if (drawable) {
                int[] vertCount = {0};
                Model model = bakeBoneLocal(renderer, bone, material, blocks, vertCount);
                if (model != null) {
                    dynamicBones.put(bone.getName(), model);
                    if (KmodoDebug.enabled()) {
                        dynamicBoneVertCounts.put(bone.getName(), vertCount[0]);
                    }
                }
            }
        } else if (drawable) {
            // Emit this static bone's cubes into the merged body mesh at its bind (accumulated) transform.
            renderer.renderCubesOfBone(pose, bone, body, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    1f, 1f, 1f, 1f);
            anyBody[0] = true;
        }

        for (GeoBone child : bone.getChildBones()) {
            bakeWalk(renderer, pose, child, dynamic, body, dynamicBones, material, blocks, anyBody,
                    dynamicBoneVertCounts);
        }
        pose.popPose();
    }

    /**
     * Bakes a single bone's cubes at identity into a Flywheel model. When {@code vertCountOut} is
     * non-null, its first element is set to the vertex count of the baked mesh (for debug tracking).
     */
    private static Model bakeBoneLocal(GeoRenderer<?> renderer, GeoBone bone, Material material,
                                       List<MemoryBlock> blocks, int[] vertCountOut) {
        try {
            BufferBuilder builder = new BufferBuilder(512);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            renderer.renderCubesOfBone(new PoseStack(), bone, builder, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            BufferBuilder.RenderedBuffer rendered = builder.end();
            if (vertCountOut != null) {
                vertCountOut[0] = rendered.drawState().vertexCount();
            }
            Model model = toModel(rendered, material, bone.getName(), blocks);
            rendered.release();
            return model;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Copies one NEW_ENTITY {@code RenderedBuffer} into a Flywheel {@link FullVertexView} → mesh → model. */
    private static Model toModel(BufferBuilder.RenderedBuffer rendered, Material material, String name,
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
            // NEW_ENTITY: pos 3f@0, color 4ub@12, uv0 2f@16, uv1(overlay) 2s@24, uv2(light) 2s@28, normal 3b@32
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

        Mesh mesh = new SimpleQuadMesh(view, "wfcore_vehicle:" + name);
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
