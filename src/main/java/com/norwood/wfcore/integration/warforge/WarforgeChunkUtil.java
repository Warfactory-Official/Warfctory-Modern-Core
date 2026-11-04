package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import com.flansmod.warforge.api.WarforgeAPI;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class WarforgeChunkUtil {

    private WarforgeChunkUtil() {
    }

    public static boolean canDestroyIn(@Nullable Player player, ServerLevel level, BlockPos pos) {
        if (ownsChunkAsMember(player, level, pos)) {
            return true;
        }
        return !WarforgeAPI.isExplosionProtected(level, pos, player == null ? null : player.getUUID());
    }

    private static boolean ownsChunkAsMember(@Nullable Player player, ServerLevel level, BlockPos pos) {
        if (player == null) {
            return false;
        }
        UUID factionId = WarforgeAPI.ownerOf(level.dimension(), new ChunkPos(pos));
        return factionId != null && WarforgeAPI.isMember(factionId, player.getUUID());
    }
}
