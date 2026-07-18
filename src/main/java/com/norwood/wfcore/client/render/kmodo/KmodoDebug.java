package com.norwood.wfcore.client.render.kmodo;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;

/**
 * Kmodo Accelerator — debug/observability layer. Tracks per-model GPU upload stats, active render
 * modes, and live vehicle/instance counts. ALL calls are guarded by {@link #enabled()} so there is
 * zero overhead when the toggle is off.
 * <p>
 * Toggle from in-game with the bound key (default UNBOUND; see {@link KmodoDebugKeyMappings}).
 * Pressing the key twice dumps a full summary to chat and to the log. The overlay (if enabled) shows
 * a live one-line per model while the toggle is on.
 * <p>
 * Thread-safety: all mutable state lives in {@link ConcurrentHashMap}s or {@link AtomicInteger}s.
 * Bake callbacks arrive from {@code Util.backgroundExecutor()}; instance-count mutations happen on
 * the render thread; all reads happen on the render thread. The combination is safe: worst case the
 * first beginFrame after a concurrent bake completes sees a snapshot that is one bake behind.
 */
public final class KmodoDebug {

    private KmodoDebug() {}

    // -------------------------------------------------------------------------
    // Toggle
    // -------------------------------------------------------------------------

    private static volatile boolean ENABLED = false;

    /** Returns true when the debug layer is active. Guard every call site with this. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** Toggles the enabled flag and returns the new state. */
    public static boolean toggle() {
        ENABLED = !ENABLED;
        return ENABLED;
    }

    // -------------------------------------------------------------------------
    // Render-mode enum
    // -------------------------------------------------------------------------

    /** The three render modes a vehicle can be in. */
    public enum Mode {
        /** Flywheel GPU-instanced (body + per-dynamic-bone TransformedInstances). */
        FLYWHEEL,
        /** Retained per-bone VBO draws via KmodoAccumulator + KmodoRenderer. */
        RETAINED,
        /** Stock GeckoLib immediate-mode tessellation — no Kmodo path active. */
        VANILLA
    }

    // -------------------------------------------------------------------------
    // Per-model stats
    // -------------------------------------------------------------------------

    /**
     * All stats we collect for one GeckoLib model {@link ResourceLocation}. Fields are written from
     * potentially different threads (bake thread, render thread) but reads always happen on the render
     * thread, and each field is individually volatile / atomic, so no coarse locking is needed.
     */
    public static final class ModelStats {
        /** The RL that identifies this model. */
        public final ResourceLocation res;

        // --- Flywheel bake stats (written off-thread, read on render thread) ---
        public volatile int flywheelBodyVertices = 0;
        public volatile int flywheelDynamicBoneCount = 0;
        /** Sum of per-bone vertex counts for the dynamic bones. */
        public volatile int flywheelDynamicVertices = 0;
        /**
         * Total off-heap bytes across all {@code MemoryBlock}s (= FullVertexView.STRIDE *
         * totalVertices, both body and dynamic).
         */
        public volatile long flywheelGpuBytes = 0L;

        // --- Retained bake stats (written on render-thread upload, read on render thread) ---
        public volatile int retainedVboCount = 0;
        public volatile int retainedTotalVertices = 0;

        // --- Live instance/vehicle counts (written on render thread) ---
        /** Number of Flywheel visuals currently alive (one per vehicle in the world). */
        public final AtomicInteger flywheelLiveInstances = new AtomicInteger(0);
        /** Number of vehicles rendered via the retained path this frame. Cleared each frame. */
        public final AtomicInteger retainedFrameVehicles = new AtomicInteger(0);

        // --- Mode tracking ---
        public volatile Mode lastMode = null;

        // --- One-shot log flags ---
        volatile boolean flywheelBakeSummarised = false;
        volatile boolean retainedBakeSummarised = false;

        ModelStats(ResourceLocation res) {
            this.res = res;
        }
    }

    /** All per-model stats, keyed by model {@link ResourceLocation}. */
    private static final Map<ResourceLocation, ModelStats> MODELS = new ConcurrentHashMap<>();

    /** The set of model RLs that rendered via RETAINED this frame (cleared at frame start). */
    static final Set<ResourceLocation> RETAINED_THIS_FRAME = ConcurrentHashMap.newKeySet();

