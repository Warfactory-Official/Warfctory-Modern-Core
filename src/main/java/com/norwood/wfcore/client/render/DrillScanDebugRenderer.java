package com.norwood.wfcore.client.render;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.debug.WFDebugKeyMappings;
import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import com.norwood.wfcore.common.machine.DrillRigMachine;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev tool (toggle with Numpad 3, see {@link WFDebugKeyMappings#TOGGLE_DRILL_SCAN}): visualises what a
 * Drilling Rig's deposit scan sees. For every formed drill nearby it draws a box on the drill head (yellow),
 * a box on every deposit block the scan finds within its radius (red), and a box on the one the rig would
 * pick as the vein centre (green). The boxes use the depth-test-off overlay type, so buried deposits show
 * through the ground. If red boxes appear but the machine screen still says "No ore", the server-side scan
 * is the culprit; if there are no boxes at all, there simply is no deposit within range of the head.
 */
@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class DrillScanDebugRenderer {

    /** How often (game ticks) the client re-scans - the scan walks columns to bedrock, too heavy per-frame. */
    private static final int REFRESH_TICKS = 5;

    private static boolean enabled = false;
    private static long lastScanTick = Long.MIN_VALUE;
    private static final List<BoxEntry> ENTRIES = new ArrayList<>();

    private record BoxEntry(BlockPos pos, float r, float g, float b) {}

    private DrillScanDebugRenderer() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        boolean toggled = false;
        while (WFDebugKeyMappings.TOGGLE_DRILL_SCAN.consumeClick()) {
            enabled = !enabled;
            toggled = true;
        }
        if (toggled) {
            ENTRIES.clear();
            lastScanTick = Long.MIN_VALUE;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        enabled ? "wfcore.debug.drill_scan.on" : "wfcore.debug.drill_scan.off"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        refresh(level);
        if (ENTRIES.isEmpty()) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(WFRenderTypes.EXPLOSIVE_OVERLAY);
        Matrix4f pose = poseStack.last().pose();
        for (BoxEntry e : ENTRIES) {
            float x0 = (float) (e.pos.getX() - cam.x);
            float y0 = (float) (e.pos.getY() - cam.y);
            float z0 = (float) (e.pos.getZ() - cam.z);
            ExplosiveOverlayRenderer.box(vc, pose, x0, y0, z0, x0 + 1, y0 + 1, z0 + 1, e.r, e.g, e.b, 0.45F);
        }
        buffers.endBatch(WFRenderTypes.EXPLOSIVE_OVERLAY);
    }

    /** Re-scan every nearby formed drill at most every {@link #REFRESH_TICKS} ticks; cache the boxes. */
    private static void refresh(ClientLevel level) {
        long now = level.getGameTime();
        if (lastScanTick != Long.MIN_VALUE && now - lastScanTick < REFRESH_TICKS) {
            return;
        }
        lastScanTick = now;
        ENTRIES.clear();
        for (BlockPos controllerPos : RenderMaskManager.getMaskedControllers()) {
            if (!(MetaMachine.getMachine(level, controllerPos) instanceof DrillRigMachine drill) || !drill.isFormed()) {
                continue;
            }
            DrillRigMachine.DebugScan scan = drill.debugScan(level);
            if (scan.head() != null) {
                ENTRIES.add(new BoxEntry(scan.head(), 1.0F, 1.0F, 0.0F)); // drill head: yellow
            }
            for (BlockPos hit : scan.hits()) {
                if (hit.equals(scan.nearest())) {
                    ENTRIES.add(new BoxEntry(hit, 0.1F, 1.0F, 0.1F)); // rig's pick (vein centre): green
                } else {
                    ENTRIES.add(new BoxEntry(hit, 1.0F, 0.15F, 0.15F)); // other deposits in range: red
                }
            }
        }
    }
}
