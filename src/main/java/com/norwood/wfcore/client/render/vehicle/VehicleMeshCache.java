package com.norwood.wfcore.client.render.vehicle;

import com.atsuishio.superbwarfare.tools.RenderDistanceHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.wfcore.WFCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;




public final class VehicleMeshCache {

    /** 12 floats per vertex: x,y,z, r,g,b,a (0-255), u,v, nx,ny,nz. */
    static final int STRIDE = 12;

    /** Drop a cached mesh this long after it was last rendered (lazy eviction; frees RAM, holds no VRAM). */
    private static final long TTL_MS = 60_000L;
    private static final long SWEEP_INTERVAL_MS = 1_000L;
    private static long lastSweepMs = 0L;

    /** A flattened vehicle mesh: vertices grouped by RenderType, plus fit info. */
    public static final class Baked {
        final Map<RenderType, float[]> byType;
        final Vec3 center;
        final double length;

        Baked(Map<RenderType, float[]> byType, Vec3 center, double length) {
            this.byType = byType;
            this.center = center;
            this.length = Math.max(length, 1.0e-3);
        }
    }

    private static final class Entry {
        final Baked baked; // null = capture failed (fall back to crate)
        long lastUsedMs;

        Entry(Baked baked, long now) {
            this.baked = baked;
            this.lastUsedMs = now;
        }
    }

    private static final Map<EntityType<?>, Entry> CACHE = new HashMap<>();

    private VehicleMeshCache() {}


