package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.server.Faction;

import java.util.UUID;

public final class WarforgeChunkUtil {

    public static boolean canDestroyIn(Player player, ServerLevel level, BlockPos pos) {
        UUID chunkFactionUUID = WarForgeMod.FACTIONS.getClaim(new DimBlockPos(level.dimension(), pos));
        if (chunkFactionUUID.equals(Faction.nullUuid))
            return true;
        UUID playerFactionUUID = WarForgeMod.FACTIONS.getFactionOfPlayer(player.getUUID()).uuid;
        return chunkFactionUUID.equals(playerFactionUUID);
    }
}
