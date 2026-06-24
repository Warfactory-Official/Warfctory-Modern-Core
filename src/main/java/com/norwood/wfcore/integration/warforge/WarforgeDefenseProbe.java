package com.norwood.wfcore.integration.warforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.flansmod.warforge.api.WarforgeAPI;
import com.flansmod.warforge.common.WarForgeConfig;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.blocks.IClaim;
import com.flansmod.warforge.common.util.DimBlockPos;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Samples the WarForge siege difficulty of a chunk, mirroring {@code Siege.GetAttackSuccessThreshold()}
 * ({@code mBaseDifficulty + mExtraDifficulty}) for the no-attacker case: base claim defence + per-member
 * reinforcement (capped) - adjacent friendly support + active chunk-reinforcer bonus.
 * <p>
 * Kept separate from the tool item so the hard {@code com.flansmod.warforge.*} references are linked only
 * when WarForge is present (the method signature exposes no WarForge types); callers gate on
 * {@link WarforgeIntegration#isLoaded()}.
 */
public final class WarforgeDefenseProbe {

    private static final int MEMBER_BONUS_CAP = 5;

    private WarforgeDefenseProbe() {}

    public static List<Component> sample(ServerLevel level, BlockPos at) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("wfcore.tool.siege_tester.header").withStyle(ChatFormatting.GOLD));

        DimChunkPos chunk = new DimChunkPos(level.dimension(), at);
        UUID factionId = WarForgeMod.FACTIONS.getClaim(chunk);
        if (factionId == null || Faction.nullUuid.equals(factionId)) {
            out.add(Component.translatable("wfcore.tool.siege_tester.unclaimed").withStyle(ChatFormatting.GRAY));
            return out;
        }
        Faction faction = WarForgeMod.FACTIONS.getFaction(factionId);
        if (faction == null) {
            out.add(Component.translatable("wfcore.tool.siege_tester.unclaimed").withStyle(ChatFormatting.GRAY));
            return out;
        }

        int base = defenceStrength(level, faction, chunk);
        int member = Math.min(MEMBER_BONUS_CAP, faction.members.size() * WarForgeConfig.SIEGE_DIFF_PER_MEMBER);
        int support = adjacentSupport(level, faction, factionId, chunk);
        int reinforce = WarforgeAPI.getReinforcementBonus(factionId, chunk);
        int total = base + member - support + reinforce;

        out.add(Component.translatable("wfcore.tool.siege_tester.faction", faction.name)
                .withStyle(ChatFormatting.AQUA));
        out.add(Component.translatable("wfcore.tool.siege_tester.base", base).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.members", member).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.support", support).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.reinforce", reinforce)
                .withStyle(reinforce > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.total", total).withStyle(ChatFormatting.YELLOW));
        return out;
    }

    /** Base claim defence of a chunk, matching {@code Siege}'s constructor (claim block, else claim type). */
    private static int defenceStrength(ServerLevel level, Faction faction, DimChunkPos chunk) {
        DimBlockPos pos = faction.getSpecificPosForClaim(chunk);
        if (pos != null) {
            BlockEntity te = level.getBlockEntity(pos.toRegularPos());
            if (te instanceof IClaim claim) {
                return claim.getDefenceStrength();
            }
        }
        return faction.getClaimType(chunk).defenceStrength;
    }

    /** Summed support strength of horizontally-adjacent chunks owned by the same faction (siege reduces it). */
    private static int adjacentSupport(ServerLevel level, Faction faction, UUID factionId, DimChunkPos chunk) {
        int support = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            DimChunkPos neighbor = chunk.Offset(dir, 1);
            if (!factionId.equals(WarForgeMod.FACTIONS.getClaim(neighbor))) {
                continue;
            }
            DimBlockPos pos = faction.getSpecificPosForClaim(neighbor);
            if (pos == null) {
                continue;
            }
            BlockEntity te = level.getBlockEntity(pos.toRegularPos());
            support += te instanceof IClaim claim ? claim.getSupportStrength() :
                    faction.getClaimType(neighbor).supportStrength;
        }
        return support;
    }
}
