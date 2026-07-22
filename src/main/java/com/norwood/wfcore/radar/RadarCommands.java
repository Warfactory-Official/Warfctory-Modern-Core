package com.norwood.wfcore.radar;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.RadarClustering.DataPoint;
import com.norwood.wfcore.radar.RadarClustering.TargetType;
import com.norwood.wfcore.radar.data.RadarScanData;
import com.norwood.wfcore.radar.math.IntCoord2;
import it.unimi.dsi.fastutil.longs.Long2IntMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;


public class RadarCommands {

    public static final RadarCommands INSTANCE = new RadarCommands();

    private static final String EXPORT_DIR = "wfcore_radar_exports";

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_radar")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("scan")
                        .executes(ctx -> runScanExport(ctx, RadarConfig.getEps(), RadarConfig.getMinPts()))
                        .then(Commands.argument("eps", IntegerArgumentType.integer(1))
                                .then(Commands.argument("minPts", IntegerArgumentType.integer(1))
                                        .executes(ctx -> runScanExport(ctx,
                                                IntegerArgumentType.getInteger(ctx, "eps"),
                                                IntegerArgumentType.getInteger(ctx, "minPts"))))))
                .then(Commands.literal("datastick")
                        .executes(ctx -> runDataStick(ctx, RadarConfig.getEps(), RadarConfig.getMinPts()))
                        .then(Commands.argument("eps", IntegerArgumentType.integer(1))
                                .then(Commands.argument("minPts", IntegerArgumentType.integer(1))
                                        .executes(ctx -> runDataStick(ctx,
                                                IntegerArgumentType.getInteger(ctx, "eps"),
                                                IntegerArgumentType.getInteger(ctx, "minPts"))))))
                .then(Commands.literal("targets")
                        .executes(this::runTargets)));
    }

    /** Scans all GT machines + players, runs DBSCAN, and writes the result to a JSON file under the world save. */
    private int runScanExport(CommandContext<CommandSourceStack> ctx, int eps, int minPts) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        MinecraftServer server = level.getServer();

        Map<IntCoord2, DataPoint> targets = RadarClustering.collectPlayers(level);
        int playerCount = targets.size();

        // Capture everything the (off-thread) writer needs while we are still on the server thread.
        String dimId = level.dimension().location().toString();
        long gameTime = level.getGameTime();
        long generatedAt = System.currentTimeMillis();

        src.sendSuccess(() -> Component.literal("§7Scanning §f" + dimId + "§7 for all GregTech machines "
                + "(eps=" + eps + ", minPts=" + minPts + "); reading region files, this can take a moment..."),
                false);

        RadarChunkParser.scanDimension(level, RadarChunkParser::gtMachineValue)
                .thenCompose(machineMap -> {
                    addMachines(targets, machineMap);
                    return RadarClustering.calculateDBSCAN(targets, eps, minPts);
                })
                .whenComplete((clusters, err) -> {
                    if (err != null) {
                        WFCore.LOGGER.error("Radar scan-export command failed", err);
                        server.execute(() -> src.sendFailure(Component.literal("Radar scan failed: " + err)));
                        return;
                    }
                    if (targets.isEmpty()) {
                        server.execute(() -> src.sendFailure(Component.literal("No radar targets found in " + dimId
                                + " (no players, no GregTech machines on disk).")));
                        return;
                    }
                    int machineCount = targets.size() - playerCount;
                    try {
                        JsonObject json = RadarScanExporter.toJson(dimId, gameTime, generatedAt, targets, clusters,
                                eps, minPts);
                        Path out = writeExport(server, dimId, gameTime, RadarScanExporter.toPrettyString(json));
                        int clusterCount = clusters.size();
                        server.execute(() -> src.sendSuccess(() -> Component.literal("§aScan complete: §f"
                                + clusterCount + "§a cluster(s) from §f" + targets.size() + "§a target(s) (§f"
                                + playerCount + "§a player(s), §f" + machineCount + "§a machine(s)).\n§7Wrote §f"
                                + out), true));
                    } catch (IOException e) {
                        WFCore.LOGGER.error("Radar scan-export write failed", e);
                        server.execute(() -> src.sendFailure(Component.literal("Failed to write scan JSON: " + e)));
                    }
                });
        return 1;
    }

    /** Scans all GT machines + players, runs DBSCAN, stores the scan, and hands the caller a data stick. */
    private int runDataStick(CommandContext<CommandSourceStack> ctx, int eps, int minPts) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            src.sendFailure(Component.literal("This command must be run by a player (it writes to your inventory)."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        MinecraftServer server = level.getServer();

        Map<IntCoord2, DataPoint> targets = RadarClustering.collectPlayers(level);
        String dimId = level.dimension().location().toString();

        src.sendSuccess(() -> Component.literal("§7Scanning §f" + dimId + "§7 for all GregTech machines for a "
                + "data stick (eps=" + eps + ", minPts=" + minPts + ")..."), false);

        RadarChunkParser.scanDimension(level, RadarChunkParser::gtMachineValue)
                .thenCompose(machineMap -> {
                    addMachines(targets, machineMap);
                    return RadarClustering.calculateDBSCAN(targets, eps, minPts);
                })
                .thenAccept(clusters -> server.execute(() -> {
                    UUID id = UUID.randomUUID();
                    RadarScanData.get(level).addScan(id, clusters);
                    ItemStack stick = RadarDataStick.createDataStick(id);
                    if (!player.getInventory().add(stick)) {
                        player.drop(stick, false);
                    }
                    src.sendSuccess(() -> Component.literal("§aWrote a printer-ready data stick (§f" + clusters.size()
                            + "§a cluster(s) from §f" + targets.size() + "§a targets) to your inventory.\n"
                            + "§7Scan id: §f" + id), true);
                }))
                .exceptionally(ex -> {
                    WFCore.LOGGER.error("Radar datastick command failed", ex);
                    server.execute(() -> src.sendFailure(Component.literal("Radar scan failed: " + ex)));
                    return null;
                });
        return 1;
    }

    /** Counts what a scan would feed into DBSCAN: online players + every GT machine in the dimension. */
    private int runTargets(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        MinecraftServer server = level.getServer();
        String dimId = level.dimension().location().toString();
        int playerCount = level.players().size();

        src.sendSuccess(() -> Component.literal("§7Scanning §f" + dimId + "§7 for all GregTech machines..."), false);

        RadarChunkParser.scanDimension(level, RadarChunkParser::gtMachineValue).whenComplete((machineMap, err) -> {
            if (err != null) {
                WFCore.LOGGER.error("Radar targets command failed", err);
                server.execute(() -> src.sendFailure(Component.literal("Radar target scan failed: " + err)));
                return;
            }
            int machineCount = machineMap.size();
            server.execute(() -> src.sendSuccess(() -> Component.literal(String.format(
                    "§bRadar targets in %s:§r %d total — §a%d player(s)§r, §e%d GT machine(s)§r. "
                            + "(config defaults: eps=%d, minPts=%d)",
                    dimId, playerCount + machineCount, playerCount, machineCount,
                    RadarConfig.getEps(), RadarConfig.getMinPts())), false));
        });
        return 1;
    }

    /** Merges scanned machine positions (packed x,z -> richness) into {@code targets} as STRUCTURE datapoints. */
    private static void addMachines(Map<IntCoord2, DataPoint> targets, Long2IntMap machineMap) {
        for (Long2IntMap.Entry entry : machineMap.long2IntEntrySet()) {
            targets.put(new IntCoord2(entry.getLongKey()), new DataPoint(TargetType.STRUCTURE, entry.getIntValue()));
        }
    }

    private static Path writeExport(MinecraftServer server, String dimId, long gameTime, String json)
            throws IOException {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve(EXPORT_DIR);
        Files.createDirectories(dir);
        String safeDim = dimId.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path out = dir.resolve("scan-" + safeDim + "-" + gameTime + ".json");
        Files.writeString(out, json);
        return out.toAbsolutePath();
    }
}
