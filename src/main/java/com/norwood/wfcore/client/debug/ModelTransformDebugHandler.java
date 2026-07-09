package com.norwood.wfcore.client.debug;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.render.gltf.IAnimatedMachine;
import com.norwood.wfcore.client.render.mask.RenderMaskManager;

/**
 * Drives {@link ModelTransformDebug} off the numpad keys registered in {@link WFDebugKeyMappings}, and
 * draws its live-editing HUD. Targeting rides on {@link RenderMaskManager}'s existing "which controllers
 * are currently rendering their GLTF model" bookkeeping rather than a fresh raycast, since the actual
 * multiblock controller block the model belongs to is often buried or tiny compared to the model it
 * draws — nearest-on-screen is a far more forgiving way to pick "the one you're looking at".
 */
@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public class ModelTransformDebugHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (WFDebugKeyMappings.TOGGLE.consumeClick()) {
            boolean wanted = !ModelTransformDebug.isEnabled();
            boolean result = ModelTransformDebug.setEnabled(wanted);
            if (wanted && !result) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(
                            "§c[WFCore] Model transform debug is disabled - set modelTransformDebugEnabled: true in wfcore.yaml"),
                            true);
                }
            }
        }
        if (!ModelTransformDebug.isEnabled()) {
            return;
        }

        updateTarget();

        double step = ModelTransformDebug.currentStep();
        double dx = clicks(WFDebugKeyMappings.X_POS) - clicks(WFDebugKeyMappings.X_NEG);
        double dy = clicks(WFDebugKeyMappings.Y_POS) - clicks(WFDebugKeyMappings.Y_NEG);
        double dz = clicks(WFDebugKeyMappings.Z_POS) - clicks(WFDebugKeyMappings.Z_NEG);
        if (dx != 0 || dy != 0 || dz != 0) {
            ModelTransformDebug.nudge(dx * step, dy * step, dz * step);
        }
        if (WFDebugKeyMappings.CYCLE_STEP.consumeClick()) {
            ModelTransformDebug.cycleStep();
        }
        if (WFDebugKeyMappings.EXPORT.consumeClick()) {
            ModelTransformDebug.exportCurrent();
        }
        if (WFDebugKeyMappings.RESET.consumeClick()) {
            IAnimatedMachine source = currentTargetInstance();
            if (source != null) {
                ModelTransformDebug.resetTarget(source);
            }
        }
    }

    private static int clicks(net.minecraft.client.KeyMapping key) {
        int n = 0;
        while (key.consumeClick()) {
            n++;
        }
        return n;
    }

    /** Nearest currently-rendering animated machine to the player becomes (or stays) the live-edit target. */
    private static void updateTarget() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            return;
        }
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (BlockPos pos : RenderMaskManager.getMaskedControllers()) {
            double distSq = player.position().distanceToSqr(Vec3.atCenterOf(pos));
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = pos;
            }
        }
        if (nearest == null) {
            return; // keep the previous target so you can back away and keep tuning
        }
        BlockEntity be = level.getBlockEntity(nearest);
        if (!(be instanceof MetaMachineBlockEntity mbe)) {
            return;
        }
        MetaMachine machine = mbe.getMetaMachine();
        if (machine instanceof IAnimatedMachine animated) {
            ModelTransformDebug.trackTarget(animated, machine.getFrontFacing());
        }
    }

    @org.jetbrains.annotations.Nullable
    private static IAnimatedMachine currentTargetInstance() {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return null;
        }
        for (BlockPos pos : RenderMaskManager.getMaskedControllers()) {
            if (!(level.getBlockEntity(pos) instanceof MetaMachineBlockEntity mbe)) {
                continue;
            }
            MetaMachine machine = mbe.getMetaMachine();
            if (machine instanceof IAnimatedMachine animated &&
                    animated.getClass() == ModelTransformDebug.targetClass() &&
                    machine.getFrontFacing() == ModelTransformDebug.targetFacing()) {
                return animated;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ModelTransformDebug.isEnabled()) {
            return;
        }
        GuiGraphics gg = event.getGuiGraphics();
        int x = 6;
        int y = 6;
        int lineHeight = 10;

        if (!ModelTransformDebug.hasTarget()) {
            gg.drawString(Minecraft.getInstance().font,
                    "§b[WFCore Transform Debug]§r walk up to a formed animated machine", x, y, 0xFFFFFF);
            return;
        }

        Vec3 v = ModelTransformDebug.targetValue();
        Direction facing = ModelTransformDebug.targetFacing();
        String className = ModelTransformDebug.targetClass().getSimpleName();

        gg.drawString(Minecraft.getInstance().font,
                "§b[WFCore Transform Debug]§r " + className + " facing=" + facing, x, y, 0xFFFFFF);
        y += lineHeight;
        gg.drawString(Minecraft.getInstance().font,
                String.format("X: %.3f  Y: %.3f  Z: %.3f   (step %.2f)", v.x, v.y, v.z,
                        ModelTransformDebug.currentStep()),
                x, y, 0xFFFF55);
        y += lineHeight;
        gg.drawString(Minecraft.getInstance().font,
                "§7Numpad 4/6 X  8/2 Y  7/9 Z  |  0 step  |  Enter export  |  . reset", x, y, 0xAAAAAA);
    }
}
