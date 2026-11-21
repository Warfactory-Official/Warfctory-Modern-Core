package com.norwood.wfcore;

import com.norwood.wfcore.integration.gt.GTIntegration;
import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.Registrate;

/** The mod's registrate: GregTech's when GregTech is there (its machines and materials register through it), the plain one otherwise. */
final class WFRegistrate {
    private WFRegistrate() {
    }

    static AbstractRegistrate<?> create(String modId) {
        return GTIntegration.isLoaded() ? GtRegistrates.create(modId) : Registrate.create(modId);
    }

    /** Own class so the GregTech type is only resolved when it is chosen. */
    private static final class GtRegistrates {
        static AbstractRegistrate<?> create(String modId) {
            return com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate.create(modId);
        }
    }
}
