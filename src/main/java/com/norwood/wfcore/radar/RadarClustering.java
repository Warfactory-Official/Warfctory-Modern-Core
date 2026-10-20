package com.norwood.wfcore.radar;

import net.minecraft.core.BlockPos;
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

    /** Radars scanning within this many ticks of each other share a single DBSCAN result (~2s). */
    public static final int CACHE_TICKS = 40;

    // Per-level cache of the latest (possibly still in-flight) scan. Main-thread access only; weak keys so a
    // level that unloads (e.g. server stop) drops its entry instead of pinning a stale result.
    private static final Map<ServerLevel, Map<RadarProfile, Cached>> CACHE = new WeakHashMap<>();

    private record Cached(long gameTime, CompletableFuture<List<ClusterData>> future) {}

    private RadarClustering() {}

    public enum TargetType {
        PLAYER,
        STRUCTURE
    }

    /** Players carry no richness value. */
    public record DataPoint(TargetType type, int value) {}

    /** Online players in {@code level} as {@link TargetType#PLAYER} datapoints. Server-thread only. */
    public static Map<IntCoord2, DataPoint> collectPlayers(ServerLevel level) {
        Map<IntCoord2, DataPoint> map = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            map.put(new IntCoord2(player.blockPosition()), new DataPoint(TargetType.PLAYER, 0));
        }
        return map;
    }

    /** Players plus the whitelisted machines {@code profile} sees from {@code origin}. Server-thread only. */
    public static Map<IntCoord2, DataPoint> collectTargets(ServerLevel level, RadarProfile profile, BlockPos origin) {
        Map<IntCoord2, DataPoint> map = new HashMap<>();
        int seaLevel = level.getSeaLevel();
        long rangeSq = (long) profile.range() * profile.range();
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            if (inRange(profile, rangeSq, origin, pos.getX(), pos.getZ())
                    && profile.weight(pos.getY(), seaLevel) > 0) {
                map.put(new IntCoord2(pos), new DataPoint(TargetType.PLAYER, 0));
            }
        }
        for (Long2IntMap.Entry entry : RadarRegistryData.get(level).getMachineMap().long2IntEntrySet()) {
            long packed = entry.getLongKey();
            int x = BlockPos.getX(packed);
            int z = BlockPos.getZ(packed);
            if (!inRange(profile, rangeSq, origin, x, z)) {
                continue;
            }
            double weight = profile.weight(BlockPos.getY(packed), seaLevel);
            if (weight > 0) {
                int value = Math.max(1, entry.getIntValue());
                map.put(new IntCoord2(x, z),
                        new DataPoint(TargetType.STRUCTURE, Math.max(1, (int) Math.round(value * weight))));
            }
        }
        return map;
    }

    private static boolean inRange(RadarProfile profile, long rangeSq, BlockPos origin, int x, int z) {
        if (profile.range() <= 0) {
            return true;
        }
        long dx = x - origin.getX();
        long dz = z - origin.getZ();
        return dx * dx + dz * dz <= rangeSq;
    }

    /**
     * DBSCAN over {@link #collectTargets}. Whole-level profiles ({@code range == 0}) share one result per level
     * within {@link #CACHE_TICKS}; ranged profiles always compute. Failed scan evicted. Server thread only.
     */
    public static CompletableFuture<List<ClusterData>> scan(ServerLevel level, RadarProfile profile, BlockPos origin) {
        if (profile.range() > 0) {
            return calculateDBSCAN(collectTargets(level, profile, origin), profile.eps(), profile.minPts());
        }
        long now = level.getGameTime();
        Map<RadarProfile, Cached> perProfile = CACHE.computeIfAbsent(level, l -> new HashMap<>());
        Cached cached = perProfile.get(profile);
        if (cached != null) {
            long age = now - cached.gameTime();
            if (age >= 0 && age <= CACHE_TICKS) {
                return cached.future();
            }
        }
        CompletableFuture<List<ClusterData>> future =
                calculateDBSCAN(collectTargets(level, profile, origin), profile.eps(), profile.minPts());
        Cached entry = new Cached(now, future);
        perProfile.put(profile, entry);
        // Completes off-thread; evict on the server thread, only if still this entry.
        future.whenComplete((result, error) -> {
            if (error != null) {
                level.getServer().execute(() -> perProfile.remove(profile, entry));
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
