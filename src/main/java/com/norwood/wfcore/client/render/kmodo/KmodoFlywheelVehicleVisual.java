package com.norwood.wfcore.client.render.kmodo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import com.norwood.wfcore.client.render.kmodo.KmodoFlywheelModelCache.VehicleModels;
import com.norwood.wfcore.mixin.GeoEntityRendererAccessor;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtils;

@SuppressWarnings({"unchecked", "rawtypes"})
public class KmodoFlywheelVehicleVisual extends AbstractEntityVisual<GeoVehicleEntity> implements SimpleDynamicVisual {

    private static final long FNV_OFFSET = -3750763034362895579L;
    private static final long FNV_PRIME = 1099511628211L;

    private static final Map<Integer, KmodoFlywheelVehicleVisual> BY_ENTITY = new ConcurrentHashMap<>();

    private final GeoRenderer renderer;
    private final Map<String, TransformedInstance> dynamicInstances = new HashMap<>();
    private TransformedInstance bodyInstance;
    private boolean instancesCreated;

    private final KmodoDormancy dormancy = new KmodoDormancy();
    private long poseHash;

    private volatile Map<String, Matrix4f> boneLocal;
    private volatile boolean dormantFlag;
    private volatile long poseStamp;
    private volatile float scaleW = 1.0f;
    private volatile float scaleH = 1.0f;

    private long appliedStamp = Long.MIN_VALUE;
    private float appliedX;
    private float appliedY;
    private float appliedZ;
    private boolean hasApplied;

    public KmodoFlywheelVehicleVisual(VisualizationContext ctx, GeoVehicleEntity entity, float partialTick) {
        super(ctx, entity, partialTick);
        EntityRenderer<?> er = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        this.renderer = (er instanceof GeoRenderer) ? (GeoRenderer) er : null;
        BY_ENTITY.put(entity.getId(), this);
    }

    static KmodoFlywheelVehicleVisual byEntity(int entityId) {
        return BY_ENTITY.get(entityId);
    }

