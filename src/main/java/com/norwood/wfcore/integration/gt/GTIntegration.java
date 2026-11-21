package com.norwood.wfcore.integration.gt;

import net.neoforged.fml.ModList;

/**
 * Gate for the optional GregTech (GTCEu) integration. GregTech is a soft dependency: everything that needs it (machines, materials, pipes,
 * covers, research, GT recipe types, the ore-vein worldgen hooks, the compute and radar tooling that reads GT machines) is created and
 * registered only when {@link #isLoaded()}; without it those parts are quietly absent and the rest of the mod runs.
 *
 * <p>Callers must check {@link #isLoaded()} before touching any class that imports {@code com.gregtechceu}, {@code com.lowdragmc} or
 * {@code brachy.modularui}; nothing outside the gated paths may name one, or the class fails to load.
 */
public final class GTIntegration {
    public static final String MOD_ID = "gtceu";

    private GTIntegration() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }
}
