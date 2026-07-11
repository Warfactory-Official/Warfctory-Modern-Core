package com.norwood.wfcore.client.debug;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.render.gltf.IAnimatedMachine;
import com.norwood.wfcore.config.WFCoreConfig;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side live editor for {@link IAnimatedMachine#getModelTransform()} and
 * {@link IAnimatedMachine#getModelScale()}. Lets a dev nudge the per-facing render offset (or scale)
 * in-world and re-export it as a ready-to-paste {@code switch} block, instead of recompiling for every
 * millimetre adjustment.
 * <p>
 * Overrides are keyed by machine class + facing (not by block position): every on-screen instance of a
 * given machine sharing a facing renders with the same tuned value, mirroring the fact that the real
 * {@code getModelTransform()}/{@code getModelScale()} are hardcoded constants per class+facing, not per
 * instance.
 */
@OnlyIn(Dist.CLIENT)
public final class ModelTransformDebug {

    private ModelTransformDebug() {}

    /** Which field the nudge/export/reset keys currently act on; toggled by {@link WFDebugKeyMappings#MODE}. */
    public enum EditMode {
        TRANSFORM,
        SCALE
    }

    /** Successive Numpad0 presses cycle through these nudge sizes, in blocks (or scale units). */
    private static final double[] STEPS = { 1.0, 0.25, 0.05 };

    private static boolean enabled = false;
    private static int stepIndex = 1;
    private static EditMode mode = EditMode.TRANSFORM;

    private static Class<? extends IAnimatedMachine> targetClass;
    private static Direction targetFacing;

    private static final Map<Class<? extends IAnimatedMachine>, EnumMap<Direction, Vec3>> transformOverrides = new HashMap<>();
    private static final Map<Class<? extends IAnimatedMachine>, EnumMap<Direction, Vec3>> scaleOverrides = new HashMap<>();

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Arms/disarms the debugger. Enabling is refused (a no-op) unless {@code modelTransformDebugEnabled}
     * is set in {@code wfcore.toml}, so the numpad bindings and HUD stay inert for normal players even
     * though the keybinds are always registered.
     *
     * @return the resulting enabled state, so callers can tell a config-gated refusal from a real toggle
     */
    public static boolean setEnabled(boolean value) {
        if (value && !WFCoreConfig.isModelTransformDebugEnabled()) {
            return enabled;
        }
        enabled = value;
        if (!enabled) {
            targetClass = null;
            targetFacing = null;
        }
        return enabled;
    }

    public static double currentStep() {
        return STEPS[stepIndex];
    }

    public static void cycleStep() {
        stepIndex = (stepIndex + 1) % STEPS.length;
    }

    public static EditMode mode() {
        return mode;
    }

    /** Toggles between tuning {@code getModelTransform()} and {@code getModelScale()}; keeps the same target. */
    public static void cycleMode() {
        mode = mode == EditMode.TRANSFORM ? EditMode.SCALE : EditMode.TRANSFORM;
    }

    private static Map<Class<? extends IAnimatedMachine>, EnumMap<Direction, Vec3>> currentOverrides() {
        return mode == EditMode.SCALE ? scaleOverrides : transformOverrides;
    }

    private static Vec3 identityFor(EditMode m) {
        return m == EditMode.SCALE ? new Vec3(1, 1, 1) : Vec3.ZERO;
    }

    /**
     * Called every client tick with the nearest formed animated machine on screen. Adopts it as the
     * live-edit target and seeds both its transform and scale from the real (compiled) values if not
     * already captured, so nudging starts from the shipped baseline rather than identity.
     */
    public static void trackTarget(IAnimatedMachine animated, Direction facing) {
        if (!enabled || animated == null || facing == null) {
            return;
        }
        targetClass = animated.getClass();
        targetFacing = facing;
        transformOverrides.computeIfAbsent(targetClass, c -> new EnumMap<>(Direction.class))
                .computeIfAbsent(facing, f -> animated.getModelTransform());
        scaleOverrides.computeIfAbsent(targetClass, c -> new EnumMap<>(Direction.class))
                .computeIfAbsent(facing, f -> animated.getModelScale());
    }

    public static boolean hasTarget() {
        return enabled && targetClass != null && targetFacing != null;
    }

    @Nullable
    public static Class<? extends IAnimatedMachine> targetClass() {
        return targetClass;
    }

    public static Direction targetFacing() {
        return targetFacing;
    }

    /** The value for the active {@link #mode()} (transform or scale) at the current target. */
    public static Vec3 targetValue() {
        if (!hasTarget()) {
            return identityFor(mode);
        }
        return currentOverrides().get(targetClass).getOrDefault(targetFacing, identityFor(mode));
    }

    public static void nudge(double dx, double dy, double dz) {
        if (!hasTarget()) {
            return;
        }
        Vec3 current = targetValue();
        currentOverrides().get(targetClass).put(targetFacing, current.add(dx, dy, dz));
    }

    /** Resets the currently targeted facing's active-mode value back to the machine's real (compiled) value. */
    public static void resetTarget(IAnimatedMachine sourceInstance) {
        if (!hasTarget() || sourceInstance == null) {
            return;
        }
        Vec3 real = mode == EditMode.SCALE ? sourceInstance.getModelScale() : sourceInstance.getModelTransform();
        currentOverrides().get(targetClass).put(targetFacing, real);
    }

    /**
     * Read by {@link com.norwood.wfcore.client.render.gltf.GltfMachineRenderer} in place of
     * {@link IAnimatedMachine#getModelTransform()} when a live override exists.
     */
    public static Vec3 resolve(IAnimatedMachine animated, Direction facing) {
        if (!enabled || facing == null) {
            return animated.getModelTransform();
        }
        EnumMap<Direction, Vec3> byFacing = transformOverrides.get(animated.getClass());
        if (byFacing == null) {
            return animated.getModelTransform();
        }
        return byFacing.getOrDefault(facing, animated.getModelTransform());
    }

    /**
     * Read by {@link com.norwood.wfcore.client.render.gltf.GltfMachineRenderer} in place of
     * {@link IAnimatedMachine#getModelScale()} when a live override exists.
     */
    public static Vec3 resolveScale(IAnimatedMachine animated, Direction facing) {
        if (!enabled || facing == null) {
            return animated.getModelScale();
        }
        EnumMap<Direction, Vec3> byFacing = scaleOverrides.get(animated.getClass());
        if (byFacing == null) {
            return animated.getModelScale();
        }
        return byFacing.getOrDefault(facing, animated.getModelScale());
    }

    /**
     * Sends a paste-ready {@code switch} block for the current target/mode's captured facings to chat/log/clipboard.
     */
    public static void exportCurrent() {
        if (!hasTarget()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        EnumMap<Direction, Vec3> byFacing = currentOverrides().get(targetClass);
        String fieldLabel = mode == EditMode.SCALE ? "scale" : "transform";
        StringBuilder text = new StringBuilder();
        for (Direction dir : new Direction[] { Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH }) {
            Vec3 v = byFacing.get(dir);
            if (v == null) {
                text.append("            // ").append(dir).append(" not captured yet - look at a ")
                        .append(targetClass.getSimpleName()).append(" built facing ").append(dir).append('\n');
            } else {
                text.append("            case ").append(dir).append(" -> new Vec3(")
                        .append(fmt(v.x)).append(", ").append(fmt(v.y)).append(", ").append(fmt(v.z))
                        .append(");\n");
            }
        }
        String result = text.toString();
        WFCore.LOGGER.info("[ModelTransformDebug] {} {} exported:\n{}", targetClass.getSimpleName(), fieldLabel,
                result);

        mc.keyboardHandler.setClipboard(result);
        if (mc.player != null) {
            MutableComponent header = Component.literal("[WFCore] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(targetClass.getSimpleName() + " " + fieldLabel + " copied to clipboard")
                            .withStyle(ChatFormatting.GRAY));
            mc.player.displayClientMessage(header, false);
            for (String line : result.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                mc.player.displayClientMessage(
                        Component.literal(line).withStyle(s -> s.withColor(ChatFormatting.YELLOW)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, line))),
                        false);
            }
        }
    }

    private static String fmt(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) {
            return Long.toString(Math.round(d));
        }
        return BigDecimal.valueOf(d).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
