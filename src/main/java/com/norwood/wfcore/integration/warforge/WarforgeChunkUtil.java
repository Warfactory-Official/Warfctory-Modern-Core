package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import com.flansmod.warforge.common.ExplosionProtection;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.flansmod.warforge.server.FactionStorage;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class WarforgeChunkUtil {

    private WarforgeChunkUtil() {
    }


    public static boolean canDestroyIn(@Nullable Player player, ServerLevel level, BlockPos pos) {
        if (ownsChunkAsMember(player, level, pos)) {
            return true;
        }
        UUID igniter = player == null ? Faction.nullUuid : player.getUUID();
        return !ExplosionProtection.isProtected(level, igniter, pos);
    }

    private static boolean ownsChunkAsMember(@Nullable Player player, ServerLevel level, BlockPos pos) {
        if (player == null) {
            return false;
        }
        UUID factionId = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(level.dimension(), pos));
        if (factionId == null || Faction.nullUuid.equals(factionId) || FactionStorage.IsNeutralZone(factionId)) {
            return false;
        }
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        return faction != null && faction.isPlayerInFaction(player.getUUID());
    }
}
