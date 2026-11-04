package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.flansmod.warforge.api.NotificationStyle;
import com.flansmod.warforge.api.WarforgeAPI;
import com.norwood.wfcore.api.research.Research;

import java.util.UUID;

/**
 * WarForge toasts for WFCore machines. Callers gate on {@link WarforgeIntegration#isLoaded()}.
 */
public final class WarforgeNotifications {

    private static final int RESEARCH_DURATION_MS = 7000;

    private WarforgeNotifications() {}

    /** Server only; no-op on unclaimed chunks. */
    public static void researchCompleted(Level world, BlockPos pos, Research research) {
        if (world == null || world.isClientSide || pos == null || research == null) {
            return;
        }
        UUID factionId = WarforgeAPI.ownerOf(world.dimension(), new ChunkPos(pos));
        if (factionId == null) {
            return;
        }
        String name = Component.translatable(research.getNameKey()).getString();
        WarforgeAPI.notifyFaction(factionId,
                "wfcore_research_complete_" + research.getId(),
                Component.translatable("wfcore.notification.research_complete.title")
                        .append(": ")
                        .append(Component.translatable("wfcore.notification.research_complete.subtitle", name)),
                NotificationStyle.SUCCESS,
                RESEARCH_DURATION_MS);
    }
}
