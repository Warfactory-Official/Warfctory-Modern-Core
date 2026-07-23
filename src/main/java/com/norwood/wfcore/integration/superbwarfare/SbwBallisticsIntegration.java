package com.norwood.wfcore.integration.superbwarfare;

import net.minecraftforge.fml.ModList;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.ballistics.BallisticsRegistry;

public final class SbwBallisticsIntegration {

    private static final String SBW_MOD_ID = "superbwarfare";

    private SbwBallisticsIntegration() {}

    public static void register() {
        if (ModList.get().isLoaded(SBW_MOD_ID)) {
            BallisticsRegistry.register(new SbwBallisticsAdapter());
            WFCore.LOGGER.info("Ballistics: Superb Warfare adapter enabled (mod '{}' present)", SBW_MOD_ID);
        } else {
            WFCore.LOGGER.debug("Ballistics: Superb Warfare not loaded, skipping SBW adapter");
        }
    }
}
