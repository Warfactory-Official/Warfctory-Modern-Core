package com.norwood.wfcore.integration.tacz;

import net.minecraft.world.entity.player.Player;

import net.minecraftforge.fml.ModList;

import com.norwood.wfcore.WFCore;

import java.lang.reflect.Method;

/**
 * Optional-mod bridge to TACZ Tactical Breaching's gas-mask system.
 *
 * <p>Its {@code GasMaskHandler.hasWorkingGasMask(Player)} check (a gas mask in the head slot backed by a
 * non-empty filter) is package-private, so we bind it reflectively once and no-op if the mod is absent —
 * no compile-time dependency on the addon. Used by {@link com.norwood.wfcore.mixin.WFBallisticsGasMaskMixin}
 * so a working gas mask blocks wfballistics gas clouds.
 */
public final class TaczGasMaskCompat {

    private static final String MOD_ID = "tacz_tactical_breaching";
    private static boolean resolved;
    private static Method hasWorkingGasMask;

    private TaczGasMaskCompat() {}

    /** True if TACZ Tactical Breaching is loaded and {@code player} wears a gas mask with a working filter. */
    public static boolean hasWorkingGasMask(Player player) {
        Method m = resolve();
        if (m == null) {
            return false;
        }
        try {
            return (Boolean) m.invoke(null, player);
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method resolve() {
        if (!resolved) {
            resolved = true;
            if (ModList.get().isLoaded(MOD_ID)) {
                try {
                    Class<?> handler = Class.forName("com.victoriomods.taczballisticbreaching.GasMaskHandler");
                    Method m = handler.getDeclaredMethod("hasWorkingGasMask", Player.class);
                    m.setAccessible(true);
                    hasWorkingGasMask = m;
                } catch (Throwable t) {
                    WFCore.LOGGER.warn("[wfcore] TACZ Tactical Breaching is present but "
                            + "GasMaskHandler.hasWorkingGasMask(Player) could not be bound; gas masks will not "
                            + "block wfballistics gas", t);
                }
            }
        }
        return hasWorkingGasMask;
    }
}
