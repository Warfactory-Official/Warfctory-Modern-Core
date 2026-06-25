package com.norwood.wfcore.radar;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.norwood.wfcore.radar.data.RadarRegistryData;
import com.norwood.wfcore.radar.math.BoundingBox;
import com.norwood.wfcore.radar.math.ClusterData;
import com.norwood.wfcore.radar.math.IntCoord2;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Snapshots the radar-visible targets in a level (online players + registered machines) and runs
 * DBSCAN over them to find bases. Densely populated, structure-rich areas form clusters; isolated
 * points are discarded as noise, which filters out ghost bases.
 */
public final class RadarClustering {

    // TODO: make these adjustable in the GUI, as in the 1.12.2 version.
    public static final int MIN_PTS = 10;
    public static final int EPS = 200;

    /** Radars scanning within this many ticks of each other share a single DBSCAN result (~2s). */
    public static final int CACHE_TICKS = 40;

    // Per-level cache of the latest (possibly still in-flight) scan. Main-thread access only; weak keys so a
    // level that unloads (e.g. server stop) drops its entry instead of pinning a stale result.
    private static final Map<ServerLevel, Cached> CACHE = new WeakHashMap<>();

    private record Cached(long gameTime, CompletableFuture<List<ClusterData>> future) {}

    private RadarClustering() {}

    public enum TargetType {
        PLAYER,
        STRUCTURE
    }

    /** Players carry no richness value. */
    public record DataPoint(TargetType type, int value) {}

    public static Map<IntCoord2, DataPoint> collectTargets(ServerLevel level) {
        Map<IntCoord2, DataPoint> map = new HashMap<>();

        for (ServerPlayer player : level.players()) {
            map.put(new IntCoord2(player.blockPosition()), new DataPoint(TargetType.PLAYER, 0));
        }

        Long2IntMap machineMap = RadarRegistryData.get(level).getMachineMap();
        for (Long2IntMap.Entry entry : machineMap.long2IntEntrySet()) {
            long packed = entry.getLongKey();
            map.put(new IntCoord2((int) (packed >> 32), (int) packed),
                    new DataPoint(TargetType.STRUCTURE, entry.getIntValue()));
        }

        return map;
    }

    /**
     * Runs a DBSCAN scan for {@code level}, or reuses a recent one: requests within {@link #CACHE_TICKS} ticks
     * share the same (possibly still-running) result, so a burst of radars firing together computes once. A
     * failed scan is evicted rather than reused. Must be called on the server thread — it snapshots
     * {@link #collectTargets(ServerLevel)} synchronously.
     */
    public static CompletableFuture<List<ClusterData>> scan(ServerLevel level, int eps, int minPts) {
        long now = level.getGameTime();
        Cached cached = CACHE.get(level);
        if (cached != null) {
            long age = now - cached.gameTime();
            if (age >= 0 && age <= CACHE_TICKS) {
                return cached.future();
            }
        }
        CompletableFuture<List<ClusterData>> future = calculateDBSCAN(collectTargets(level), eps, minPts);
        Cached entry = new Cached(now, future);
        CACHE.put(level, entry);
        // The future completes off-thread; on failure, drop this entry back on the server thread so co-firing
        // radars recompute instead of sharing the failure. remove(key, value) only evicts if still this entry,
        // so a newer scan that already replaced it is left alone.
        future.whenComplete((result, error) -> {
            if (error != null) {
                level.getServer().execute(() -> CACHE.remove(level, entry));
            }
        });
        return future;
    }

    public static CompletableFuture<List<ClusterData>> calculateDBSCAN(Map<IntCoord2, DataPoint> objMap,
                                                                       int eps, int minPts) {
        return CompletableFuture.supplyAsync(() -> {
            DBSCANClusterer<IntCoord2> dbscan = new DBSCANClusterer<>(eps, minPts);
            List<Cluster<IntCoord2>> clusters = dbscan.cluster(new ArrayList<>(objMap.keySet()));

            List<ClusterData> out = new ArrayList<>(clusters.size());
            for (Cluster<IntCoord2> cluster : clusters) {
                List<IntCoord2> points = cluster.getPoints();

                int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                long sumX = 0, sumZ = 0;

                for (IntCoord2 point : points) {
                    int x = point.getX();
                    int z = point.getZ();
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (z < minZ) minZ = z;
                    if (z > maxZ) maxZ = z;
                    sumX += x;
                    sumZ += z;
                }

                IntCoord2 center = new IntCoord2((int) (sumX / points.size()), (int) (sumZ / points.size()));

                int population = 0;
                int value = 0;
                for (IntCoord2 point : points) {
                    DataPoint dataPoint = objMap.get(point);
                    switch (dataPoint.type()) {
                        case PLAYER -> population++;
                        case STRUCTURE -> value += dataPoint.value() > 0 ? dataPoint.value() : 1;
                    }
                }

                out.add(new ClusterData(points, center,
                        new BoundingBox(new IntCoord2(minX, minZ), new IntCoord2(maxX, maxZ)),
                        value, population));
            }
            return out;
        });
    }
}
