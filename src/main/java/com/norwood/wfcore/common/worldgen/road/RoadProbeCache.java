package com.norwood.wfcore.common.worldgen.road;

import com.norwood.wfcore.WFCore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Stores the terrain heights probed along every road, so a world only pays for them once.
 *
 * <p>Probing is the whole cost of building the road index: each sample is a full evaluation of the dimension's
 * density-function graph, and on a terrain mod that is tens of milliseconds a column. Everything downstream —
 * solving profiles, classifying bridges and tunnels, rasterising — is arithmetic by comparison.
 *
 * <p>Only the probe results are cached, deliberately, not the finished index. Retuning a road type's width,
 * gradient budget or block palette is the thing anyone does repeatedly, and none of it changes what the terrain
 * is; keeping the cache one layer below that means those edits take effect on the next load for free. The key
 * covers the world seed and every probed coordinate, so any change that <em>would</em> move a sample — a
 * re-routed network, a different {@code sample_spacing} — misses the cache instead of reading a stale answer.
 */
final class RoadProbeCache {

    private static final int MAGIC = 0x57465250;
    private static final int VERSION = 1;

    private RoadProbeCache() {}

    /** Identifies exactly the inputs the probe results depend on: the world seed and the columns sampled. */
    static long key(long seed, List<RoadIndex.LinkGeometry> links) {
        long hash = mix(seed ^ 0x9E3779B97F4A7C15L);
        for (RoadIndex.LinkGeometry link : links) {
            hash = mix(hash ^ link.stationCount());
            hash = mix(hash ^ link.type().grade().sampleSpacing());
            for (int station = 0; station < link.stationCount(); station += link.type().grade().sampleSpacing()) {
                hash = mix(hash ^ ((long) Math.floor(link.x(station)) * 31 + (long) Math.floor(link.z(station))));
            }
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

    /** Fills every link's terrain arrays from disk. False on any miss, mismatch or damage — never on a guess. */
    static boolean load(Path file, long key, List<RoadIndex.LinkGeometry> links) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readLong() != key
                    || in.readInt() != links.size()) {
                return false;
            }
            short[][] ground = new short[links.size()][];
            short[][] water = new short[links.size()][];
            for (int i = 0; i < links.size(); i++) {
                int n = in.readInt();
                if (n != links.get(i).stationCount()) {
                    return false;
                }
                ground[i] = readShorts(in, n);
                water[i] = readShorts(in, n);
            }
            // Only published once the whole file has read back cleanly, so a truncated cache cannot leave half
            // the network holding terrain from a previous world.
            for (int i = 0; i < links.size(); i++) {
                links.get(i).setTerrain(ground[i], water[i]);
            }
            return true;
        } catch (IOException e) {
            WFCore.LOGGER.warn("Could not read the road terrain cache at {}; re-probing", file, e);
            return false;
        }
    }

    static void save(Path file, long key, List<RoadIndex.LinkGeometry> links) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeLong(key);
                out.writeInt(links.size());
                for (RoadIndex.LinkGeometry link : links) {
                    int n = link.stationCount();
                    out.writeInt(n);
                    for (int i = 0; i < n; i++) {
                        out.writeShort(link.ground(i));
                    }
                    for (int i = 0; i < n; i++) {
                        out.writeShort(link.water(i));
                    }
                }
            }
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // A cache that cannot be written costs a slow load next time and nothing else.
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
