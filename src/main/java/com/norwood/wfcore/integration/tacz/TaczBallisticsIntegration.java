package com.norwood.wfcore.integration.tacz;

import net.minecraftforge.fml.ModList;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.ballistics.BallisticsRegistry;

public final class TaczBallisticsIntegration {

    public static final String MOD_ID = "tacz";

    private TaczBallisticsIntegration() {}

    public static void register() {
        if (ModList.get() != null && ModList.get().isLoaded(MOD_ID)) {
            BallisticsRegistry.register(new TaczBallisticsAdapter());
            WFCore.LOGGER.info("Ballistics: TACZ integration active (registered tacz_kinetic_bullet adapter)");
        } else {
            WFCore.LOGGER.debug("Ballistics: TACZ not loaded, skipping TACZ ballistics adapter");
        }
    }
}