    /** Returns (creating if absent) the stats record for {@code res}. */
    static ModelStats statsFor(ResourceLocation res) {
        return MODELS.computeIfAbsent(res, ModelStats::new);
    }

    /** All currently-tracked model stats (unmodifiable view for display). */
    public static Collection<ModelStats> allStats() {
        return MODELS.values();
    }

    // -------------------------------------------------------------------------
    // Flywheel bake instrumentation
    // -------------------------------------------------------------------------

    /**
     * Called (off-thread) when the Flywheel model for {@code res} finishes baking. Records body
     * vertex count, dynamic bone count + per-bone vertex totals, and total off-heap GPU bytes. Logs a
     * one-shot summary on the first call per model.
     *
     * @param res               the GeckoLib model resource
     * @param bodyVertices      vertex count of the merged static body mesh (0 if no static geometry)
     * @param dynamicBoneCount  number of separately-baked animated bones
     * @param dynamicVertices   sum of vertex counts across all dynamic-bone meshes
     * @param memoryBlockBytes  sum of all {@code MemoryBlock} sizes (in bytes)
     */
    public static void onFlywheelBaked(ResourceLocation res, int bodyVertices, int dynamicBoneCount,
                                       int dynamicVertices, long memoryBlockBytes) {
        if (!ENABLED) return;
        ModelStats s = statsFor(res);
        s.flywheelBodyVertices = bodyVertices;
        s.flywheelDynamicBoneCount = dynamicBoneCount;
        s.flywheelDynamicVertices = dynamicVertices;
        s.flywheelGpuBytes = memoryBlockBytes;

        if (!s.flywheelBakeSummarised) {
            s.flywheelBakeSummarised = true;
            int totalVerts = bodyVertices + dynamicVertices;
            WFCore.LOGGER.info(
                    "[KmodoDebug] Flywheel bake done: {} | body={} verts | dynamic {} bone(s)={} verts"
                            + " | GPU mem={}B (stride {}×{})",
                    res, bodyVertices, dynamicBoneCount, dynamicVertices,
                    memoryBlockBytes, FullVertexView.STRIDE, totalVerts);
        }
    }

    // -------------------------------------------------------------------------
    // Flywheel instance tracking
    // -------------------------------------------------------------------------

    /**
     * Called on the render thread when a {@code KmodoFlywheelVehicleVisual} creates its instances
     * (body + dynamic bones). Increments the live instance counter for the model.
     *
     * @param res              the GeckoLib model resource
     * @param dynamicBoneCount number of dynamic-bone instances created for this vehicle
     */
    public static void onFlywheelInstanceCreated(ResourceLocation res, int dynamicBoneCount) {
        if (!ENABLED) return;
        statsFor(res).flywheelLiveInstances.incrementAndGet();
    }

    /**
     * Called on the render thread when a {@code KmodoFlywheelVehicleVisual} is deleted. Decrements
     * the live instance counter.
     *
     * @param res the GeckoLib model resource
     */
    public static void onFlywheelInstanceDeleted(ResourceLocation res) {
        if (!ENABLED) return;
        ModelStats s = MODELS.get(res);
        if (s != null) {
            s.flywheelLiveInstances.decrementAndGet();
        }
    }

    /**
     * Called from {@code KmodoFlywheelVehicleVisual.beginFrame} when it successfully pushes instance
     * transforms (= vehicle is actually rendering via Flywheel this frame). Records / logs the mode
     * transition for the model.
     *
     * @param res the GeckoLib model resource
     */
    public static void onFlywheelFrameDrawing(ResourceLocation res) {
        if (!ENABLED) return;
        recordMode(res, Mode.FLYWHEEL);
    }

    // -------------------------------------------------------------------------
    // Retained-VBO bake instrumentation
    // -------------------------------------------------------------------------

    /**
     * Called on the render thread when the retained per-bone VBOs are uploaded for {@code res}.
     * Records the VBO count and total vertex count; logs a one-shot summary.
     *
     * @param res          the GeckoLib model resource
     * @param vboCount     number of bone VBOs uploaded
     * @param totalVertices sum of vertex counts across all bone VBOs
     */
    public static void onRetainedBaked(ResourceLocation res, int vboCount, int totalVertices) {
        if (!ENABLED) return;
        ModelStats s = statsFor(res);
        s.retainedVboCount = vboCount;
        s.retainedTotalVertices = totalVertices;

        if (!s.retainedBakeSummarised) {
            s.retainedBakeSummarised = true;
            WFCore.LOGGER.info("[KmodoDebug] Retained bake done: {} | {} VBOs | {} total verts",
                    res, vboCount, totalVertices);
        }
    }

