package com.norwood.wfcore.client.render.kmodo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import com.norwood.wfcore.client.render.kmodo.KmodoFlywheelModelCache.VehicleModels;
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

    private final GeoRenderer renderer;
    private final Map<String, TransformedInstance> dynamicInstances = new HashMap<>();
    private TransformedInstance bodyInstance;
    private boolean instancesCreated;

    private final KmodoDormancy dormancy = new KmodoDormancy();
    private long poseHash;
    private float lastVisualX;
    private float lastVisualY;
    private float lastVisualZ;
    private boolean hasVisualPos;

    public KmodoFlywheelVehicleVisual(VisualizationContext ctx, GeoVehicleEntity entity, float partialTick) {
        super(ctx, entity, partialTick);
        EntityRenderer<?> er = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        this.renderer = (er instanceof GeoRenderer) ? (GeoRenderer) er : null;
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
        if (prof) {
            KmodoProfiler.countProcessed();
        }
        float partialTick = ctx.partialTick();

        long bakeStart = prof ? System.nanoTime() : 0L;
        VehicleModels models = KmodoFlywheelModelCache.getModels(renderer, entity);
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.BAKE, System.nanoTime() - bakeStart);
        }
        if (models == null) {
            if (prof) {
                KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, System.nanoTime() - totalStart);
            }
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
            if (prof) {
                KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, System.nanoTime() - totalStart);
            }
            return;
        }

        ResourceLocation res = modelRes();
        if (res == null) {
            if (prof) {
                KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, System.nanoTime() - totalStart);
            }
            return;
        }

        Vector3f visualPos = getVisualPosition(partialTick);
        boolean visualMoved = !hasVisualPos
                || visualPos.x() != lastVisualX
                || visualPos.y() != lastVisualY
                || visualPos.z() != lastVisualZ;

        long dormStart = prof ? System.nanoTime() : 0L;
        boolean needsUpdate = dormancy.needsUpdate(entity, visualMoved);
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.DORMANCY, System.nanoTime() - dormStart);
            KmodoProfiler.countState(dormancy.state());
        }
        if (!needsUpdate) {
            if (KmodoDebug.enabled()) {
                KmodoDebug.onDormancy(res, true);
            }
            if (prof) {
                KmodoProfiler.countSkipped();
                KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, System.nanoTime() - totalStart);
            }
            return;
        }
        if (prof && visualMoved) {
            KmodoProfiler.countWake();
        }

        poseHash = FNV_OFFSET;
        foldFloat(visualPos.x());
        foldFloat(visualPos.y());
        foldFloat(visualPos.z());

        boolean posed = false;
        long lockStart = prof ? System.nanoTime() : 0L;
        synchronized (KmodoFlywheelModelCache.lockFor(res)) {
            if (prof) {
                KmodoProfiler.addPhase(KmodoProfiler.Phase.LOCK_WAIT, System.nanoTime() - lockStart);
            }
            GeoModel geoModel = renderer.getGeoModel();
            BakedGeoModel baked = bakedModel(res);
            if (baked != null) {
                long animStart = prof ? System.nanoTime() : 0L;
                geoModel.handleAnimations(entity, renderer.getInstanceId(entity),
                        new AnimationState<>(entity, 0f, 0f, partialTick, false));
                if (prof) {
                    KmodoProfiler.addPhase(KmodoProfiler.Phase.ANIMATE, System.nanoTime() - animStart);
                }

                PoseStack pose = new PoseStack();
                pose.translate(visualPos.x(), visualPos.y(), visualPos.z());
                float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
                ((VehicleRenderer) renderer).vehicleAxis(entity, pose, yaw, partialTick);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
                pose.translate(0.0F, 0.01F, 0.0F);

                long walkStart = prof ? System.nanoTime() : 0L;
                if (bodyInstance != null) {
                    Matrix4f m = pose.last().pose();
                    foldMatrix(m);
                    bodyInstance.setTransform(m);
                    bodyInstance.setChanged();
                    if (prof) {
                        KmodoProfiler.countInstances(1);
                    }
                }
                if (!dynamicInstances.isEmpty()) {
                    for (Object top : baked.topLevelBones()) {
                        walk(pose, (GeoBone) top, prof);
                    }
                }
                if (prof) {
                    KmodoProfiler.addPhase(KmodoProfiler.Phase.WALK, System.nanoTime() - walkStart);
                }
                posed = true;
            }
        }

        lastVisualX = visualPos.x();
        lastVisualY = visualPos.y();
        lastVisualZ = visualPos.z();
        hasVisualPos = true;

        if (!posed) {
            if (prof) {
                KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, System.nanoTime() - totalStart);
            }
            return;
        }

        dormancy.recordPose(poseHash, entity.tickCount);

        List<FlatLit> lit = new ArrayList<>(dynamicInstances.values());
        if (bodyInstance != null) {
            lit.add(bodyInstance);
        }
        long relightStart = prof ? System.nanoTime() : 0L;
        relight(partialTick, lit.toArray(new FlatLit[0]));
        if (prof) {
            KmodoProfiler.addPhase(KmodoProfiler.Phase.RELIGHT, System.nanoTime() - relightStart);
            KmodoProfiler.countUpdated();
            long totalDelta = System.nanoTime() - totalStart;
            KmodoProfiler.addPhase(KmodoProfiler.Phase.TOTAL, totalDelta);
            KmodoProfiler.addUpdatedTotal(totalDelta);
        }

        if (KmodoDebug.enabled()) {
            KmodoDebug.onFlywheelFrameDrawing(res);
            KmodoDebug.onDormancy(res, dormancy.isDormant());
        }
    }

    private void walk(PoseStack pose, GeoBone bone, boolean prof) {
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        TransformedInstance instance = dynamicInstances.get(bone.getName());
        if (instance != null) {
            Matrix4f m = pose.last().pose();
            foldMatrix(m);
            instance.setTransform(m);
            instance.setChanged();
            if (prof) {
                KmodoProfiler.countInstances(1);
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            walk(pose, child, prof);
        }
        pose.popPose();
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
