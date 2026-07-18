package com.norwood.wfcore.client.render.kmodo;

import net.minecraftforge.fml.ModList;

/**
 * Kmodo Accelerator — feature gates for retained vehicle rendering. Deliberately static/simple so the
 * render-thread hot path stays allocation-free.
 * <p>
 * Retained rendering draws each bone's cached bone-local {@code VertexBuffer} at GeckoLib's live per-bone
 * matrix instead of re-tessellating the mesh every frame. It is on for every vehicle by default (bone-local
 * geometry is pose-independent, so all animation states — turret slew, barrels, wheels — are preserved via the
 * live matrix). It falls back to stock GeckoLib tessellation whenever an Iris/Oculus shader pack is active,
 * because a raw {@code drawWithShader} bypasses the shader pack's gbuffer pipeline.
 */
public final class KmodoConfig {

    private KmodoConfig() {}

    private static volatile boolean RETAIN = true;
    private static volatile boolean FLYWHEEL = true;

    public static boolean retainEnabled() {
        return RETAIN;
    }

    public static void setRetain(boolean enabled) {
        RETAIN = enabled;
    }

    /**
     * Whether the top-tier Flywheel GPU-instanced path may engage (when Flywheel's backend is on). When true and
     * a vehicle's per-bone Flywheel model is baked, that vehicle is instanced and its vanilla/retained render is
     * suppressed; otherwise the vehicle falls back to the retained ({@link #retainEnabled}) path.
     */
    public static boolean flywheelEnabled() {
        return FLYWHEEL;
    }

    public static void setFlywheel(boolean enabled) {
        FLYWHEEL = enabled;
    }

    /** Raw VBO draws are only safe without a shader pack (they bypass Iris/Oculus gbuffers). */
    public static boolean rawDrawAllowed() {
        return !shaderPackActive();
    }

    private static Boolean irisLoaded;

    private static boolean shaderPackActive() {
        try {
            if (irisLoaded == null) {
                irisLoaded = ModList.get().isLoaded("oculus")
                        || ModList.get().isLoaded("iris")
                        || ModList.get().isLoaded("irisflw");
            }
            if (!irisLoaded) {
                return false;
            }
            // Oculus (Forge port of Iris) exposes the stable v0 API; older builds used the coderbot package.
            for (String className : new String[]{
                    "net.irisshaders.iris.api.v0.IrisApi",
                    "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    Class<?> api = Class.forName(className);
                    Object instance = api.getMethod("getInstance").invoke(null);
                    return (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
                } catch (ClassNotFoundException ignored) {
                    // try next candidate
                }
            }
        } catch (Throwable ignored) {
            // any reflection failure → assume no shader pack, use the fast path
        }
        return false;
    }
}
