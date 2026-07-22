package com.norwood.wfcore.radar;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.data.RadarRegistryData;

import java.io.File;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

/**
 * Retroactively populates the radar registry by scanning a dimension's region files off-thread.
 *
 * <p>
 * Ported from the 1.12.2 version: targets are identified purely by their block registry name from
 * the chunk's block-state palette (no tile-entity reads needed in modern, since the palette already
 * carries the block id). Region files are parsed on virtual threads; results queue up and are drained
 * a few per server tick to avoid a spike.
 */
public class Retrofitter {

    public static final Retrofitter INSTANCE = new Retrofitter();
    private static final int ENTRIES_PER_TICK = 16;

    /** Retrofit only feeds whitelisted blocks (see {@link RadarConfig}) into the persistent registry. */
    private static final ToIntFunction<ResourceLocation> WHITELIST_CLASSIFIER =
            id -> RadarConfig.isWhitelisted(id) ? RadarConfig.getValue(id) : -1;

    private volatile boolean active = false;
    private volatile boolean producing = false;
    private volatile ServerLevel targetLevel;

    public final Queue<Combined> queue = new ConcurrentLinkedQueue<>();

    public record Combined(long packed, int value) {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!active || event.phase != TickEvent.Phase.END || targetLevel == null) {
            return;
        }
        RadarRegistryData data = RadarRegistryData.get(targetLevel);
        for (int i = 0; i < ENTRIES_PER_TICK; i++) {
            Combined entry = queue.poll();
            if (entry == null) {
                break; // queue momentarily empty; this does NOT mean the scan is done
            }
            data.addMachine((int) (entry.packed >> 32), (int) entry.packed, entry.value);
        }
        // Finish only once every region file has been parsed AND the backlog is fully drained. A transiently
        // empty queue mid-scan (producers slower than the 16/tick drain) must not end the scan early.
        if (!producing && queue.isEmpty()) {
            active = false;
            targetLevel = null;
            WFCore.LOGGER.info("Retrofitter scan finished; queue drained.");
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_retrofit")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    if (startGlobalScan(level)) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Started radar retrofit scan for " + level.dimension().location()), true);
                        return 1;
                    }
                    ctx.getSource().sendFailure(Component.literal("A radar retrofit scan is already running."));
                    return 0;
                }));
    }

    /** @return {@code false} if a scan is already running (request ignored), {@code true} if one was started. */
    public boolean startGlobalScan(ServerLevel level) {
        if (active) {
            WFCore.LOGGER.warn("Retrofitter: a scan is already running; ignoring new request.");
            return false;
        }
        Path regionDir = RadarChunkParser.regionDirectory(level);
        File[] files = regionDir.toFile().listFiles((dir, name) -> name.endsWith(".mca"));
        if (files == null || files.length == 0) {
            WFCore.LOGGER.warn("Retrofitter: no region files found in {}", regionDir);
            return false;
        }

        this.targetLevel = level;
        this.producing = true;
        this.active = true;
        WFCore.LOGGER.info("Retrofitter: scanning {} region files...", files.length);

        // Run the (blocking) fan-out on its own daemon thread so the command returns immediately;
        // the server tick drains the queue as it fills. `producing` stays true until every file is parsed,
        // so the tick loop never mistakes a transiently empty queue for completion.
        Thread worker = new Thread(() -> {
            int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            try {
                for (File mcaFile : files) {
                    executor.submit(() -> RadarChunkParser.parseRegionFile(mcaFile, regionDir,
                            WHITELIST_CLASSIFIER, (packed, value) -> queue.add(new Combined(packed, value))));
                }
                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                producing = false;
            }
            WFCore.LOGGER.info("Retrofitter: parsing finished, {} positions queued.", queue.size());
        }, "wfcore-retrofit");
        worker.setDaemon(true);
        worker.start();
        return true;
    }
}