    // -------------------------------------------------------------------------
    // Retained frame instrumentation
    // -------------------------------------------------------------------------

    /**
     * Called from {@code KmodoAccumulator.flush} to record that a vehicle rendered via the retained
     * path this frame. Also records / logs the mode transition for the model.
     *
     * @param res the GeckoLib model resource (may be null if unavailable — then we skip)
     */
    public static void onRetainedFlush(ResourceLocation res) {
        if (!ENABLED) return;
        if (res == null) return;
        RETAINED_THIS_FRAME.add(res);
        statsFor(res).retainedFrameVehicles.incrementAndGet();
        recordMode(res, Mode.RETAINED);
    }

    /**
     * Called at the start of each frame (before any vehicle renders) to clear per-frame retained
     * vehicle counts.
     */
    public static void beginFrame() {
        if (!ENABLED) return;
        RETAINED_THIS_FRAME.clear();
        for (ModelStats s : MODELS.values()) {
            s.retainedFrameVehicles.set(0);
        }
    }

    // -------------------------------------------------------------------------
    // Mode tracking
    // -------------------------------------------------------------------------

    /**
     * Records that {@code res} is rendering via {@code mode} this frame; logs a one-time message
     * whenever the active mode changes.
     */
    private static void recordMode(ResourceLocation res, Mode mode) {
        ModelStats s = statsFor(res);
        if (s.lastMode != mode) {
            Mode prev = s.lastMode;
            s.lastMode = mode;
            WFCore.LOGGER.info("[KmodoDebug] {} mode: {} → {}",
                    res, prev == null ? "NONE" : prev, mode);
        }
    }

    // -------------------------------------------------------------------------
    // Dump
    // -------------------------------------------------------------------------

    /**
     * Logs a full human-readable summary of every tracked model to {@link WFCore#LOGGER}. Returns the
     * same text (without ANSI) for display in chat.
     */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== KmodoDebug dump ===\n");
        if (MODELS.isEmpty()) {
            sb.append("  (no models tracked yet — drive a vehicle)\n");
        }
        for (ModelStats s : MODELS.values()) {
            sb.append("  ").append(s.res).append('\n');
            Mode mode = s.lastMode;
            sb.append("    mode: ").append(mode == null ? "UNKNOWN" : mode).append('\n');

            // Flywheel stats
            if (s.flywheelBodyVertices > 0 || s.flywheelDynamicBoneCount > 0) {
                sb.append("    [Flywheel] body=").append(s.flywheelBodyVertices).append(" verts");
                sb.append(" | dynamic ").append(s.flywheelDynamicBoneCount).append(" bone(s)=")
                        .append(s.flywheelDynamicVertices).append(" verts");
                long totalVerts = s.flywheelBodyVertices + s.flywheelDynamicVertices;
                sb.append(" | GPU ").append(s.flywheelGpuBytes).append("B (stride ")
                        .append(FullVertexView.STRIDE).append("×").append(totalVerts).append(")\n");
                sb.append("    [Flywheel] live vehicles=").append(s.flywheelLiveInstances.get()).append('\n');
            } else {
                sb.append("    [Flywheel] not baked\n");
            }

            // Retained stats
            if (s.retainedVboCount > 0) {
                sb.append("    [Retained] ").append(s.retainedVboCount).append(" VBOs | ")
                        .append(s.retainedTotalVertices).append(" total verts\n");
                sb.append("    [Retained] vehicles this frame=")
                        .append(s.retainedFrameVehicles.get()).append('\n');
            } else {
                sb.append("    [Retained] not baked\n");
            }
        }
        sb.append("=== end ===");
        String text = sb.toString();
        WFCore.LOGGER.info("[KmodoDebug] {}", text);
        return text;
    }

    // -------------------------------------------------------------------------
    // Invalidation (call on resource reload)
    // -------------------------------------------------------------------------

    /**
     * Clears all per-model stats. Call together with the Kmodo mesh/model cache invalidations on
     * resource reload so stale stats are not shown.
     */
    public static void invalidateAll() {
        MODELS.clear();
        RETAINED_THIS_FRAME.clear();
    }
}
