package com.norwood.wfcore.integration.wfweight;

import com.norwood.wfcore.WFCore;
import com.warfactory.ultimateweight.api.WeightCompatRegistry;
import net.minecraftforge.fml.ModList;

public final class SbwWeightIntegration {

    private SbwWeightIntegration() {}

    public static void register() {
        if (!ModList.get().isLoaded("wfweight") || !ModList.get().isLoaded("superbwarfare")) {
            return;
        }
        WeightCompatRegistry.register(new SbwLoadedLauncherWeightProvider());
        WFCore.LOGGER.info("Weight: SBW loaded-launcher provider registered (IGLA + Javelin)");
    }
}
