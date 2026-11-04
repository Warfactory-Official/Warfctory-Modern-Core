package com.norwood.wfcore.integration.warforge;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import com.flansmod.warforge.api.SiegeDefence;
import com.flansmod.warforge.api.WarforgeAPI;

import java.util.ArrayList;
import java.util.List;

/** Siege difficulty of a chunk with no attacker. Callers gate on {@link WarforgeIntegration#isLoaded()}. */
public final class WarforgeDefenseProbe {

    private WarforgeDefenseProbe() {}

    public static List<Component> sample(ServerLevel level, BlockPos at) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("wfcore.tool.siege_tester.header").withStyle(ChatFormatting.GOLD));

        ChunkPos chunk = new ChunkPos(at);
        SiegeDefence defence = WarforgeAPI.siegeDefenceAt(level.dimension(), chunk);
        if (defence == null) {
            out.add(Component.translatable("wfcore.tool.siege_tester.unclaimed").withStyle(ChatFormatting.GRAY));
            return out;
        }

        String name = WarforgeAPI.factionName(WarforgeAPI.ownerOf(level.dimension(), chunk));
        out.add(Component.translatable("wfcore.tool.siege_tester.faction", name).withStyle(ChatFormatting.AQUA));
        out.add(Component.translatable("wfcore.tool.siege_tester.base", defence.base()).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.members", defence.member())
                .withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.support", defence.adjacentSupport())
                .withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.reinforce", defence.reinforcement())
                .withStyle(defence.reinforcement() > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        out.add(Component.translatable("wfcore.tool.siege_tester.total", defence.total())
                .withStyle(ChatFormatting.YELLOW));
        return out;
    }
}
