package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import com.norwood.wfcore.WFCore;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every road in a level: centrelines resampled and bucketed by chunk up front; terrain probe and elevation profile
 * per link resolved on first use.
 *
 * <p>Link i's final profile depends on:
 * <ul>
 * <li>its own probe;
 * <li>the unpinned profiles of every link meeting at its junctions (junction height = mean of their end decks);
 * <li>the final profile of the nearest lower-indexed road within {@link #SNAP_RADIUS} of its start, for tracks
 * with no {@code from}.
 * </ul>
 * Dependencies point at unpinned solves (leaves) or strictly lower indices => acyclic, and the result equals the
 * eager all-links solve.
 */
public final class RoadIndex {

    private static final double SNAP_RADIUS = 8.0;

    private static final ForkJoinPool PROBE_POOL = new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
            pool -> {
                ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                thread.setName("wfcore-road-probe-" + thread.getPoolIndex());
                return thread;
            }, null, false);

    public static final RoadIndex EMPTY = new RoadIndex(List.of(), new Long2ObjectOpenHashMap<>(), new Runs(), 0.0,
            null, null, new int[0], new int[0], new int[0], new long[0], new Long2ObjectOpenHashMap<>());

    private final List<LinkGeometry> links;
    private final Long2ObjectMap<int[]> byChunk;
    private final Runs runs;
    private final int chunkMargin;

    private final RoadProfile.Terrain terrain;
    private final RoadProbeCache cache;
    private final int[] from;
    private final int[] to;
    private final int[] network;
    private final long[] probeKeys;
    private final Long2ObjectMap<IntArrayList> junctionEnds;
    private final Once[] probed;
    private final Once[] unpinned;
    private final Once[] resolved;
    private final int[] unpinnedFirst;
    private final int[] unpinnedLast;

    private RoadIndex(List<LinkGeometry> links, Long2ObjectMap<int[]> byChunk, Runs runs, double maxHalfWidth,
            RoadProfile.Terrain terrain, RoadProbeCache cache, int[] from, int[] to, int[] network, long[] probeKeys,
            Long2ObjectMap<IntArrayList> junctionEnds) {
        this.links = links;
        this.byChunk = byChunk;
        this.runs = runs;
        this.chunkMargin = Math.max(1, (int) Math.ceil(maxHalfWidth / 16.0));
        this.terrain = terrain;
        this.cache = cache;
        this.from = from;
        this.to = to;
        this.network = network;
        this.probeKeys = probeKeys;
        this.junctionEnds = junctionEnds;
        int n = links.size();
        this.probed = Once.array(n);
        this.unpinned = Once.array(n);
        this.resolved = Once.array(n);
        this.unpinnedFirst = new int[n];
        this.unpinnedLast = new int[n];
    }

    public boolean isEmpty() {
        return links.isEmpty();
    }

    public int linkCount() {
        return links.size();
    }

    public long stationCount() {
        long total = 0;
        for (LinkGeometry link : links) {
            total += link.stationCount();
        }
        return total;
    }

    /** Stations on the ground / bridge / tunnel, indexed by {@link RoadProfile} modes. Resolves every link. */
    public long[] modeHistogram() {
        long[] counts = new long[3];
        for (int i = 0; i < links.size(); i++) {
            LinkGeometry link = link(i);
            for (int station = 0; station < link.stationCount(); station++) {
                counts[link.mode(station)]++;
            }
        }
        return counts;
    }

    /**
     * Every contiguous slice of a link's stations, in flat parallel arrays rather than objects. The rasteriser
     * touches these once per column of every chunk a road passes through, so the bounding box and the corridor
     * width are precomputed here — recomputing {@code gradedHalfWidth()} down a chain of record accessors 256
     * times per chunk is pure overhead, and the box is what lets most runs be rejected without looking at a
     * single station.
     */
    public static final class Runs {

        int[] link = new int[64];
        int[] first = new int[64];
        int[] last = new int[64];
        float[] minX = new float[64];
        float[] minZ = new float[64];
        float[] maxX = new float[64];
        float[] maxZ = new float[64];
        float[] reach = new float[64];
        int size;

        void add(int linkIndex, int firstStation, int lastStation, LinkGeometry geometry) {
            if (size == link.length) {
                int capacity = size * 2;
                link = java.util.Arrays.copyOf(link, capacity);
                first = java.util.Arrays.copyOf(first, capacity);
                last = java.util.Arrays.copyOf(last, capacity);
                minX = java.util.Arrays.copyOf(minX, capacity);
                minZ = java.util.Arrays.copyOf(minZ, capacity);
                maxX = java.util.Arrays.copyOf(maxX, capacity);
                maxZ = java.util.Arrays.copyOf(maxZ, capacity);
                reach = java.util.Arrays.copyOf(reach, capacity);
            }
            double lowX = Double.MAX_VALUE;
            double lowZ = Double.MAX_VALUE;
            double highX = -Double.MAX_VALUE;
            double highZ = -Double.MAX_VALUE;
            for (int station = firstStation; station <= lastStation; station++) {
                double x = geometry.x(station);
                double z = geometry.z(station);
                lowX = Math.min(lowX, x);
                lowZ = Math.min(lowZ, z);
                highX = Math.max(highX, x);
                highZ = Math.max(highZ, z);
            }
            link[size] = linkIndex;
            first[size] = firstStation;
            last[size] = lastStation;
            minX[size] = (float) lowX;
            minZ[size] = (float) lowZ;
            maxX[size] = (float) highX;
            maxZ[size] = (float) highZ;
            reach[size] = (float) geometry.type().clearedHalfWidth();
            size++;
        }
    }

    public int runLink(int run) {
        return runs.link[run];
    }

    /** Resolves the link's profile on first use; blocks while it is probed. */
    public LinkGeometry link(int index) {
        resolve(index);
        return links.get(index);
    }

    public void flush() {
        if (cache != null) {
            cache.flush();
        }
    }

    public int runFirst(int run) {
        return runs.first[run];
    }

    public int runLast(int run) {
        return runs.last[run];
    }

    public float runReach(int run) {
        return runs.reach[run];
    }

    /** True when any part of the run's bounding box comes within its corridor width of the given block box. */
    public boolean runReaches(int run, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        float r = runs.reach[run];
        return runs.maxX[run] >= minBlockX - r && runs.minX[run] <= maxBlockX + r
                && runs.maxZ[run] >= minBlockZ - r && runs.minZ[run] <= maxBlockZ + r;
    }

    /** Appends every run that can reach into the given chunk, including the corridor margin around it. */
    public void collectRuns(int chunkX, int chunkZ, IntArrayList out) {
        out.clear();
        for (int dx = -chunkMargin; dx <= chunkMargin; dx++) {
            for (int dz = -chunkMargin; dz <= chunkMargin; dz++) {
                int[] found = byChunk.get(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
                if (found != null) {
                    out.addElements(out.size(), found);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------------------------------------------

    public static RoadIndex build(ResourceKey<Level> dimension, ChunkGenerator generator, RandomState randomState,
            LevelHeightAccessor heights, Registry<RoadNetwork> networks, Registry<RoadType> types, long seed,
            java.nio.file.Path cacheFile) {
        return build(dimension, new GeneratorTerrain(generator, randomState, heights), networks, types, seed,
                cacheFile);
    }

    static RoadIndex build(ResourceKey<Level> dimension, RoadProfile.Terrain terrain, Registry<RoadNetwork> networks,
            Registry<RoadType> types, long seed, java.nio.file.Path cacheFile) {
        if (networks.size() == 0) {
            return EMPTY;
        }
        List<RoadType> ranked = new ArrayList<>(types.stream().toList());
        if (ranked.isEmpty()) {
            WFCore.LOGGER.warn("Road networks are loaded but no wfcore:road_type is defined; no roads will generate");
            return EMPTY;
        }
        ranked.sort((a, b) -> Integer.compare(b.match().priority(), a.match().priority()));

        List<LinkGeometry> links = new ArrayList<>();
        List<RoadNetwork.Link> sources = new ArrayList<>();
        IntArrayList networkOf = new IntArrayList();
        int unmatched = 0;
        double maxHalfWidth = 0.0;
        int networkOrdinal = 0;

        for (Map.Entry<ResourceKey<RoadNetwork>, RoadNetwork> entry : networks.entrySet()) {
            RoadNetwork network = entry.getValue();
            if (!network.dimension().equals(dimension)) {
                continue;
            }
            for (RoadNetwork.Link link : network.links()) {
                double[][] stations = RoadCurve.stations(link);
                RoadType type = choose(ranked, link, stations[0].length);
                if (type == null) {
                    unmatched++;
                    continue;
                }
                links.add(new LinkGeometry(type, stations[0], stations[1]));
                sources.add(link);
                networkOf.add(networkOrdinal);
                maxHalfWidth = Math.max(maxHalfWidth, type.clearedHalfWidth());
            }
            networkOrdinal++;
        }
        if (unmatched > 0) {
            WFCore.LOGGER.warn("{} road link(s) matched no wfcore:road_type and were skipped", unmatched);
        }
        if (links.isEmpty()) {
            return EMPTY;
        }

        int duplicates = dropShadowed(links, sources, networkOf);
        if (duplicates > 0) {
            WFCore.LOGGER.info("Roads: dropped {} link(s) that duplicate an existing route", duplicates);
        }

        int n = links.size();
        int[] from = new int[n];
        int[] to = new int[n];
        long[] probeKeys = new long[n];
        Long2ObjectMap<IntArrayList> junctionEnds = new Long2ObjectOpenHashMap<>();
        Long2ObjectMap<IntArrayList> building = new Long2ObjectOpenHashMap<>();
        Runs runs = new Runs();
        LongOpenHashSet validKeys = new LongOpenHashSet();
        for (int i = 0; i < n; i++) {
            RoadNetwork.Link source = sources.get(i);
            from[i] = source.from().orElse(-1);
            to[i] = source.to().orElse(-1);
            if (from[i] >= 0) {
                junctionEnds.computeIfAbsent(junctionKey(networkOf.getInt(i), from[i]), k -> new IntArrayList())
                        .add(i * 2);
            }
            if (to[i] >= 0) {
                junctionEnds.computeIfAbsent(junctionKey(networkOf.getInt(i), to[i]), k -> new IntArrayList())
                        .add(i * 2 + 1);
            }
            probeKeys[i] = RoadProbeCache.key(seed, links.get(i));
            validKeys.add(probeKeys[i]);
            bucket(building, runs, i, links.get(i));
        }
        Long2ObjectMap<int[]> byChunk = new Long2ObjectOpenHashMap<>(building.size());
        for (Long2ObjectMap.Entry<IntArrayList> entry : building.long2ObjectEntrySet()) {
            byChunk.put(entry.getLongKey(), entry.getValue().toIntArray());
        }

        long cacheStarted = System.nanoTime();
        RoadProbeCache cache = RoadProbeCache.open(cacheFile, validKeys);
        WFCore.LOGGER.info("Roads: terrain cache opened in {} ms", (System.nanoTime() - cacheStarted) / 1_000_000L);
        return new RoadIndex(links, byChunk, runs, maxHalfWidth, terrain, cache, from, to, networkOf.toIntArray(),
                probeKeys, junctionEnds);
    }

    private static long junctionKey(int network, int junction) {
        return ((long) network << 32) | junction;
    }

    private void probe(int index) {
        probed[index].run(() -> {
            LinkGeometry link = links.get(index);
            short[][] cached = cache.get(probeKeys[index]);
            if (cached != null && cached[0].length == link.stationCount()) {
                link.setTerrain(cached[0], cached[1]);
                return;
            }
            long started = System.nanoTime();
            awaitOnProbePool(() -> RoadProfile.probe(link, terrain));
            cache.put(probeKeys[index], link.groundArray(), link.waterArray());
            WFCore.LOGGER.info("Roads: probed link {} ({} stations) in {} ms", index, link.stationCount(),
                    (System.nanoTime() - started) / 1_000_000L);
        });
    }

    private static void awaitOnProbePool(Runnable work) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        PROBE_POOL.execute(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing road terrain", e);
        }
        if (failure.get() != null) {
            throw new IllegalStateException("Road terrain probe failed", failure.get());
        }
    }

    private void solveUnpinned(int index) {
        unpinned[index].run(() -> {
            probe(index);
            RoadProfile.Profile profile = RoadProfile.solve(links.get(index), OptionalInt.empty(), OptionalInt.empty());
            unpinnedFirst[index] = profile.deck()[0];
            unpinnedLast[index] = profile.deck()[profile.deck().length - 1];
        });
    }

    private OptionalInt junctionHeight(int network, int junction) {
        IntArrayList ends = junctionEnds.get(junctionKey(network, junction));
        double sum = 0.0;
        for (int i = 0; i < ends.size(); i++) {
            int end = ends.getInt(i);
            int link = end >> 1;
            solveUnpinned(link);
            sum += (end & 1) == 0 ? unpinnedFirst[link] : unpinnedLast[link];
        }
        return OptionalInt.of((int) Math.round(sum / ends.size()));
    }

    private void resolve(int index) {
        resolved[index].run(() -> {
            LinkGeometry geometry = links.get(index);
            probe(index);
            OptionalInt pinStart = from[index] >= 0
                    ? junctionHeight(network[index], from[index])
                    : snapTo(index, geometry.x(0), geometry.z(0));
            OptionalInt pinEnd = to[index] >= 0 ? junctionHeight(network[index], to[index]) : OptionalInt.empty();
            RoadProfile.Profile profile = RoadProfile.solve(geometry, pinStart, pinEnd);
            geometry.setProfile(profile.deck(), profile.mode(), profile.portal());
        });
    }

    /** Deck of the nearest station within {@link #SNAP_RADIUS} on a link below {@code index}; ties keep the first. */
    private OptionalInt snapTo(int index, double x, double z) {
        int chunkX = (int) Math.floor(x) >> 4;
        int chunkZ = (int) Math.floor(z) >> 4;
        double best = SNAP_RADIUS * SNAP_RADIUS;
        int bestLink = -1;
        int bestStation = -1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int[] found = byChunk.get(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
                if (found == null) {
                    continue;
                }
                for (int run : found) {
                    if (runs.link[run] >= index) {
                        continue;
                    }
                    LinkGeometry link = links.get(runs.link[run]);
                    for (int s = runs.first[run]; s <= runs.last[run]; s++) {
                        double dxs = link.x(s) - x;
                        double dzs = link.z(s) - z;
                        double distance = dxs * dxs + dzs * dzs;
                        if (distance < best) {
                            best = distance;
                            bestLink = runs.link[run];
                            bestStation = s;
                        }
                    }
                }
            }
        }
        if (bestLink < 0) {
            return OptionalInt.empty();
        }
        resolve(bestLink);
        return OptionalInt.of(links.get(bestLink).deck(bestStation));
    }

    private static final class Once {

        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile boolean done;
        private volatile Throwable failure;

        static Once[] array(int n) {
            Once[] once = new Once[n];
            for (int i = 0; i < n; i++) {
                once[i] = new Once();
            }
            return once;
        }

        void run(Runnable work) {
            if (!done) {
                if (claimed.compareAndSet(false, true)) {
                    try {
                        work.run();
                    } catch (Throwable t) {
                        failure = t;
                    } finally {
                        done = true;
                        finished.countDown();
                    }
                } else {
                    try {
                        finished.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while resolving a road", e);
                    }
                }
            }
            if (failure != null) {
                throw new IllegalStateException("Road resolution failed", failure);
            }
        }
    }

    /** Cell size for the shadow test — two roads within about this far apart read as the same road. */
    private static final int SHADOW_CELL = 8;
    /** Fraction of a link that has to lie on top of another before it is considered a duplicate. */
    private static final double SHADOW_FRACTION = 0.75;

    /**
     * Removes links that run on top of one already kept, in place. Candidates are considered most-important
     * first — higher road class, then longer — so the survivor is the one worth keeping, but the lists come back
     * in their original file order because the profile solve depends on it for pinning tracks.
     */
    private static int dropShadowed(List<LinkGeometry> links, List<RoadNetwork.Link> sources,
            IntArrayList networks) {
        Integer[] order = new Integer[links.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int byClass = Integer.compare(links.get(b).type().match().priority(),
                    links.get(a).type().match().priority());
            return byClass != 0 ? byClass : Integer.compare(links.get(b).stationCount(),
                    links.get(a).stationCount());
        });

        it.unimi.dsi.fastutil.longs.LongOpenHashSet occupied = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        boolean[] drop = new boolean[links.size()];
        for (int index : order) {
            LinkGeometry link = links.get(index);
            int samples = 0;
            int shadowed = 0;
            for (int station = 0; station < link.stationCount(); station += SHADOW_CELL) {
                samples++;
                if (occupied.contains(cell(link.x(station), link.z(station)))) {
                    shadowed++;
                }
            }
            if (samples > 0 && (double) shadowed / samples >= SHADOW_FRACTION) {
                drop[index] = true;
                continue;
            }
            // Every cell it passes through, plus the ring around it, so a road one cell off still counts as on top.
            for (int station = 0; station < link.stationCount(); station++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        occupied.add(cell(link.x(station) + dx * SHADOW_CELL, link.z(station) + dz * SHADOW_CELL));
                    }
                }
            }
        }

        int removed = 0;
        for (int i = links.size() - 1; i >= 0; i--) {
            if (drop[i]) {
                links.remove(i);
                sources.remove(i);
                networks.removeInt(i);
                removed++;
            }
        }
        return removed;
    }

    private static long cell(double x, double z) {
        return ChunkPos.asLong(Math.floorDiv((int) Math.floor(x), SHADOW_CELL),
                Math.floorDiv((int) Math.floor(z), SHADOW_CELL));
    }

    /** Highest-priority type whose match the link satisfies, or null when a pack defines none that fits. */
    private static RoadType choose(List<RoadType> ranked, RoadNetwork.Link link, int length) {
        for (RoadType type : ranked) {
            if (type.match().matches(link, length)) {
                return type;
            }
        }
        return null;
    }

    /** Files each maximal run of consecutive stations under the chunk it passes through. */
    private static void bucket(Long2ObjectMap<IntArrayList> byChunk, Runs runs, int index, LinkGeometry geometry) {
        int n = geometry.stationCount();
        int runStart = 0;
        long currentChunk = chunkOf(geometry, 0);
        for (int i = 1; i <= n; i++) {
            long chunk = i < n ? chunkOf(geometry, i) : Long.MIN_VALUE;
            if (chunk != currentChunk) {
                byChunk.computeIfAbsent(currentChunk, key -> new IntArrayList(2)).add(runs.size);
                runs.add(index, runStart, i - 1, geometry);
                runStart = i;
                currentChunk = chunk;
            }
        }
    }

    private static long chunkOf(LinkGeometry geometry, int station) {
        return ChunkPos.asLong((int) Math.floor(geometry.x(station)) >> 4,
                (int) Math.floor(geometry.z(station)) >> 4);
    }

    // ---------------------------------------------------------------------------------------------------------

    /** One road's resampled centreline plus the profile solved along it. */
    public static final class LinkGeometry {

        private final RoadType type;
        private final double[] xs;
        private final double[] zs;
        private short[] ground;
        private short[] water;
        private short[] deck;
        private byte[] mode;
        private boolean[] portal;

        LinkGeometry(RoadType type, double[] xs, double[] zs) {
            this.type = type;
            this.xs = xs;
            this.zs = zs;
        }

        void setTerrain(short[] ground, short[] water) {
            this.ground = ground;
            this.water = water;
        }

        void setProfile(short[] deck, byte[] mode, boolean[] portal) {
            this.deck = deck;
            this.mode = mode;
            this.portal = portal;
        }

        public RoadType type() {
            return type;
        }

        public int stationCount() {
            return xs.length;
        }

        public double x(int station) {
            return xs[station];
        }

        public double z(int station) {
            return zs[station];
        }

        short[] groundArray() {
            return ground;
        }

        short[] waterArray() {
            return water;
        }

        public int ground(int station) {
            return ground[station];
        }

        public int water(int station) {
            return water[station];
        }

        public int deck(int station) {
            return deck[station];
        }

        public byte mode(int station) {
            return mode[station];
        }

        public boolean portal(int station) {
            return portal[station];
        }
    }

    /**
     * Terrain height straight from the noise router. This never loads or generates a chunk, which is what lets a
     * profile be solved for a whole road from inside one chunk's feature without cascading generation.
     */
    private record GeneratorTerrain(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heights,
            int seaLevel) implements RoadProfile.Terrain {

        GeneratorTerrain(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heights) {
            this(generator, randomState, heights, generator.getSeaLevel());
        }

        @Override
        public long sample(int x, int z) {
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState) - 1;
            // Above sea level there is nothing that could be holding water, so the first non-air block down from
            // the top is also the ground and the second scan can be skipped outright. Roads are almost entirely
            // on land, so this is the path nearly every probe takes — and each probe is a full evaluation of the
            // dimension's density-function graph, which on a terrain mod is the most expensive call here by far.
            int ground = surface > seaLevel ? surface
                    : generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState) - 1;
            return ((long) ground << 32) | (surface & 0xFFFFFFFFL);
        }
    }
}
