package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketClientNotification;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.norwood.wfcore.api.research.Research;

import java.util.UUID;

/**
 * Sends WarForge toast notifications on behalf of WFCore machines. The hard {@code com.flansmod.warforge}
 * imports are safe because every caller gates on {@link WarforgeIntegration#isLoaded()} first.
 */
public final class WarforgeNotifications {

    private static final int RESEARCH_DURATION_MS = 7000;

    private WarforgeNotifications() {}

    /**
     * Toasts the faction that owns the controller's chunk that a research just finished. Server-side only; a
     * no-op when the chunk is unclaimed (no owning faction to notify).
     */
    public static void researchCompleted(Level world, BlockPos pos, Research research) {
        if (world == null || world.isClientSide || pos == null || research == null) {
            return;
        }
        UUID factionId = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(world.dimension(), pos));
        if (factionId == null || Faction.nullUuid.equals(factionId)) {
            return;
        }
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        if (faction == null) {
            return;
        }
        String name = Component.translatable(research.getNameKey()).getString();
        WarForgeMod.FACTIONS.sendNotificationToFaction(faction,
                "wfcore_research_complete_" + research.getId(),
                Component.translatable("wfcore.notification.research_complete.title").getString(),
                Component.translatable("wfcore.notification.research_complete.subtitle", name).getString(),
                PacketClientNotification.COLOR_SUCCESS,
                RESEARCH_DURATION_MS);
    }
}
