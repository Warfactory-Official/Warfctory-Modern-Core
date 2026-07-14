package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketClientNotification;
import com.flansmod.warforge.server.Faction;

import java.util.UUID;

/**
 * Sends WarForge team notifications (the on-screen banner every faction member sees). All
 * {@code com.flansmod.warforge.*} references are isolated here, so this class is only loaded — and therefore
 * only touches WarForge — behind a {@link WarforgeIntegration#isLoaded()} check.
 */
public final class FactionNotifier {

    /** Stable token so repeated warnings refresh a single banner instead of stacking a new one each time. */
    private static final String MISSILE_TOKEN = "wfcore.interceptor.incoming";
    private static final int WARN_DURATION_MS = 6000;

    private FactionNotifier() {}

    /**
     * Warns every online member of the faction identified by {@code factionId} that a missile is inbound and
     * a battery is engaging it. No-op if the id is null/nullUuid or no such faction exists (e.g. the battery
     * sits on unclaimed land).
     */
    public static void warnIncomingMissile(UUID factionId, BlockPos batteryPos) {
        if (factionId == null || factionId.equals(Faction.nullUuid)) {
            return;
        }
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        if (faction == null) {
            return;
        }
        WarForgeMod.notifyFaction(faction, MISSILE_TOKEN,
                "Incoming Missile",
                "Interceptor battery at " + batteryPos.getX() + ", " + batteryPos.getZ() + " engaging",
                PacketClientNotification.COLOR_DANGER, WARN_DURATION_MS);
    }
}
