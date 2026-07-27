package com.norwood.wfcore.radar;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
                        .executes(this::runTargets))
                .then(Commands.literal("list")
                        .executes(this::runList))
                .then(Commands.literal("id")
                        .executes(this::runId)));
    }

    /** Reach (blocks) the {@code id} lookup ray-casts to find the block the caller is looking at. */
    private static final double PICK_RANGE = 6.0;

    /**
     * Reports the registry id — and current radar status — of the block the caller is looking at. For GregTech
     * machines the block registry id is exactly the whitelist key, so the printed id is the string to paste into
     * {@code wfcore-radar.toml} or a {@code WFRadar.whitelist(...)} / {@code removeFromWhitelist(...)} call.
     */
    private int runId(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            src.sendFailure(Component.literal("This command must be run by a player (it reads what you're looking at)."));
            return 0;
        }

        HitResult hit = player.pick(PICK_RANGE, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            src.sendFailure(Component.literal("Look directly at a block within " + (int) PICK_RANGE
                    + " blocks and run this again."));
            return 0;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState state = player.level().getBlockState(pos);
        Block block = state.getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);

        // The whitelist is keyed by block registry id; for a GregTech machine the definition id equals it.
        ResourceLocation machineId = block instanceof MetaMachineBlock machine ? machine.definition.getId() : null;
        String kind = block instanceof MetaMachineBlock
                ? "GregTech machine [" + bucketName(bucketOf(blockId)) + "]"
                : "not a GregTech machine";
        boolean whitelisted = RadarConfig.isWhitelisted(blockId);
        int value = RadarConfig.getValue(blockId);

        WFCore.LOGGER.info("Radar id-lookup at {}: block={} machine={} ({}) whitelisted={}{}",
                pos.toShortString(), blockId, machineId, kind, whitelisted,
                whitelisted ? " value=" + value : "");

        src.sendSuccess(() -> Component.literal("§bRadar id:§r §f" + blockId + " §7(" + kind + ")"), false);
        if (machineId != null && !machineId.equals(blockId)) {
            src.sendSuccess(() -> Component.literal("§7  machine definition id: §f" + machineId), false);
        }
        if (whitelisted) {
            src.sendSuccess(() -> Component.literal("§a  ✔ radar-detectable §7(richness §e" + value + "§7)"), false);
        } else {
            src.sendSuccess(() -> Component.literal("§c  ✘ not detectable §7— add with "
                    + "§fWFRadar.whitelist('" + blockId + "')"), false);
        }
        return 1;
    }

    /**
     * Dumps the radar target whitelist — every machine/block the live radar can detect — to the server console
     * (full list, annotated with each GregTech machine's voltage tier) and echoes a summary plus a short preview
     * back to the caller. This reflects the final baked whitelist, so KubeJS bulk rules such as
     * {@code WFRadar.whitelistMachinesAtLeast('hv')} show up as their individual expanded entries; the per-tier
     * tally makes such a rule easy to sanity-check at a glance.
     */
    private int runList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        List<Map.Entry<ResourceLocation, Integer>> targets = RadarConfig.snapshot();

        // Tally by bucket (voltage tier, MULTIBLOCK, or NON_MACHINE) so a tier-based bulk config is verifiable.
        Int2IntMap tally = new Int2IntOpenHashMap();
        tally.defaultReturnValue(0);

        WFCore.LOGGER.info("Radar-detectable whitelist: {} entr{}", targets.size(), targets.size() == 1 ? "y" : "ies");
        for (Map.Entry<ResourceLocation, Integer> e : targets) {
            int bucket = bucketOf(e.getKey());
            tally.put(bucket, tally.get(bucket) + 1);
            if (bucket != BUCKET_NON_MACHINE) {
                WFCore.LOGGER.info("  {} = {}  [{}]", e.getKey(), e.getValue(), bucketName(bucket));
            } else {
                WFCore.LOGGER.info("  {} = {}", e.getKey(), e.getValue());
            }
        }

        if (targets.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§eRadar whitelist is empty — no machines are detectable."), false);
            return 0;
        }

        String tierSummary = summarise(tally);
        WFCore.LOGGER.info("Radar whitelist by tier: {}", tierSummary);

        src.sendSuccess(() -> Component.literal("§b" + targets.size()
                + "§r radar-detectable machine(s) — full list written to the server console."), false);
        src.sendSuccess(() -> Component.literal("§7by tier: §f" + tierSummary), false);
        int shown = 0;
        for (Map.Entry<ResourceLocation, Integer> e : targets) {
            if (shown >= 20) {
                int remaining = targets.size() - shown;
                src.sendSuccess(() -> Component.literal("§7  …and " + remaining + " more (see console)."), false);
                break;
            }
            shown++;
            int bucket = bucketOf(e.getKey());
            String tag = bucket != BUCKET_NON_MACHINE ? " §8[" + bucketName(bucket) + "]" : "";
            src.sendSuccess(() -> Component.literal("§7  • §f" + e.getKey() + " §7= §e" + e.getValue() + tag), false);
        }
        return targets.size();
    }

    private static final int BUCKET_NON_MACHINE = -1;
    private static final int BUCKET_MULTIBLOCK = -2;

    /**
     * Classifies {@code id} into a tally bucket: its voltage tier ({@code >= 0}), {@link #BUCKET_MULTIBLOCK}, or
     * {@link #BUCKET_NON_MACHINE}. Multiblock controllers (e.g. the Electric Blast Furnace) leave
     * {@code MachineDefinition.tier} at its default of 0, so reporting them as "ULV" is misleading — they are
     * bucketed on their own instead.
     */
    private static int bucketOf(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (!(block instanceof MetaMachineBlock machine)) {
            return BUCKET_NON_MACHINE;
        }
        MachineDefinition def = machine.definition;
        if (def instanceof MultiblockMachineDefinition && def.getTier() <= 0) {
            return BUCKET_MULTIBLOCK;
        }
        return def.getTier();
    }

    private static String bucketName(int bucket) {
        return switch (bucket) {
            case BUCKET_NON_MACHINE -> "non-machine";
            case BUCKET_MULTIBLOCK -> "multiblock";
            default -> bucket >= 0 && bucket < GTValues.VN.length ? GTValues.VN[bucket] : ("T" + bucket);
        };
    }

    /** Renders the tally as e.g. {@code "HV=42, EV=38, multiblock=2, non-machine=1"}: tiers ascending, then specials. */
    private static String summarise(Int2IntMap tally) {
        int[] keys = tally.keySet().toIntArray();
        Arrays.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (int key : keys) {
            if (key >= 0) {
                appendBucket(sb, key, tally.get(key));
            }
        }
        if (tally.containsKey(BUCKET_MULTIBLOCK)) {
            appendBucket(sb, BUCKET_MULTIBLOCK, tally.get(BUCKET_MULTIBLOCK));
        }
        if (tally.containsKey(BUCKET_NON_MACHINE)) {
            appendBucket(sb, BUCKET_NON_MACHINE, tally.get(BUCKET_NON_MACHINE));
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private static void appendBucket(StringBuilder sb, int bucket, int count) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(bucketName(bucket)).append('=').append(count);
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