    public static Baked get(ResourceLocation entityId) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (type == null) {
            return null;
        }
        // No client level yet (main menu, world load) — retry later, never cache-poison.
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        sweep(now);
        Entry e = CACHE.get(type);
        if (e != null) {
            e.lastUsedMs = now;
            return e.baked;
        }
        Baked baked = bake(type, entityId);
        CACHE.put(type, new Entry(baked, now));
        return baked;
    }

    private static void sweep(long now) {
        if (now - lastSweepMs < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweepMs = now;
        Iterator<Map.Entry<EntityType<?>, Entry>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastUsedMs > TTL_MS) {
                it.remove();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Baked bake(EntityType<?> type, ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        Entity dummy;
        try {
            dummy = type.create(mc.level);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[WF] vehicle preview: {} could not be created: {}", id, t.toString());
            return null;
        }
        if (dummy == null) {
            WFCore.LOGGER.warn("[WF] vehicle preview: {} create() returned null - using crate", id);
            return null;
        }

        EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(dummy);
        if (!(renderer instanceof GeoEntityRenderer)) {
            WFCore.LOGGER.info("[WF] vehicle preview: {} is not a GeckoLib entity - using crate", id);
            return null;
        }


        RenderDistanceHelper.markGuiRenderTimestamp();

        BakedGeoModel model;
        RenderType rt;
        try {
            GeoAnimatable ga = (GeoAnimatable) dummy;
            GeoModel geoModel = ((GeoEntityRenderer) renderer).getGeoModel();
            model = geoModel.getBakedModel(geoModel.getModelResource(ga));
            if (model == null) {
                WFCore.LOGGER.warn("[WF] vehicle preview: {} has no baked GeckoLib model - using crate", id);
                return null;
            }
            ResourceLocation tex = ((EntityRenderer) renderer).getTextureLocation(dummy);
            RenderType maybe = geoModel.getRenderType(ga, tex);
            rt = maybe != null ? maybe : RenderType.entityCutoutNoCull(tex);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[WF] vehicle preview: {} model/texture lookup threw - using crate: {}", id, t.toString());
            return null;
        }

        Capture cap = new Capture();
        try {
            PoseStack pose = new PoseStack();
            for (GeoBone bone : model.topLevelBones()) {
                walkBone(pose, bone, rt, cap, LightTexture.FULL_BRIGHT);
            }
        } catch (Throwable t) {
            WFCore.LOGGER.warn("[WF] vehicle preview: {} geometry walk threw (using partial capture): {}", id, t.toString());
        }

        Map<RenderType, float[]> verts = cap.pack();
        if (verts.isEmpty()) {
            WFCore.LOGGER.warn("[WF] vehicle preview: {} emitted no geometry - using crate", id);
            return null;
        }
        Vec3 center = new Vec3((cap.minX + cap.maxX) * 0.5, (cap.minY + cap.maxY) * 0.5, (cap.minZ + cap.maxZ) * 0.5);
        double length = Math.max(cap.maxX - cap.minX, Math.max(cap.maxY - cap.minY, cap.maxZ - cap.minZ));
        WFCore.LOGGER.info("[WF] vehicle preview: baked {}", id);
        return new Baked(verts, center, length);
    }


    private static void walkBone(PoseStack pose, GeoBone bone, RenderType rt, Capture cap, int light) {
        resetBoneToRest(bone);
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);

        if (!bone.isHidden()) {
            VertexConsumer vc = cap.getBuffer(rt);
            for (GeoCube cube : bone.getCubes()) {
                pose.pushPose();
                RenderUtils.translateToPivotPoint(pose, cube);
                RenderUtils.rotateMatrixAroundCube(pose, cube);
                RenderUtils.translateAwayFromPivotPoint(pose, cube);

                Matrix4f poseMat = pose.last().pose();
                Matrix3f normalMat = pose.last().normal();
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    Vector3f n = new Vector3f(quad.normal());
                    normalMat.transform(n);
                    RenderUtils.fixInvertedFlatCube(cube, n);
                    for (GeoVertex gv : quad.vertices()) {
                        Vector3f p = gv.position();
                        Vector4f tp = poseMat.transform(new Vector4f(p.x(), p.y(), p.z(), 1.0f));
                        vc.vertex(tp.x(), tp.y(), tp.z())
                                .color(255, 255, 255, 255)
                                .uv(gv.texU(), gv.texV())
                                .overlayCoords(OverlayTexture.NO_OVERLAY)
                                .uv2(light)
                                .normal(n.x(), n.y(), n.z())
                                .endVertex();
                    }
                }
                pose.popPose();
            }
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                walkBone(pose, child, rt, cap, light);
            }
        }
        pose.popPose();
    }

    private static void resetBoneToRest(GeoBone bone) {
        BoneSnapshot s = bone.getInitialSnapshot();
        if (s == null) {
            return;
        }
        bone.setRotX(s.getRotX());
        bone.setRotY(s.getRotY());
        bone.setRotZ(s.getRotZ());
        bone.setPosX(s.getOffsetX());
        bone.setPosY(s.getOffsetY());
        bone.setPosZ(s.getOffsetZ());
        bone.setScaleX(s.getScaleX());
        bone.setScaleY(s.getScaleY());
        bone.setScaleZ(s.getScaleZ());
    }


    private static final class Capture implements MultiBufferSource {

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        private final Map<RenderType, List<Float>> data = new IdentityHashMap<>();
        private final Map<RenderType, Recorder> recorders = new IdentityHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return recorders.computeIfAbsent(type,
                    t -> new Recorder(data.computeIfAbsent(t, k -> new ArrayList<>())));
        }

        Map<RenderType, float[]> pack() {
            Map<RenderType, float[]> packed = new IdentityHashMap<>();
            for (Map.Entry<RenderType, List<Float>> e : data.entrySet()) {
                List<Float> list = e.getValue();
                if (list.isEmpty()) {
                    continue;
                }
                int usable = (list.size() / (4 * STRIDE)) * (4 * STRIDE);
                if (usable == 0) {
                    continue;
                }
                float[] arr = new float[usable];
                for (int i = 0; i < usable; i++) {
                    arr[i] = list.get(i);
                }
                packed.put(e.getKey(), arr);
            }
            return packed;
        }

        private final class Recorder implements VertexConsumer {
            private final List<Float> out;
            private float x, y, z, r = 255, g = 255, b = 255, a = 255, u, v, nx, ny = 1, nz;

            Recorder(List<Float> out) {
                this.out = out;
            }

            @Override
            public VertexConsumer vertex(double px, double py, double pz) {
                this.x = (float) px;
                this.y = (float) py;
                this.z = (float) pz;
                if (px < minX) minX = px;
                if (py < minY) minY = py;
                if (pz < minZ) minZ = pz;
                if (px > maxX) maxX = px;
                if (py > maxY) maxY = py;
                if (pz > maxZ) maxZ = pz;
                return this;
            }

            @Override
            public VertexConsumer color(int cr, int cg, int cb, int ca) {
                this.r = cr;
                this.g = cg;
                this.b = cb;
                this.a = ca;
                return this;
            }

            @Override
            public VertexConsumer uv(float tu, float tv) {
                this.u = tu;
                this.v = tv;
                return this;
            }

            @Override
            public VertexConsumer overlayCoords(int ou, int ov) {
                return this; // dropped: items use NO_OVERLAY
            }

            @Override
            public VertexConsumer uv2(int lu, int lv) {
                return this; // dropped: item render supplies its own packed light
            }

            @Override
            public VertexConsumer normal(float dx, float dy, float dz) {
                this.nx = dx;
                this.ny = dy;
                this.nz = dz;
                return this;
            }

            @Override
            public void endVertex() {
                out.add(x);
                out.add(y);
                out.add(z);
                out.add(r);
                out.add(g);
                out.add(b);
                out.add(a);
                out.add(u);
                out.add(v);
                out.add(nx);
                out.add(ny);
                out.add(nz);
                r = g = b = a = 255;
                nx = nz = 0;
                ny = 1;
            }

            @Override
            public void defaultColor(int cr, int cg, int cb, int ca) {
            }

            @Override
            public void unsetDefaultColor() {
            }
        }
    }
}
