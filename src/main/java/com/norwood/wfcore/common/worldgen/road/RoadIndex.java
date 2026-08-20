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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Every road in a level, resolved once: centrelines resampled, elevation profiles solved against this world's own
 * terrain, and stations bucketed by chunk so the feature can ask "what runs through here" in constant time.
 *
 * <p>Resolving the whole network up front rather than per chunk is what makes the result seamless. A profile is a
 * property of a road, not of a chunk — smoothing it inside a 16-block window would put a kink on every chunk
 * border — so it is solved end to end and each chunk then reads its slice out of the answer.
 *
 * <p>Links are solved in file order, which is also the order the router grew them: junction-to-junction roads
 * first, then the dendritic tracks that hang off them. That ordering is load-bearing. A track's near end attaches
 * to an arbitrary point on whatever already existed, so it can only be pinned to that road's height once that
 * road has a height.
 */
public final class RoadIndex {

    public static final RoadIndex EMPTY = new RoadIndex(List.of(), new Long2ObjectOpenHashMap<>(), new Runs(), 0.0);

    /** How close a track's near end has to land to an existing road before it is pinned to that road's deck. */
    private static final double SNAP_RADIUS = 8.0;

    private final List<LinkGeometry> links;
    private final Long2ObjectMap<int[]> byChunk;
    private final Runs runs;
    private final int chunkMargin;

