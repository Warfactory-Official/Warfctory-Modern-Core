package com.norwood.wfcore.client.render.vehicle;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;

/**
 * Feature gates for the retained-vehicle renderers. Kept deliberately static/simple so the render-thread hot
 * path stays allocation-free.
 * <ul>
 *   <li><b>Strategy 1</b> (idle static swap) is on for every vehicle by default — it is safe and general and
 *       only replaces geometry that is provably not animating.</li>
 *   <li><b>Strategy 2</b> (per-bone retained draw of <em>active</em> vehicles) is opt-in per entity type. The
 *       default allow-list is empty because the mid-batch raw draw needs in-client validation per model; add
 *       an entity-type id here once a vehicle is confirmed to render correctly.</li>
 * </ul>
 * Both strategies fall back to stock GeckoLib rendering whenever an Iris/Oculus shader pack is active, because
 * a raw {@code drawWithShader} bypasses the shader pack's gbuffer pipeline.
 */
public final class WFVehicleRenderConfig {

    private WFVehicleRenderConfig() {}

    /** Ticks a vehicle is held "active" after its last active frame (debounce for the static swap). */
    public static final int IDLE_HOLD_TICKS = 20;

    private static volatile boolean STATIC_SWAP = true;

    /** Entity-type ids allowed to use the per-bone retained path. Empty = Strategy 2 dormant. */
    private static final Set<ResourceLocation> PER_BONE_MODELS = Set.of(
            // Populate after in-client validation, e.g.:
            // new ResourceLocation("superbwarfare", "t_90a"),
            // new ResourceLocation("superbwarfare", "m_1a_2")
    );

    public static boolean staticSwapEnabled(VehicleEntity entity) {
        return STATIC_SWAP;
    }

    public static void setStaticSwap(boolean enabled) {
        STATIC_SWAP = enabled;
    }

    public static boolean perBoneEnabled(VehicleEntity entity) {
        if (PER_BONE_MODELS.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && PER_BONE_MODELS.contains(id);
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
