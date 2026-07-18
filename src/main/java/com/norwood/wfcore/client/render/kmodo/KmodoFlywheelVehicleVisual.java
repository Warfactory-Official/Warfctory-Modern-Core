package com.norwood.wfcore.client.render.kmodo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

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

    private final GeoRenderer renderer;
    private final Map<String, TransformedInstance> dynamicInstances = new HashMap<>();
    private TransformedInstance bodyInstance;
    private boolean instancesCreated;

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
        float partialTick = ctx.partialTick();

        VehicleModels models = KmodoFlywheelModelCache.getModels(renderer, entity);
        if (models == null) {
            return;
        }
        if (!instancesCreated) {
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
        BakedGeoModel baked = res == null ? null : bakedModel(res);
        if (baked == null) {
            return;
        }

        synchronized (KmodoFlywheelModelCache.lockFor(res)) {
            GeoModel geoModel = renderer.getGeoModel();
            geoModel.handleAnimations(entity, renderer.getInstanceId(entity),
                    new AnimationState<>(entity, 0f, 0f, partialTick, false));

            PoseStack pose = new PoseStack();
            Vector3f visualPos = getVisualPosition(partialTick);
            pose.translate(visualPos.x(), visualPos.y(), visualPos.z());
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            ((VehicleRenderer) renderer).vehicleAxis(entity, pose, yaw, partialTick);

            if (bodyInstance != null) {
                bodyInstance.setTransform(pose.last().pose());
                bodyInstance.setChanged();
            }
            if (!dynamicInstances.isEmpty()) {
                for (Object top : baked.topLevelBones()) {
                    walk(pose, (GeoBone) top);
                }
            }
        }

        List<FlatLit> lit = new ArrayList<>(dynamicInstances.values());
        if (bodyInstance != null) {
            lit.add(bodyInstance);
        }
        relight(partialTick, lit.toArray(new FlatLit[0]));

        if (KmodoDebug.enabled()) {
            KmodoDebug.onFlywheelFrameDrawing(res);
        }
    }

    private void walk(PoseStack pose, GeoBone bone) {
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        TransformedInstance instance = dynamicInstances.get(bone.getName());
        if (instance != null) {
            instance.setTransform(pose.last().pose());
            instance.setChanged();
        }
        for (GeoBone child : bone.getChildBones()) {
            walk(pose, child);
        }
        pose.popPose();
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
