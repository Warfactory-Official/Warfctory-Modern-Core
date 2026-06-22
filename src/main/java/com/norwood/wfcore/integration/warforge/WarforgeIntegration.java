package com.norwood.wfcore.integration.warforge;

import net.minecraftforge.fml.ModList;

/**
 * Gate for the optional WarForge integration. Callers must check {@link #isLoaded()} before touching any
 * {@code com.flansmod.warforge.*} or {@link FactionLibraryAccess} member so those classes are never loaded
 * when WarForge is absent.
 */
public final class WarforgeIntegration {

    public static final String MOD_ID = "warforge";

    private static Boolean loaded;

    private WarforgeIntegration() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }
}
