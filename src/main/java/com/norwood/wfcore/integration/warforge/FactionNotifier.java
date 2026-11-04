package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.flansmod.warforge.api.NotificationStyle;
import com.flansmod.warforge.api.WarforgeAPI;

import java.util.UUID;

/**
 * Sends WarForge team notifications. Load only behind {@link WarforgeIntegration#isLoaded()}.
 */
public final class FactionNotifier {

    /** Stable token so repeated warnings refresh a single banner instead of stacking a new one each time. */
    private static final String MISSILE_TOKEN = "wfcore.interceptor.incoming";
    private static final int WARN_DURATION_MS = 6000;

    private FactionNotifier() {}

    /** No-op for a null id or unknown faction. */
    public static void warnIncomingMissile(UUID factionId, BlockPos batteryPos) {
        if (factionId == null) {
            return;
        }
        WarforgeAPI.notifyFaction(factionId, MISSILE_TOKEN,
                Component.literal("Incoming Missile: interceptor battery at " + batteryPos.getX() + ", "
                        + batteryPos.getZ() + " engaging"),
                NotificationStyle.DANGER, WARN_DURATION_MS);
    }
}