    private RoadIndex(List<LinkGeometry> links, Long2ObjectMap<int[]> byChunk, Runs runs, double maxHalfWidth) {
        this.links = links;
        this.byChunk = byChunk;
        this.runs = runs;
        this.chunkMargin = Math.max(1, (int) Math.ceil(maxHalfWidth / 16.0));
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

    /**
     * How many stations came out on the ground, on a bridge and in a tunnel, indexed by the mode constants in
     * {@link RoadProfile}. The single most useful number for tuning: a network that is mostly bridge means the
     * gradient budget is tighter than the terrain allows, and one with no tunnels at all means {@code min_cover}
     * is set higher than any hill on the route.
     */
    public long[] modeHistogram() {
        long[] counts = new long[3];
        for (LinkGeometry link : links) {
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

    public LinkGeometry link(int index) {
        return links.get(index);
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
        if (networks.size() == 0) {
            return EMPTY;
        }
        List<RoadType> ranked = new ArrayList<>(types.stream().toList());
        if (ranked.isEmpty()) {
            WFCore.LOGGER.warn("Road networks are loaded but no wfcore:road_type is defined; no roads will generate");
            return EMPTY;
        }
        ranked.sort((a, b) -> Integer.compare(b.match().priority(), a.match().priority()));

        RoadProfile.Terrain terrain = new GeneratorTerrain(generator, randomState, heights);

        // Phase 1: geometry only. Resampling a curve is arithmetic, so this is cheap and it is what tells the
        // probe phase which columns it needs.
        List<LinkGeometry> links = new ArrayList<>();
        List<RoadNetwork.Link> sources = new ArrayList<>();
        List<double[]> junctionHeightsPerLink = new ArrayList<>();
        int unmatched = 0;
        double maxHalfWidth = 0.0;

        for (Map.Entry<ResourceKey<RoadNetwork>, RoadNetwork> entry : networks.entrySet()) {
            RoadNetwork network = entry.getValue();
            if (!network.dimension().equals(dimension)) {
                continue;
            }
            double[] junctionHeights = new double[network.junctions().size()];
            for (RoadNetwork.Link link : network.links()) {
                double[][] stations = RoadCurve.stations(link);
                RoadType type = choose(ranked, link, stations[0].length);
                if (type == null) {
                    unmatched++;
                    continue;
                }
                links.add(new LinkGeometry(type, stations[0], stations[1]));
                sources.add(link);
                junctionHeightsPerLink.add(junctionHeights);
                maxHalfWidth = Math.max(maxHalfWidth, type.clearedHalfWidth());
            }
        }
        if (unmatched > 0) {
            WFCore.LOGGER.warn("{} road link(s) matched no wfcore:road_type and were skipped", unmatched);
        }
        if (links.isEmpty()) {
            return EMPTY;
        }

        // Phase 1b: drop links that shadow one already kept. The router plans separate sea passages per pair of
        // landmasses, and two pairs funnelled through the same isthmus come out as two roads on the same line —
        // redundancy on paper and none in the world. Rasterising both costs twice the work for one road, and
        // leaves the loser's kerb and centre line drawn down the middle of the winner's carriageway.
        int duplicates = dropShadowed(links, sources, junctionHeightsPerLink);
        if (duplicates > 0) {
            WFCore.LOGGER.info("Roads: dropped {} link(s) that duplicate an existing route", duplicates);
        }

        // Phase 2: every terrain column the whole network needs, from the cache if this world has already paid
        // for them, otherwise one parallel pass over all cores.
        long probeStarted = System.nanoTime();
        long cacheKey = RoadProbeCache.key(seed, links);
        if (cacheFile != null && RoadProbeCache.load(cacheFile, cacheKey, links)) {
            WFCore.LOGGER.info("Roads: terrain for {} link(s) read from cache in {} ms", links.size(),
                    (System.nanoTime() - probeStarted) / 1_000_000L);
        } else {
            RoadProfile.probeAll(links, terrain);
            WFCore.LOGGER.info("Roads: probed terrain for {} link(s) in {} ms", links.size(),
                    (System.nanoTime() - probeStarted) / 1_000_000L);
            if (cacheFile != null) {
                RoadProbeCache.save(cacheFile, cacheKey, links);
            }
        }

        // Phase 3: solve once with no pins, purely to learn what height each road wants where it meets a
        // junction. Taking the junction height off the raw terrain instead leaves a step there — the terrain at
        // a point and a profile smoothed over a hundred blocks either side of it are simply different numbers,
        // and a road cannot be pinned to a value none of the roads meeting it asked for.
        for (LinkGeometry geometry : links) {
            RoadProfile.solve(geometry, OptionalInt.empty(), OptionalInt.empty());
        }
        Map<double[], int[]> counts = new java.util.IdentityHashMap<>();
        for (int i = 0; i < links.size(); i++) {
            double[] junctions = junctionHeightsPerLink.get(i);
            int[] count = counts.computeIfAbsent(junctions, key -> new int[key.length]);
            LinkGeometry geometry = links.get(i);
            RoadNetwork.Link link = sources.get(i);
            link.from().ifPresent(j -> {
                junctions[j] += geometry.deck(0);
                count[j]++;
            });
            link.to().ifPresent(j -> {
                junctions[j] += geometry.deck(geometry.stationCount() - 1);
                count[j]++;
            });
        }
        for (Map.Entry<double[], int[]> entry : counts.entrySet()) {
            double[] junctions = entry.getKey();
            int[] count = entry.getValue();
            for (int j = 0; j < junctions.length; j++) {
                junctions[j] = count[j] > 0 ? Math.round(junctions[j] / count[j]) : Double.NaN;
            }
        }

        // Phase 4: the real solve, pinned. Sequential on purpose — a track's near end is pinned to the height of
        // whatever it branched off, so that road's profile has to exist first, and the file order is the order
        // the router grew them in.
        Long2ObjectMap<IntArrayList> building = new Long2ObjectOpenHashMap<>();
        Runs runs = new Runs();
        for (int i = 0; i < links.size(); i++) {
            LinkGeometry geometry = links.get(i);
            RoadNetwork.Link link = sources.get(i);
            double[] junctionHeights = junctionHeightsPerLink.get(i);
            OptionalInt pinStart = link.from().isPresent()
                    ? junctionHeight(junctionHeights, link.from().getAsInt())
                    : snapTo(links, runs, building, geometry.x(0), geometry.z(0));
            OptionalInt pinEnd = link.to().isPresent()
                    ? junctionHeight(junctionHeights, link.to().getAsInt())
                    : OptionalInt.empty();
            RoadProfile.solve(geometry, pinStart, pinEnd);
            bucket(building, runs, i, geometry);
        }

        Long2ObjectMap<int[]> byChunk = new Long2ObjectOpenHashMap<>(building.size());
        for (Long2ObjectMap.Entry<IntArrayList> entry : building.long2ObjectEntrySet()) {
            byChunk.put(entry.getLongKey(), entry.getValue().toIntArray());
        }
        return new RoadIndex(links, byChunk, runs, maxHalfWidth);
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
            List<double[]> junctions) {
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
                junctions.remove(i);
                removed++;
            }
        }
        return removed;
    }

    private static long cell(double x, double z) {
        return ChunkPos.asLong(Math.floorDiv((int) Math.floor(x), SHADOW_CELL),
                Math.floorDiv((int) Math.floor(z), SHADOW_CELL));
    }

    private static OptionalInt junctionHeight(double[] heights, int index) {
        double height = heights[index];
        return Double.isNaN(height) ? OptionalInt.empty() : OptionalInt.of((int) height);
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

    /** The deck height of the nearest already-solved station, for pinning a track onto the road it branches off. */
    private static OptionalInt snapTo(List<LinkGeometry> links, Runs runs, Long2ObjectMap<IntArrayList> byChunk,
            double x, double z) {
        int chunkX = (int) Math.floor(x) >> 4;
        int chunkZ = (int) Math.floor(z) >> 4;
        double best = SNAP_RADIUS * SNAP_RADIUS;
        int bestDeck = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                IntArrayList found = byChunk.get(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
                if (found == null) {
                    continue;
                }
                for (int i = 0; i < found.size(); i++) {
                    int run = found.getInt(i);
                    LinkGeometry link = links.get(runs.link[run]);
                    for (int s = runs.first[run]; s <= runs.last[run]; s++) {
                        double dxs = link.x(s) - x;
                        double dzs = link.z(s) - z;
                        double distance = dxs * dxs + dzs * dzs;
                        if (distance < best) {
                            best = distance;
                            bestDeck = link.deck(s);
                        }
                    }
                }
            }
        }
        return bestDeck == Integer.MIN_VALUE ? OptionalInt.empty() : OptionalInt.of(bestDeck);
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
