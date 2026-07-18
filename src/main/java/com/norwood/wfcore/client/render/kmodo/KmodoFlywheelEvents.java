package com.norwood.wfcore.client.render.kmodo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;
import com.norwood.wfcore.WFCore;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.engine_room.flywheel.api.visualization.VisualManager;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class KmodoFlywheelEvents {

    private KmodoFlywheelEvents() {}

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        boolean justRegistered = KmodoFlywheelRegistrar.ensureRegistered();

        if (!KmodoConfig.flywheelEnabled() || !BackendManager.isBackendOn()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        VisualManager<Entity> entities = null;
        if (justRegistered && VisualizationManager.supportsVisualization(mc.level)) {
            VisualizationManager manager = VisualizationManager.get(mc.level);
            if (manager != null) {
                entities = manager.entities();
            }
        }

        boolean drawHitboxes = mc.getEntityRenderDispatcher().shouldRenderHitBoxes()
                && !mc.showOnlyReducedInfo();
        float partialTick = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drewHitbox = false;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof GeoVehicleEntity vehicle)) {
                continue;
            }
            EntityRenderer<?> er = mc.getEntityRenderDispatcher().getRenderer(vehicle);
            if (er instanceof GeoRenderer<?> renderer) {
                KmodoFlywheelModelCache.getModels(renderer, vehicle);
            }
            if (entities != null) {
                entities.queueAdd(vehicle);
            }
            if (drawHitboxes && !vehicle.isInvisible() && VisualizationHelper.skipVanillaRender(vehicle)) {
                double ex = Mth.lerp((double) partialTick, vehicle.xOld, vehicle.getX());
                double ey = Mth.lerp((double) partialTick, vehicle.yOld, vehicle.getY());
                double ez = Mth.lerp((double) partialTick, vehicle.zOld, vehicle.getZ());
                pose.pushPose();
                pose.translate(ex - cam.x, ey - cam.y, ez - cam.z);
                renderHitbox(pose, buffers.getBuffer(RenderType.lines()), vehicle, partialTick);
                pose.popPose();
                drewHitbox = true;
            }
        }

        if (drewHitbox) {
            buffers.endBatch(RenderType.lines());
        }
    }

    private static void renderHitbox(PoseStack pose, VertexConsumer lines, Entity entity, float partialTick) {
        AABB box = entity.getBoundingBox().move(-entity.getX(), -entity.getY(), -entity.getZ());
        LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 1.0F, 1.0F, 1.0F);

        if (entity.isMultipartEntity() && entity.getParts() != null) {
            double px = -Mth.lerp((double) partialTick, entity.xOld, entity.getX());
            double py = -Mth.lerp((double) partialTick, entity.yOld, entity.getY());
            double pz = -Mth.lerp((double) partialTick, entity.zOld, entity.getZ());
            for (PartEntity<?> part : entity.getParts()) {
                pose.pushPose();
                pose.translate(px + Mth.lerp((double) partialTick, part.xOld, part.getX()),
                        py + Mth.lerp((double) partialTick, part.yOld, part.getY()),
                        pz + Mth.lerp((double) partialTick, part.zOld, part.getZ()));
                LevelRenderer.renderLineBox(pose, lines,
                        part.getBoundingBox().move(-part.getX(), -part.getY(), -part.getZ()),
                        0.25F, 1.0F, 0.0F, 1.0F);
                pose.popPose();
            }
        }

        Vec3 view = entity.getViewVector(partialTick);
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float eye = entity.getEyeHeight();
        lines.vertex(matrix, 0.0F, eye, 0.0F).color(0, 0, 255, 255)
                .normal(normal, (float) view.x, (float) view.y, (float) view.z).endVertex();
        lines.vertex(matrix, (float) (view.x * 2.0), (float) (eye + view.y * 2.0), (float) (view.z * 2.0))
                .color(0, 0, 255, 255)
                .normal(normal, (float) view.x, (float) view.y, (float) view.z).endVertex();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            purge();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            purge();
        }
    }

    private static void purge() {
        KmodoMeshCache.invalidateAll();
        KmodoFlywheelModelCache.invalidateAll();
        KmodoDebug.invalidateAll();
    }
}
