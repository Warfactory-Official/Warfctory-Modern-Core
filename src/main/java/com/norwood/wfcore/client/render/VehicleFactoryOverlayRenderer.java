package com.norwood.wfcore.client.render;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.machine.AbstractVehicleFactoryMachine;
import com.norwood.wfcore.common.machine.VehicleFactoryOverlayTracker;
import org.joml.Matrix4f;

import java.util.Set;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class VehicleFactoryOverlayRenderer {

    private VehicleFactoryOverlayRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !player.isShiftKeyDown()) {
            return;
        }
        Set<BlockPos> positions = VehicleFactoryOverlayTracker.positions();
        if (positions.isEmpty()) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(WFRenderTypes.EXPLOSIVE_OVERLAY);
        Matrix4f pose = poseStack.last().pose();

        for (BlockPos pos : positions) {
            if (!(MetaMachine.getMachine(level, pos) instanceof AbstractVehicleFactoryMachine factory) ||
                    !factory.isFormed()) {
                VehicleFactoryOverlayTracker.remove(pos); // stale (broken / chunk unloaded) — self-heal
                continue;
            }
            AABB box = factory.getClearanceBox();
            ExplosiveOverlayRenderer.box(vc, pose,
                    (float) (box.minX - cam.x), (float) (box.minY - cam.y), (float) (box.minZ - cam.z),
                    (float) (box.maxX - cam.x), (float) (box.maxY - cam.y), (float) (box.maxZ - cam.z),
                    0.2F, 1.0F, 0.3F, 0.25F);
        }
        buffers.endBatch(WFRenderTypes.EXPLOSIVE_OVERLAY);
    }
}