    public void renderThreadUpdate(float partialTick) {
        if (renderer == null || !KmodoConfig.flywheelEnabled() || !BackendManager.isBackendOn()) {
            return;
        }
        VehicleModels models = KmodoFlywheelModelCache.getModels(renderer, entity);
        if (models == null) {
            return;
        }
        ResourceLocation res = modelRes();
        if (res == null) {
            return;
        }
        final boolean prof = KmodoProfiler.enabled();
        long updateStart = prof ? System.nanoTime() : 0L;
        if (prof) {
            KmodoProfiler.countProcessed();
        }

        long dormStart = prof ? System.nanoTime() : 0L;
        boolean needsUpdate = dormancy.needsUpdate(entity, false);
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.DORMANCY, System.nanoTime() - dormStart);
            KmodoProfiler.countState(dormancy.state());
        }
        if (!needsUpdate) {
            dormantFlag = true;
            if (prof) {
                KmodoProfiler.countSkipped();
            }
            return;
        }

        GeoModel geoModel = renderer.getGeoModel();
        BakedGeoModel baked = bakedModel(res);
        if (baked == null) {
            return;
        }

        long animStart = prof ? System.nanoTime() : 0L;
        geoModel.handleAnimations(entity, renderer.getInstanceId(entity),
                new AnimationState<>(entity, 0f, 0f, partialTick, false));
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.ANIMATE, System.nanoTime() - animStart);
        }

        try {
            renderer.preRender(new PoseStack(), entity, baked, null, null, false, partialTick, 0,
                    OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            scaleW = ((GeoEntityRendererAccessor) renderer).wfcore$getScaleWidth();
            scaleH = ((GeoEntityRendererAccessor) renderer).wfcore$getScaleHeight();
        } catch (Throwable ignored) {
        }

        Set<String> dynBones = models.dynamicBones.keySet();
        Map<String, Matrix4f> local = new HashMap<>();
        poseHash = FNV_OFFSET;
        long walkStart = prof ? System.nanoTime() : 0L;
        PoseStack pose = new PoseStack();
        for (Object top : baked.topLevelBones()) {
            walkLocal(pose, (GeoBone) top, dynBones, local);
        }
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.WALK, System.nanoTime() - walkStart);
        }

        boneLocal = local;
        poseStamp++;
        dormancy.recordPose(poseHash, entity.tickCount);
        dormantFlag = dormancy.isDormant();

        if (prof) {
            KmodoProfiler.countUpdated();
            KmodoProfiler.addUpdatedTotal(System.nanoTime() - updateStart);
        }
    }

    private void walkLocal(PoseStack pose, GeoBone bone, Set<String> dynBones, Map<String, Matrix4f> out) {
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        if (dynBones.contains(bone.getName())) {
            Matrix4f m = new Matrix4f(pose.last().pose());
            out.put(bone.getName(), m);
            foldMatrix(m);
        }
        for (GeoBone child : bone.getChildBones()) {
            walkLocal(pose, child, dynBones, out);
        }
        pose.popPose();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (renderer == null || !KmodoConfig.flywheelEnabled() || !BackendManager.isBackendOn()) {
            return;
        }
        if (!isVisible(ctx.frustum())) {
            return;
        }
        final boolean prof = KmodoProfiler.enabled();
        long totalStart = prof ? System.nanoTime() : 0L;
        float partialTick = ctx.partialTick();

        long bakeStart = prof ? System.nanoTime() : 0L;
        VehicleModels models = KmodoFlywheelModelCache.getModels(renderer, entity);
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.BAKE, System.nanoTime() - bakeStart);
        }
        if (models == null) {
            return;
        }
        if (!instancesCreated) {
            if (prof) {
                KmodoProfiler.countBake();
            }
            if (models.body != null) {
                bodyInstance = instancer(models.body).createInstance();
            }
            for (Map.Entry<String, Model> e : models.dynamicBones.entrySet()) {
                dynamicInstances.put(e.getKey(), instancer(e.getValue()).createInstance());
            }
            instancesCreated = true;

            if (KmodoDebug.enabled()) {
                ResourceLocation debugRes = modelRes();
                if (debugRes != null) {
                    KmodoDebug.onFlywheelInstanceCreated(debugRes, dynamicInstances.size());
                }
            }
        }
        if (bodyInstance == null && dynamicInstances.isEmpty()) {
            return;
        }

        ResourceLocation res = modelRes();
        if (res == null) {
            return;
        }

        boolean dormant = dormantFlag;
        if (KmodoDebug.enabled()) {
            KmodoDebug.onDormancy(res, dormant);
        }

        Vector3f visualPos = getVisualPosition(partialTick);
        long stamp = poseStamp;
        if (hasApplied && stamp == appliedStamp
                && visualPos.x() == appliedX && visualPos.y() == appliedY && visualPos.z() == appliedZ) {
            if (prof) {
                KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, System.nanoTime() - totalStart);
            }
            return;
        }

        Map<String, Matrix4f> local = boneLocal;

        PoseStack pose = new PoseStack();
        pose.translate(visualPos.x(), visualPos.y(), visualPos.z());
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        ((VehicleRenderer) renderer).vehicleAxis(entity, pose, yaw, partialTick);
        pose.scale(scaleW, scaleH, scaleW);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(0.0F, 0.01F, 0.0F);
        Matrix4f root = pose.last().pose();

        if (bodyInstance != null) {
            bodyInstance.setTransform(root);
            bodyInstance.setChanged();
            if (prof) {
                KmodoProfiler.countInstances(1);
            }
        }
        if (local != null && !dynamicInstances.isEmpty()) {
            for (Map.Entry<String, TransformedInstance> e : dynamicInstances.entrySet()) {
                Matrix4f lm = local.get(e.getKey());
                if (lm != null) {
                    e.getValue().setTransform(new Matrix4f(root).mul(lm));
                    e.getValue().setChanged();
                    if (prof) {
                        KmodoProfiler.countInstances(1);
                    }
                }
            }
        }

        List<FlatLit> lit = new ArrayList<>(dynamicInstances.values());
        if (bodyInstance != null) {
            lit.add(bodyInstance);
        }
        long relightStart = prof ? System.nanoTime() : 0L;
        relight(partialTick, lit.toArray(new FlatLit[0]));
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.RELIGHT, System.nanoTime() - relightStart);
        }

        appliedStamp = stamp;
        appliedX = visualPos.x();
        appliedY = visualPos.y();
        appliedZ = visualPos.z();
        hasApplied = true;

        if (KmodoDebug.enabled()) {
            KmodoDebug.onFlywheelFrameDrawing(res);
        }
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, System.nanoTime() - totalStart);
        }
    }

    private void foldMatrix(Matrix4f m) {
        foldFloat(m.m00());
        foldFloat(m.m01());
        foldFloat(m.m02());
        foldFloat(m.m03());
        foldFloat(m.m10());
        foldFloat(m.m11());
        foldFloat(m.m12());
        foldFloat(m.m13());
        foldFloat(m.m20());
        foldFloat(m.m21());
        foldFloat(m.m22());
        foldFloat(m.m23());
        foldFloat(m.m30());
        foldFloat(m.m31());
        foldFloat(m.m32());
        foldFloat(m.m33());
    }

    private void foldFloat(float v) {
        int q = Float.isNaN(v) ? Integer.MIN_VALUE : Math.round(v * 1024.0F);
        poseHash = poseHash * FNV_PRIME + q;
    }

    private Instancer<TransformedInstance> instancer(Model model) {
        return instancerProvider().instancer(InstanceTypes.TRANSFORMED, model);
    }

    @Override
    protected void _delete() {
        BY_ENTITY.remove(entity.getId(), this);

        if (KmodoDebug.enabled() && instancesCreated) {
            ResourceLocation debugRes = modelRes();
            if (debugRes != null) {
                KmodoDebug.onFlywheelInstanceDeleted(debugRes);
            }
        }
        if (bodyInstance != null) {
            bodyInstance.delete();
            bodyInstance = null;
        }
        dynamicInstances.values().forEach(TransformedInstance::delete);
        dynamicInstances.clear();
    }

    private ResourceLocation modelRes() {
        try {
            GeoModel model = renderer.getGeoModel();
            return model.getModelResource((GeoAnimatable) entity);
        } catch (Throwable t) {
            return null;
        }
    }

    private BakedGeoModel bakedModel(ResourceLocation res) {
        try {
            return (BakedGeoModel) renderer.getGeoModel().getBakedModel(res);
        } catch (Throwable t) {
            return null;
        }
    }
}
