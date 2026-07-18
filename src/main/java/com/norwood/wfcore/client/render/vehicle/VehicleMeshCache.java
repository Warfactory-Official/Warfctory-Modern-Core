package com.norwood.wfcore.client.render.vehicle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.norwood.wfcore.WFCore;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtils;

/**
 * Bakes and caches vehicle geometry into retained GPU {@link VertexBuffer}s, keyed by the vehicle's GeckoLib
 * model resource (so every instance of a model shares one bake).
 * <p>
 * Geometry is captured by driving GeckoLib's own {@code renderCubesOfBone} into a {@link BufferBuilder} — this
 * reuses GeckoLib's exact cube math (pivots, rotation order, inflate, mirror, UVs) instead of re-deriving it:
 * <ul>
 *   <li><b>Whole-vehicle</b> ({@link #getWhole}) walks the bone tree at rest and bakes one buffer with the
 *       rest-pose bone transforms folded in, for Strategy 1's idle draw.</li>
 *   <li><b>Per-bone</b> ({@link #getBone}) bakes each bone's cubes at identity (bone-local space) so Strategy 2
 *       can redraw a bone with its live animation matrix supplied by GeckoLib.</li>
 * </ul>
 * All access is on the render thread. Matrix tracking is forced off before a bake so the shared cached bone
 * tree does not send GeckoLib's {@code renderRecursively} down its tracking branch (which dereferences a null
 * animatable during a manual bake).
 * <p>
 * Caveat: the bake reads the shared, animated {@code BakedGeoModel}. In the rare case where another instance of
 * the same model is mid-animation when the first idle bake happens, the cached rest pose can capture that
 * instance's transient bone angles. The hull is unaffected; only a moving sub-part could freeze at a wrong
 * angle until {@link #invalidateAll()} (resource reload) rebakes.
 */
public final class VehicleMeshCache {

    private VehicleMeshCache() {}

    private static final Map<ResourceLocation, VertexBuffer> WHOLE = new HashMap<>();
    private static final Set<ResourceLocation> WHOLE_FAILED = new HashSet<>();
    private static final Map<ResourceLocation, Map<String, VertexBuffer>> PER_BONE = new HashMap<>();

    /** The whole-vehicle rest-pose buffer for this entity's model, baking it on first request. Null on failure. */
    public static VertexBuffer getWhole(GeoRenderer<?> renderer, VehicleEntity entity) {
        ResourceLocation res = modelRes(renderer, entity);
        if (res == null) {
            return null;
        }
        VertexBuffer vb = WHOLE.get(res);
        if (vb != null) {
            return vb;
        }
        if (WHOLE_FAILED.contains(res)) {
            return null;
        }
        vb = bakeWhole(renderer, res);
        if (vb == null) {
            WHOLE_FAILED.add(res);
            return null;
        }
        WHOLE.put(res, vb);
        return vb;
    }

    /** The bone-local buffer for a named bone of this entity's model, baking the whole model on first request. */
    public static VertexBuffer getBone(GeoRenderer<?> renderer, VehicleEntity entity, String boneName) {
        ResourceLocation res = modelRes(renderer, entity);
        if (res == null) {
            return null;
        }
        Map<String, VertexBuffer> map = PER_BONE.get(res);
        if (map == null) {
            map = bakePerBone(renderer, res);
            PER_BONE.put(res, map); // cache even an empty map so a failed bake is not retried every frame
        }
        return map.get(boneName);
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

    private static VertexBuffer bakeWhole(GeoRenderer<?> renderer, ResourceLocation res) {
        try {
            BakedGeoModel baked = renderer.getGeoModel().getBakedModel(res);
            if (baked == null || baked.topLevelBones().isEmpty()) {
                return null;
            }
            baked.topLevelBones().forEach(VehicleMeshCache::trackingOff);

            BufferBuilder builder = new BufferBuilder(2048);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            PoseStack pose = new PoseStack();
            for (GeoBone top : baked.topLevelBones()) {
                renderBoneRest(renderer, pose, top, builder);
            }
            return upload(builder);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore] vehicle whole-mesh bake failed for {}", res, t);
            return null;
        }
    }

    private static Map<String, VertexBuffer> bakePerBone(GeoRenderer<?> renderer, ResourceLocation res) {
        Map<String, VertexBuffer> out = new HashMap<>();
        try {
            BakedGeoModel baked = renderer.getGeoModel().getBakedModel(res);
            if (baked == null) {
                return out;
            }
            baked.topLevelBones().forEach(VehicleMeshCache::trackingOff);
            for (GeoBone top : baked.topLevelBones()) {
                bakeBoneRec(renderer, top, out);
            }
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[wfcore] vehicle per-bone bake failed for {}", res, t);
        }
        return out;
    }

    /** Recursively emit a bone's cubes with its rest-pose transform applied (whole-vehicle capture). */
    private static void renderBoneRest(GeoRenderer<?> renderer, PoseStack pose, GeoBone bone, VertexConsumer buf) {
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        if (!bone.isHidden()) {
            renderer.renderCubesOfBone(pose, bone, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    1f, 1f, 1f, 1f);
        }
        for (GeoBone child : bone.getChildBones()) {
            renderBoneRest(renderer, pose, child, buf);
        }
        pose.popPose();
    }

    /** Recursively bake each bone's cubes at identity (bone-local) into its own buffer (per-bone capture). */
    private static void bakeBoneRec(GeoRenderer<?> renderer, GeoBone bone, Map<String, VertexBuffer> out) {
        if (!bone.isHidden() && !bone.getCubes().isEmpty()) {
            try {
                BufferBuilder builder = new BufferBuilder(512);
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                renderer.renderCubesOfBone(new PoseStack(), bone, builder, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
                VertexBuffer vbo = upload(builder);
                if (vbo != null) {
                    out.put(bone.getName(), vbo);
                }
            } catch (Throwable ignored) {
                // skip this bone; it simply falls back to tessellation
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            bakeBoneRec(renderer, child, out);
        }
    }

    /** Finishes a {@link BufferBuilder} and uploads it to a static {@link VertexBuffer}; null if it was empty. */
    private static VertexBuffer upload(BufferBuilder builder) {
        BufferBuilder.RenderedBuffer rendered;
        try {
            rendered = builder.end();
        } catch (Throwable emptyOrBroken) {
            return null; // no vertices were emitted (all bones hidden / no cubes)
        }
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(rendered);
        VertexBuffer.unbind();
        return vbo;
    }

    private static void trackingOff(GeoBone bone) {
        bone.setTrackingMatrices(false);
        for (GeoBone child : bone.getChildBones()) {
            trackingOff(child);
        }
    }

    /** Frees every cached GPU buffer and clears the caches. Call on resource reload (geometry may have changed). */
    public static void invalidateAll() {
        WHOLE.values().forEach(VertexBuffer::close);
        WHOLE.clear();
        WHOLE_FAILED.clear();
        PER_BONE.values().forEach(map -> map.values().forEach(VertexBuffer::close));
        PER_BONE.clear();
    }
}
