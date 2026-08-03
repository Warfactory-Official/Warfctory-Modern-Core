package com.norwood.wfcore.integration.wfweight;

import com.norwood.wfcore.WFCore;
import com.warfactory.ultimateweight.api.WeightCompatRegistry;

public final class SbwWeightIntegration {

    private SbwWeightIntegration() {}

    public static void register() {
        WeightCompatRegistry.register(new SbwLoadedLauncherWeightProvider());
        WFCore.LOGGER.info("Weight: SBW loaded-launcher provider registered (IGLA + Javelin)");
    }
}
