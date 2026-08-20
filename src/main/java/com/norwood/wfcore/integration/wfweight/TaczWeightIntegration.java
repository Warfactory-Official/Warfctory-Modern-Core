package com.norwood.wfcore.integration.wfweight;

import net.neoforged.fml.ModList;

import com.norwood.wfcore.WFCore;
import com.warfactory.ultimateweight.api.WeightCompatRegistry;

/** Registers wfcore's Ultimate Weight compat providers, when both mods are present. */
public final class TaczWeightIntegration {

    private TaczWeightIntegration() {}

    public static void register() {
        if (!WeightGate.isLoaded() || !ModList.get().isLoaded("tacz")) {
            return;
        }
        WeightCompatRegistry.register(new TaczLoadedAmmoWeightProvider());
        WFCore.LOGGER.info("Weight: TACZ loaded-ammo provider registered ({} id override(s))", TaczWeights.size());
    }
}
