package com.norwood.wfcore.common.worldgen.road;

import com.norwood.wfcore.WFCore;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Probed terrain heights per link, keyed by seed + the columns sampled. Only probes are cached, not solved
 * profiles: retuning a road type must not invalidate them.
 */
final class RoadProbeCache {

    private static final int MAGIC = 0x57465250;
    private static final int VERSION = 2;

    private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wfcore-road-cache");
        thread.setDaemon(true);
        return thread;
    });

    private final Path file;
    private final Long2ObjectOpenHashMap<short[][]> entries;
    private boolean dirty;

    private RoadProbeCache(Path file, Long2ObjectOpenHashMap<short[][]> entries) {
        this.file = file;
        this.entries = entries;
    }

    static long key(long seed, RoadIndex.LinkGeometry link) {
        long hash = mix(seed ^ 0x9E3779B97F4A7C15L);
        int spacing = link.type().grade().sampleSpacing();
        hash = mix(hash ^ link.stationCount());
        hash = mix(hash ^ spacing);
        for (int station = 0; station < link.stationCount(); station += spacing) {
            hash = mix(hash ^ ((long) Math.floor(link.x(station)) * 31 + (long) Math.floor(link.z(station))));
        }
        return hash;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        value *= 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }

    /** Entries absent from {@code validKeys} are dropped, so the file does not accumulate retired routes. */
    static RoadProbeCache open(Path file, LongSet validKeys) {
        Long2ObjectOpenHashMap<short[][]> entries = new Long2ObjectOpenHashMap<>();
        boolean pruned = false;
        if (Files.isRegularFile(file)) {
            try (DataInputStream in = new DataInputStream(
                    new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
                if (in.readInt() == MAGIC && in.readInt() == VERSION) {
                    int count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        long key = in.readLong();
                        int n = in.readInt();
                        short[][] heights = {readShorts(in, n), readShorts(in, n)};
                        if (validKeys.contains(key)) {
                            entries.put(key, heights);
                        } else {
                            pruned = true;
                        }
                    }
                }
            } catch (IOException e) {
                WFCore.LOGGER.warn("Could not read the road terrain cache at {}; re-probing", file, e);
                entries.clear();
            }
        }
        RoadProbeCache cache = new RoadProbeCache(file, entries);
        cache.dirty = pruned;
        return cache;
    }

    synchronized short[][] get(long key) {
        return entries.get(key);
    }

    void put(long key, short[] ground, short[] water) {
        synchronized (this) {
            entries.put(key, new short[][] {ground, water});
            dirty = true;
        }
        SAVER.execute(this::flush);
    }

    synchronized void flush() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(entries.size());
                for (Long2ObjectMap.Entry<short[][]> entry : entries.long2ObjectEntrySet()) {
                    short[][] heights = entry.getValue();
                    out.writeLong(entry.getLongKey());
                    out.writeInt(heights[0].length);
                    for (short value : heights[0]) {
                        out.writeShort(value);
                    }
                    for (short value : heights[1]) {
                        out.writeShort(value);
                    }
                }
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            WFCore.LOGGER.warn("Could not write the road terrain cache to {}", file, e);
        }
    }

    private static short[] readShorts(DataInputStream in, int count) throws IOException {
        short[] values = new short[count];
        for (int i = 0; i < count; i++) {
            values[i] = in.readShort();
        }
        return values;
    }
}
