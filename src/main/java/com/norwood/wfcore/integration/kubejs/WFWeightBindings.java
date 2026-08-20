package com.norwood.wfcore.integration.kubejs;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.integration.wfweight.TaczWeights;

/**
 * KubeJS binding exposed as {@code WFWeight} in <b>startup scripts</b>: per-gun and per-round weights for
 * TACZ, which Ultimate Weight's own config cannot express.
 *
 * <p>
 * UW resolves weights by item id, but TACZ registers one item for every gun
 * ({@code tacz:modern_kinetic_gun}) and one for every round ({@code tacz:ammo}) — which model it is lives in
 * a data component. So {@code tacz:modern_kinetic_gun: 3.5} in weight_config applies to a pistol and a
 * heavy machine gun alike. These keep the id resolution:
 *
 * <pre>{@code
 * // startup_scripts/wfcore_weights.js
 * WFWeight
 *     .gun('tacz:ak47', 3.47)        // empty weight, kg
 *     .ammo('tacz:762x39', 0.016)    // per round, kg
 * }</pre>
 *
 * <p>
 * Anything left unset falls back to Ultimate Weight's configured weight for the underlying item, so a pack
 * can set a sensible blanket value there and override only the guns it cares about. A gun's total is its
 * own weight plus its currently-chambered rounds.
 */
public class WFWeightBindings {

    /** Empty weight in kg of the TACZ gun {@code gunId}, e.g. {@code 'tacz:ak47'}. */
    public WFWeightBindings gun(String gunId, double kg) {
        ResourceLocation id = ResourceLocation.tryParse(gunId);
        if (id == null || kg <= 0.0D) {
            WFCore.LOGGER.warn("[WFWeight] ignoring gun('{}', {})", gunId, kg);
            return this;
        }
        TaczWeights.gun(id, kg);
        return this;
    }

    /** Weight in kg of a single round of {@code ammoId}, e.g. {@code 'tacz:762x39'}. */
    public WFWeightBindings ammo(String ammoId, double kg) {
        ResourceLocation id = ResourceLocation.tryParse(ammoId);
        if (id == null || kg <= 0.0D) {
            WFCore.LOGGER.warn("[WFWeight] ignoring ammo('{}', {})", ammoId, kg);
            return this;
        }
        TaczWeights.ammo(id, kg);
        return this;
    }
}
