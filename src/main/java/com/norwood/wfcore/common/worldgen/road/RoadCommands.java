package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.mojang.brigadier.context.CommandContext;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/** {@code /wfcore_road status | info [pos]}: what the generator decided at a column. */
public final class RoadCommands {

    public static final RoadCommands INSTANCE = new RoadCommands();

    private static final int SEARCH_CHUNKS = 2;
    private static final int GRADE_WINDOW = 20;

    private RoadCommands() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_road")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(RoadCommands::status))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, new ColumnPos(
                                net.minecraft.util.Mth.floor(ctx.getSource().getPosition().x),
                                net.minecraft.util.Mth.floor(ctx.getSource().getPosition().z))))
                        .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                                .executes(ctx -> info(ctx, ColumnPosArgument.getColumnPos(ctx, "pos"))))));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        int types = level.registryAccess().registry(RoadRegistries.ROAD_TYPE).map(r -> r.size()).orElse(0);
        int networks = level.registryAccess().registry(RoadRegistries.ROAD_NETWORK).map(r -> r.size()).orElse(0);
        RoadIndex index = RoadIndexManager.get(level);
        long[] modes = index.modeHistogram();
        long total = Math.max(1, modes[RoadProfile.GROUND] + modes[RoadProfile.BRIDGE] + modes[RoadProfile.TUNNEL]);
        source.sendSuccess(() -> Component.literal(String.format(
                "%s: %d road type(s), %d network(s), %d link(s), %,d station(s)%n"
                        + "  ground %.0f%%, bridge %.0f%%, tunnel %.0f%%",
                level.dimension().location(), types, networks, index.linkCount(), index.stationCount(),
                100.0 * modes[RoadProfile.GROUND] / total,
                100.0 * modes[RoadProfile.BRIDGE] / total,
                100.0 * modes[RoadProfile.TUNNEL] / total))
                .withStyle(ChatFormatting.GRAY), false);
        return index.linkCount();
    }

    private static int info(CommandContext<CommandSourceStack> ctx, ColumnPos column) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        RoadIndex index = RoadIndexManager.get(level);
        if (index.isEmpty()) {
            source.sendFailure(Component.literal("No road network is loaded in " + level.dimension().location()));
            return 0;
        }

        RoadIndex.LinkGeometry nearest = null;
        int nearestStation = -1;
        double nearestDistance = Double.MAX_VALUE;
        IntArrayList runs = new IntArrayList();
        for (int dx = -SEARCH_CHUNKS; dx <= SEARCH_CHUNKS; dx++) {
            for (int dz = -SEARCH_CHUNKS; dz <= SEARCH_CHUNKS; dz++) {
                index.collectRuns((column.x() >> 4) + dx, (column.z() >> 4) + dz, runs);
                for (int i = 0; i < runs.size(); i++) {
                    int run = runs.getInt(i);
                    RoadIndex.LinkGeometry link = index.link(index.runLink(run));
                    for (int station = index.runFirst(run); station <= index.runLast(run); station++) {
                        double ax = link.x(station) - column.x();
                        double az = link.z(station) - column.z();
                        double distance = ax * ax + az * az;
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = link;
                            nearestStation = station;
                        }
                    }
                }
            }
        }
        nearestDistance = Math.sqrt(nearestDistance);
        if (nearest == null) {
            source.sendSuccess(() -> Component.literal("No road within " + (SEARCH_CHUNKS * 16) + " blocks")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        RoadIndex.LinkGeometry link = nearest;
        int station = nearestStation;
        double offset = nearestDistance;
        String typeId = level.registryAccess().registry(RoadRegistries.ROAD_TYPE)
                .flatMap(registry -> registry.getResourceKey(index.type(link)))
                .map(key -> key.location().toString())
                .orElse("<unregistered>");
        String mode = switch (link.mode(station)) {
            case RoadProfile.BRIDGE -> "bridge";
            case RoadProfile.TUNNEL -> link.portal(station) ? "tunnel (portal)" : "tunnel";
            default -> "ground";
        };
        // Whole-block decks 1 station apart: an adjacent-station grade reads only 0% or 100%.
        int previous = Math.max(0, station - GRADE_WINDOW);
        int next = Math.min(link.stationCount() - 1, station + GRADE_WINDOW);
        double grade = next == previous ? 0.0
                : (double) (link.deck(next) - link.deck(previous)) / (next - previous);

        source.sendSuccess(() -> Component.literal(String.format(
                "%s | station %d/%d, %.1f blocks off centre%n  deck y=%d, terrain y=%d, water y=%d, %s%n"
                        + "  grade %.1f%% (budget %.1f%%), width %d + %d shoulder",
                typeId, station, link.stationCount() - 1, offset,
                link.deck(station), link.ground(station), link.water(station), mode,
                grade * 100.0, link.spec().grade().max() * 100.0,
                link.spec().width(), link.spec().shoulder()))
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
