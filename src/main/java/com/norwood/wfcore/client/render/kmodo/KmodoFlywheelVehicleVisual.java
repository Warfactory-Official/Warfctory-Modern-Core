package com.norwood.wfcore.client.render.kmodo;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
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

/**
 * Kmodo Accelerator (Flywheel path) — the per-vehicle Flywheel {@link AbstractEntityVisual}. One
 * {@link TransformedInstance} per cube-bearing bone is drawn from the cached bone-local models; instances of the
 * same bone across all vehicles of a model are batched into a single GPU draw by Flywheel.
 * <p>
 * {@link #beginFrame} runs at {@code renderLevel} HEAD (before the vanilla entity pass), so it computes each
 * bone's live transform this frame with zero lag: it drives GeckoLib's own animation
 * ({@code getGeoModel().handleAnimations} — reproducing SBW's turret/wheel/track bone mutations), then walks the
 * bone tree with {@link RenderUtils#prepMatrixForBone} under a root of {@code visualPosition * vehicleAxis}
 * (SBW's own public {@code vehicleAxis} is reused for exact body yaw/pitch/roll). Light is pushed per-instance
 * via {@link #relight}.
 * <p>
 * The GeckoLib bone tree is shared per model, so {@code handleAnimations} + the walk are done under
 * {@link KmodoFlywheelModelCache#lockFor} to be safe under Flywheel's parallel frame plan. When Flywheel is off
 * or the model isn't baked, this bows out and the vanilla renderer (and the Kmodo retained path) draw instead —
 * gated identically by the visualizer's {@code skipVanillaRender} predicate.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class KmodoFlywheelVehicleVisual extends AbstractEntityVisual<GeoVehicleEntity> implements SimpleDynamicVisual {

    private final GeoRenderer renderer;
    private final Map<String, TransformedInstance> instances = new HashMap<>();
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

        Map<String, Model> models = KmodoFlywheelModelCache.getModels(renderer, entity);
        if (models == null || models.isEmpty()) {
            return; // still baking (or failed) — vanilla/retained path is drawing it
        }
        if (!instancesCreated) {
            for (Map.Entry<String, Model> e : models.entrySet()) {
                Instancer<TransformedInstance> instancer =
                        instancerProvider().instancer(InstanceTypes.TRANSFORMED, e.getValue());
                instances.put(e.getKey(), instancer.createInstance());
            }
            instancesCreated = true;
        }
        if (instances.isEmpty()) {
            return;
        }

        ResourceLocation res = modelRes();
        BakedGeoModel baked = res == null ? null : bakedModel(res);
        if (baked == null) {
            return;
        }

        // The GeckoLib bone tree is shared per model; serialise animation + walk per model against parallel visuals.
        synchronized (KmodoFlywheelModelCache.lockFor(res)) {
            GeoModel geoModel = renderer.getGeoModel();
            geoModel.handleAnimations(entity, renderer.getInstanceId(entity),
                    new AnimationState<>(entity, 0f, 0f, partialTick, false));

            PoseStack pose = new PoseStack();
            Vector3f visualPos = getVisualPosition(partialTick);
            pose.translate(visualPos.x(), visualPos.y(), visualPos.z());
            float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            ((VehicleRenderer) renderer).vehicleAxis(entity, pose, yaw, partialTick);

            for (Object top : baked.topLevelBones()) {
                walk(pose, (GeoBone) top);
            }
        }

        relight(partialTick, instances.values().toArray(new FlatLit[0]));
    }

    /** Recursively reproduces GeckoLib's renderRecursively transform, pushing each bone's live pose to its instance. */
    private void walk(PoseStack pose, GeoBone bone) {
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        TransformedInstance instance = instances.get(bone.getName());
        if (instance != null) {
            instance.setTransform(pose.last().pose());
            instance.setChanged();
        }
        for (GeoBone child : bone.getChildBones()) {
            walk(pose, child);
        }
        pose.popPose();
    }

    @Override
    protected void _delete() {
        instances.values().forEach(TransformedInstance::delete);
        instances.clear();
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
