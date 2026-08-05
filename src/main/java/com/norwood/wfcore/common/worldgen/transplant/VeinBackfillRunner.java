package com.norwood.wfcore.common.worldgen.transplant;

import com.gregtechceu.gtceu.common.data.GTOres;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.mojang.logging.LogUtils;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class VeinBackfillRunner {

    public static final VeinBackfillRunner INSTANCE = new VeinBackfillRunner();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROGRESS_FILE = "wfcore_vein_backfill.progress";
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("wfcore_vein_backfill", Comparator.comparingLong(ChunkPos::toLong));
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private enum Phase { IDLE, ENUMERATING, RUNNING, DONE, ERROR }

    private volatile Phase phase = Phase.IDLE;
    private volatile String message = "idle";

    private ServerLevel level;
    private int perTick = 4;
    private int gridSize = 3;
    private int forceLoadRadius = 4;
    private int veinReach = 2;

    private volatile LongOpenHashSet generated;
    private volatile long[] origins;

    private int cursor;
    private long veinsPlaced;
    private long chunksTouched;
    private long originsWithVeins;
    private long lastLoggedAt;

    private boolean skipComputed;
    private LongOpenHashSet skipOrigins;
    private long skippedLoaded;

    private VeinBackfillRunner() {}


    public synchronized boolean start(ServerLevel level, int perTick) {
        if (phase == Phase.ENUMERATING || phase == Phase.RUNNING) {
            return false;
        }
        this.level = level;
        this.perTick = Math.max(1, perTick);
        this.gridSize = Math.max(1, ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinGridSize);
        this.veinReach = maxVeinSearchDistance();
        this.forceLoadRadius = this.veinReach + 2;
        this.cursor = 0;
        this.veinsPlaced = 0;
        this.chunksTouched = 0;
        this.originsWithVeins = 0;
        this.lastLoggedAt = 0;
        this.skipComputed = false;
        this.skipOrigins = null;
        this.skippedLoaded = 0;
        this.phase = Phase.ENUMERATING;
        this.message = "enumerating region files...";

        Thread t = new Thread(this::enumerate, "wfcore-vein-backfill-enumerate");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public synchronized void stop() {
        if (phase == Phase.RUNNING || phase == Phase.ENUMERATING) {
            persistProgress();
            phase = Phase.IDLE;
            message = "stopped at " + cursor + "/" + (origins == null ? 0 : origins.length);
            LOGGER.info("[WFCore] Vein backfill stopped: {}", message);
        }
    }

    public String status() {
        int total = origins == null ? 0 : origins.length;
        return switch (phase) {
            case IDLE -> "idle";
            case ENUMERATING -> "enumerating: " + message;
            case RUNNING -> String.format(
                    "running %d/%d origins (%.1f%%), %d veins into %d chunks, %d skipped (loaded)",
                    cursor, total, total == 0 ? 0.0 : 100.0 * cursor / total,
                    veinsPlaced, chunksTouched, skippedLoaded);
            case DONE -> String.format(
                    "done: %d origins scanned, %d veins placed into %d chunks, %d skipped (loaded)",
                    total, veinsPlaced, chunksTouched, skippedLoaded);
            case ERROR -> "error: " + message;
        };
    }

    public boolean isBusy() {
        return phase == Phase.ENUMERATING || phase == Phase.RUNNING;
    }


    private void enumerate() {
        try {
            LongOpenHashSet gen = new LongOpenHashSet(1 << 20);
            LongArrayList orig = new LongArrayList();
            Path regionDir = regionDir(level);
            if (!Files.isDirectory(regionDir)) {
                fail("no region directory: " + regionDir);
                return;
            }

            long lastDone = readResumeCursor();
            byte[] header = new byte[4096];
            int regionFiles = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
                for (Path file : stream) {
                    Matcher m = REGION_NAME.matcher(file.getFileName().toString());
                    if (!m.matches()) {
                        continue;
                    }
                    int rx = Integer.parseInt(m.group(1));
                    int rz = Integer.parseInt(m.group(2));
                    if (Files.size(file) < 4096) {
                        continue;
                    }
                    try (InputStream in = Files.newInputStream(file)) {
                        int read = in.readNBytes(header, 0, 4096);
                        if (read < 4096) {
                            continue;
                        }
                    }
                    for (int i = 0; i < 1024; i++) {
                        int off = ((header[i * 4] & 0xFF) << 16)
                                | ((header[i * 4 + 1] & 0xFF) << 8)
                                | (header[i * 4 + 2] & 0xFF);
                        if (off == 0) {
                            continue;
                        }
                        int cx = rx * 32 + (i & 31);
                        int cz = rz * 32 + (i >> 5);
                        gen.add(ChunkPos.asLong(cx, cz));
                        if (Math.floorMod(cx, gridSize) == 0 && Math.floorMod(cz, gridSize) == 0) {
                            orig.add(ChunkPos.asLong(cx, cz));
                        }
                    }
                    regionFiles++;
                    this.message = "scanned " + regionFiles + " regions, " + gen.size() + " chunks";
                }
            }

            long[] sorted = orig.toLongArray();
            java.util.Arrays.sort(sorted);

            // Resume: skip origins already processed (value <= lastDone in sorted order).
            int resumeAt = 0;
            if (lastDone != Long.MIN_VALUE) {
                int idx = java.util.Arrays.binarySearch(sorted, lastDone);
                resumeAt = idx >= 0 ? idx + 1 : -(idx + 1);
            }

            // Publish, then flip phase (safe publication).
            this.generated = gen;
            this.origins = sorted;
            this.cursor = resumeAt;
            this.message = "ready: " + sorted.length + " grid origins over " + gen.size() + " chunks"
                    + (resumeAt > 0 ? " (resuming at " + resumeAt + ")" : "");
            LOGGER.info("[WFCore] Vein backfill enumeration complete: {}", this.message);
            this.phase = Phase.RUNNING;
        } catch (Throwable t) {
            LOGGER.error("[WFCore] Vein backfill enumeration failed", t);
            fail(t.toString());
        }
    }

    // ------------------------------------------------------------------ processing (server thread)

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase != Phase.RUNNING) {
            return;
        }
        ServerChunkCache scc = level.getChunkSource();
        long[] work = this.origins;

        // One-shot: snapshot origins near currently-loaded (base/loader/spawn) chunks so we never edit them.
        if (!skipComputed) {
            computeBaseAvoidanceSkipSet(scc);
            skipComputed = true;
        }

        int placements = perTick;
        int iterCap = perTick * 64; // bound skip-scanning per tick so a big base area can't stall a tick
        while (placements > 0 && iterCap-- > 0 && cursor < work.length) {
            long key = work[cursor];
            if (skipOrigins.contains(key)) {
                skippedLoaded++;
                cursor++;
                continue;
            }
            ChunkPos origin = new ChunkPos(key);
            try {
                scc.addRegionTicket(TICKET, origin, forceLoadRadius, origin);
                VeinBackfill.Result r =
                        VeinBackfill.placeAt(level, origin, cp -> generated.contains(cp.toLong()));
                if (r.veinsPlaced() > 0) {
                    originsWithVeins++;
                    veinsPlaced += r.veinsPlaced();
                    chunksTouched += r.chunksTouched();
                }
            } catch (Throwable t) {
                LOGGER.error("[WFCore] Vein backfill failed at origin {}", origin, t);
            } finally {
                scc.removeRegionTicket(TICKET, origin, forceLoadRadius, origin);
            }
            cursor++;
            placements--;
        }

        // Periodic flush + progress so a crash resumes cleanly and chunks can unload.
        if (cursor - lastLoggedAt >= 200 || cursor >= work.length) {
            scc.save(false);
            persistProgress();
            LOGGER.info("[WFCore] Vein backfill: {}", status());
            lastLoggedAt = cursor;
        }

        if (cursor >= work.length) {
            scc.save(false);
            persistProgress();
            phase = Phase.DONE;
            message = status();
            LOGGER.info("[WFCore] Vein backfill DONE: {}", message);
        }
    }


    private void fail(String reason) {
        this.message = reason;
        this.phase = Phase.ERROR;
    }


    private void computeBaseAvoidanceSkipSet(ServerChunkCache scc) {
        long[] work = this.origins;
        LongOpenHashSet skip = new LongOpenHashSet();
        for (long key : work) {
            int ox = ChunkPos.getX(key);
            int oz = ChunkPos.getZ(key);
            if (neighbourhoodLoaded(scc, ox, oz)) {
                skip.add(key);
            }
        }
        this.skipOrigins = skip;
        LOGGER.info("[WFCore] Base-avoidance: {} of {} origins are near currently-loaded chunks and "
                + "will be skipped.", skip.size(), work.length);
    }

    private boolean neighbourhoodLoaded(ServerChunkCache scc, int ox, int oz) {
        for (int dx = -veinReach; dx <= veinReach; dx++) {
            for (int dz = -veinReach; dz <= veinReach; dz++) {
                if (scc.hasChunk(ox + dx, oz + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int maxVeinSearchDistance() {
        double halfVeinSize = GTOres.getLargestVeinSize() / 2.0;
        int randomOffset = ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinRandomOffset;
        return (int) Math.ceil((halfVeinSize + randomOffset) / 16.0);
    }

    private static Path regionDir(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path dimFolder = DimensionType.getStorageFolder(level.dimension(), worldRoot);
        return dimFolder.resolve("region");
    }

    private Path progressPath() {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(PROGRESS_FILE);
    }

    private void persistProgress() {
        if (origins == null) {
            return;
        }
        Properties p = new Properties();
        p.setProperty("dimension", level.dimension().location().toString());
        p.setProperty("cursor", Integer.toString(cursor));
        p.setProperty("lastDoneOrigin", Long.toString(cursor > 0 ? origins[cursor - 1] : Long.MIN_VALUE));
        p.setProperty("veinsPlaced", Long.toString(veinsPlaced));
        p.setProperty("chunksTouched", Long.toString(chunksTouched));
        p.setProperty("originsWithVeins", Long.toString(originsWithVeins));
        try (OutputStream out = Files.newOutputStream(progressPath())) {
            p.store(out, "WFCore nether->overworld vein backfill progress");
        } catch (IOException e) {
            LOGGER.warn("[WFCore] Could not write backfill progress", e);
        }
    }

    private long readResumeCursor() {
        Path path = progressPath();
        if (!Files.exists(path)) {
            return Long.MIN_VALUE;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
        if (!level.dimension().location().toString().equals(p.getProperty("dimension"))) {
            return Long.MIN_VALUE;
        }
        this.veinsPlaced = Long.parseLong(p.getProperty("veinsPlaced", "0"));
        this.chunksTouched = Long.parseLong(p.getProperty("chunksTouched", "0"));
        this.originsWithVeins = Long.parseLong(p.getProperty("originsWithVeins", "0"));
        return Long.parseLong(p.getProperty("lastDoneOrigin", Long.toString(Long.MIN_VALUE)));
    }
}
