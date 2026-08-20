package com.norwood.wfcore.integration.wfweight;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-gun and per-round weights keyed by TACZ id, set from the {@code WFWeight} KubeJS binding. Unset ids
 * fall back to Ultimate Weight's configured weight for the underlying item — see
 * {@link com.norwood.wfcore.integration.kubejs.WFWeightBindings} for why they cannot be expressed there.
 */
public final class TaczWeights {

    private static final Map<ResourceLocation, Double> GUNS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Double> AMMO = new ConcurrentHashMap<>();

    private TaczWeights() {}

    /** Weight in kg of the gun {@code gunId}, empty. */
    public static void gun(ResourceLocation gunId, double kg) {
        put(GUNS, gunId, kg);
    }

    /** Weight in kg of a single round of {@code ammoId}. */
    public static void ammo(ResourceLocation ammoId, double kg) {
        put(AMMO, ammoId, kg);
    }

    private static void put(Map<ResourceLocation, Double> into, ResourceLocation id, double kg) {
        if (id != null && kg > 0.0D) {
            into.put(id, kg);
        }
    }

    /** The override for {@code gunId}, or {@code fallback} if the pack set none. */
    public static double gunOr(ResourceLocation gunId, double fallback) {
        Double kg = gunId == null ? null : GUNS.get(gunId);
        return kg != null ? kg : fallback;
    }

    /** The override for {@code ammoId}, or {@code fallback} if the pack set none. */
    public static double ammoOr(ResourceLocation ammoId, double fallback) {
        Double kg = ammoId == null ? null : AMMO.get(ammoId);
        return kg != null ? kg : fallback;
    }

    public static int size() {
        return GUNS.size() + AMMO.size();
    }
}
