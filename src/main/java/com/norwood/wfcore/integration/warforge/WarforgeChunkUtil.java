package com.norwood.wfcore.integration.warforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import com.flansmod.warforge.common.ExplosionProtection;
import com.flansmod.warforge.server.Faction;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class WarforgeChunkUtil {

    private WarforgeChunkUtil() {
    }


    public static boolean canDestroyIn(@Nullable Player player, ServerLevel level, BlockPos pos) {
        UUID igniter = player == null ? Faction.nullUuid : player.getUUID();
        return !ExplosionProtection.isProtected(level, igniter, pos);
    }
}
