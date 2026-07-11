package com.norwood.wfcore.client.render.mask;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side render mask: the set of structure block positions a machine's GLTF model visually
 * replaces, hidden from the chunk meshers so only the model is seen. Ported from the 1.12.2 coremod's
 * {@code wfcore.api.util.RenderMaskManager}.
 * <p>
 * The masked set is published as an immutable snapshot behind a {@code volatile} reference so the
 * Sodium/Embeddium chunk-build worker threads can read it safely while the main thread mutates it (the
 * 1.12.2 version was main-thread only and relied on the single-threaded chunk builder). The per-machine
 * mapping is only ever touched on the render/main thread.
 */
@OnlyIn(Dist.CLIENT)
public final class RenderMaskManager {

    private RenderMaskManager() {}

    /** Immutable snapshot of all masked positions; replaced wholesale on every change. */
    private static volatile Set<BlockPos> modelDisabled = Set.of();
    /** controller position -> the positions it masks. Main/render thread only. */
    private static final Map<BlockPos, Collection<BlockPos>> multiDisabled = new HashMap<>();

    public static boolean isControllerMasked(BlockPos controllerPos) {
        return multiDisabled.containsKey(controllerPos);
    }

    /** Controller positions of every currently formed, on-screen animated machine (render/main thread only). */
    public static Set<BlockPos> getMaskedControllers() {
        return multiDisabled.keySet();
    }

    public static void addDisableModel(BlockPos controllerPos, Collection<BlockPos> poses) {
        if (poses == null) {
            return;
        }
        // Tracked even when poses is empty: controllers whose model doesn't hide any structure blocks (e.g.
        // the drill rig) must still register here so they show up in getMaskedControllers(), which the
        // model-transform debugger and isControllerMasked() rely on to know a machine is currently rendering.
        multiDisabled.put(controllerPos, poses);
        rebuildSnapshot();
        updateRenderChunk(poses);
    }

    public static void removeDisableModel(BlockPos controllerPos) {
        Collection<BlockPos> poses = multiDisabled.remove(controllerPos);
        if (poses == null) {
            return;
        }
        rebuildSnapshot();
        updateRenderChunk(poses);
    }

    /** Reads the published snapshot; safe to call from chunk-build worker threads. */
    public static boolean isModelDisabledRaw(BlockPos pos) {
        return modelDisabled.contains(pos);
    }

    public static void clear() {
        multiDisabled.clear();
        modelDisabled = Set.of();
    }

    private static void rebuildSnapshot() {
        Set<BlockPos> next = new HashSet<>();
        for (Collection<BlockPos> poses : multiDisabled.values()) {
            next.addAll(poses);
        }
        modelDisabled = next; // publish; never mutated after this point
    }

    private static void updateRenderChunk(Collection<BlockPos> poses) {
        if (poses.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : poses) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        mc.levelRenderer.setBlocksDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
