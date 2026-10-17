package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Every road in a level: topology + stations bucketed by chunk up front; probe and profile per link on first use.
 *
 * <p>Link i's final profile depends on its probe, the unpinned profiles of the links sharing its junctions, and
 * the final profiles of lower-indexed links it attaches to or crosses ({@link RoadTopology} order). Acyclic =>
 * lazy result == eager all-links solve.
 */
public final class RoadIndex {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ForkJoinPool PROBE_POOL = new ForkJoinPool(Runtime.getRuntime().availableProcessors(),
            pool -> {
                ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                thread.setName("wfcore-road-probe-" + thread.getPoolIndex());
                return thread;
            }, null, false);

    public static final RoadIndex EMPTY = new RoadIndex(List.of(), List.of(), null, null, List.of());

    private final List<LinkGeometry> links;
    private final List<RoadType> types;
    private final Long2ObjectMap<int[]> byChunk = new Long2ObjectOpenHashMap<>();
    private final Runs runs = new Runs();
    private final int chunkMargin;

    private final RoadProfile.Terrain terrain;
    private final RoadProbeCache cache;
    private final List<RoadTopology.Piece> pieces;
    private final long[] probeKeys;
    private final Long2ObjectMap<IntArrayList> junctionEnds = new Long2ObjectOpenHashMap<>();
    private final Once[] probed;
    private final Once[] unpinned;
    private final Once[] resolved;
    private final RoadProfile.Profile[] unpinnedProfile;

    /** One link's stations; {@code type} indexes the type list given to {@code build}, -1 junction = none. */
    public record Source(int network, int from, int to, RoadSpec spec, int type, double[] xs, double[] zs) {

        public static Source of(int network, RoadNetwork.Link link, RoadSpec spec, int type) {
            double[][] stations = RoadCurve.stations(link);
            return new Source(network, link.from().orElse(-1), link.to().orElse(-1), spec, type, stations[0],
                    stations[1]);
        }
    }

    private RoadIndex(List<RoadTopology.Piece> pieces, List<RoadType> types, RoadProfile.Terrain terrain,
            RoadProbeCache cache, List<Long> keys) {
        this.pieces = pieces;
        this.types = types;
        this.terrain = terrain;
        this.cache = cache;
        int n = pieces.size();
        this.links = new ArrayList<>(n);
        this.probeKeys = new long[n];
        double maxHalfWidth = 0.0;
        Long2ObjectMap<IntArrayList> building = new Long2ObjectOpenHashMap<>();
        for (int i = 0; i < n; i++) {
            RoadTopology.Piece piece = pieces.get(i);
            LinkGeometry link = new LinkGeometry(piece.origin.spec(), piece.origin.type(), piece.xs, piece.zs);
            links.add(link);
            probeKeys[i] = keys.get(i);
            maxHalfWidth = Math.max(maxHalfWidth, link.spec.clearedHalfWidth());
            if (piece.startJunction >= 0) {
                junctionEnds.computeIfAbsent(piece.startJunction, k -> new IntArrayList()).add(i * 2);
            }
            if (piece.endJunction >= 0) {
                junctionEnds.computeIfAbsent(piece.endJunction, k -> new IntArrayList()).add(i * 2 + 1);
            }
            bucket(building, runs, i, link);
        }
        for (Long2ObjectMap.Entry<IntArrayList> entry : building.long2ObjectEntrySet()) {
            byChunk.put(entry.getLongKey(), entry.getValue().toIntArray());
        }
        this.chunkMargin = Math.max(1, (int) Math.ceil(maxHalfWidth / 16.0));
        this.probed = Once.array(n);
        this.unpinned = Once.array(n);
        this.resolved = Once.array(n);
        this.unpinnedProfile = new RoadProfile.Profile[n];
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

    RoadTopology.Piece piece(int index) {
        return pieces.get(index);
    }

    /** The datapack type a link was built from. */
    public RoadType type(LinkGeometry link) {
        return types.get(link.type);
    }

    /** Stations on ground / bridge / tunnel, indexed by {@link RoadProfile} modes. Resolves every link. */
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

    /** Per-chunk slices of a link's stations: flat arrays, box + corridor width precomputed for rejection. */
    static final class Runs {

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
                link = Arrays.copyOf(link, capacity);
                first = Arrays.copyOf(first, capacity);
                last = Arrays.copyOf(last, capacity);
                minX = Arrays.copyOf(minX, capacity);
                minZ = Arrays.copyOf(minZ, capacity);
                maxX = Arrays.copyOf(maxX, capacity);
                maxZ = Arrays.copyOf(maxZ, capacity);
                reach = Arrays.copyOf(reach, capacity);
            }
            double lowX = Double.MAX_VALUE;
            double lowZ = Double.MAX_VALUE;
            double highX = -Double.MAX_VALUE;
            double highZ = -Double.MAX_VALUE;
            for (int station = firstStation; station <= lastStation; station++) {
                lowX = Math.min(lowX, geometry.x(station));
                lowZ = Math.min(lowZ, geometry.z(station));
                highX = Math.max(highX, geometry.x(station));
                highZ = Math.max(highZ, geometry.z(station));
            }
            link[size] = linkIndex;
            first[size] = firstStation;
            last[size] = lastStation;
            minX[size] = (float) lowX;
            minZ[size] = (float) lowZ;
            maxX[size] = (float) highX;
            maxZ[size] = (float) highZ;
            reach[size] = (float) geometry.spec.clearedHalfWidth();
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

    /** Run's box, widened by its corridor, meets the block box. */
    public boolean runReaches(int run, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        float r = runs.reach[run];
        return runs.maxX[run] >= minBlockX - r && runs.minX[run] <= maxBlockX + r
                && runs.maxZ[run] >= minBlockZ - r && runs.minZ[run] <= maxBlockZ + r;
    }

    /** Every run that can reach into the chunk, corridor margin included. */
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
            Path cacheFile) {
        if (networks.size() == 0) {
            return EMPTY;
        }
        List<RoadType> ranked = new ArrayList<>(types.stream().toList());
        if (ranked.isEmpty()) {
            LOGGER.warn("Road networks are loaded but no wfcore:road_type is defined; no roads will generate");
            return EMPTY;
        }
        ranked.sort((a, b) -> Integer.compare(b.match().priority(), a.match().priority()));

        List<Source> sources = new ArrayList<>();
        int unmatched = 0;
        int networkOrdinal = 0;
        for (Map.Entry<ResourceKey<RoadNetwork>, RoadNetwork> entry : networks.entrySet()) {
            RoadNetwork network = entry.getValue();
            if (!network.dimension().equals(dimension)) {
                continue;
            }
            for (RoadNetwork.Link link : network.links()) {
                double[][] stations = RoadCurve.stations(link);
                int type = choose(ranked, link, stations[0].length);
                if (type < 0) {
                    unmatched++;
                    continue;
                }
                sources.add(new Source(networkOrdinal, link.from().orElse(-1), link.to().orElse(-1),
                        ranked.get(type).spec(), type, stations[0], stations[1]));
            }
            networkOrdinal++;
        }
        if (unmatched > 0) {
            LOGGER.warn("{} road link(s) matched no wfcore:road_type and were skipped", unmatched);
        }
        return build(new GeneratorTerrain(generator, randomState, heights), sources, ranked, seed, cacheFile);
    }

    /** {@code cacheFile == null} => no probe cache. */
    static RoadIndex build(RoadProfile.Terrain terrain, List<Source> sources, List<RoadType> types, long seed,
            Path cacheFile) {
        if (sources.isEmpty()) {
            return EMPTY;
        }
        Integer[] order = new Integer[sources.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            Source ca = sources.get(a);
            Source cb = sources.get(b);
            int byClass = Integer.compare(cb.spec().priority(), ca.spec().priority());
            if (byClass != 0) {
                return byClass;
            }
            int byLength = Integer.compare(cb.xs().length, ca.xs().length);
            return byLength != 0 ? byLength : Integer.compare(a, b);
        });
        List<Source> ranked = new ArrayList<>(order.length);
        for (Integer i : order) {
            ranked.add(sources.get(i));
        }

        long started = System.nanoTime();
        List<RoadTopology.Piece> pieces = RoadTopology.build(ranked);
        LOGGER.info("Roads: {} link(s) -> {} piece(s) after overlap trimming in {} ms", sources.size(),
                pieces.size(), (System.nanoTime() - started) / 1_000_000L);

        List<Long> keys = new ArrayList<>(pieces.size());
        LongOpenHashSet validKeys = new LongOpenHashSet();
        for (RoadTopology.Piece piece : pieces) {
            long key = RoadProbeCache.key(seed, piece.origin.spec().grade().sampleSpacing(), piece.xs, piece.zs);
            keys.add(key);
            validKeys.add(key);
        }
        RoadProbeCache cache = cacheFile == null ? null : RoadProbeCache.open(cacheFile, validKeys);
        return new RoadIndex(pieces, types, terrain, cache, keys);
    }

    private void probe(int index) {
        probed[index].run(() -> {
            LinkGeometry link = links.get(index);
            short[][] cached = cache == null ? null : cache.get(probeKeys[index]);
            if (cached != null && cached[0].length == link.stationCount()) {
                link.setTerrain(cached[0], cached[1]);
                return;
            }
            long started = System.nanoTime();
            awaitOnProbePool(() -> RoadProfile.probe(link, terrain));
            if (cache != null) {
                cache.put(probeKeys[index], link.ground, link.water);
            }
            LOGGER.debug("Roads: probed link {} ({} stations) in {} ms", index, link.stationCount(),
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

    private RoadProfile.Profile unpinned(int index) {
        unpinned[index].run(() -> {
            probe(index);
            unpinnedProfile[index] = RoadProfile.solve(links.get(index));
        });
        return unpinnedProfile[index];
    }

    private int junctionHeight(long junction) {
        IntArrayList ends = junctionEnds.get(junction);
        double sum = 0.0;
        for (int i = 0; i < ends.size(); i++) {
            int end = ends.getInt(i);
            short[] deck = unpinned(end >> 1).deck();
            sum += (end & 1) == 0 ? deck[0] : deck[deck.length - 1];
        }
        return (int) Math.round(sum / ends.size());
    }

    private void resolve(int index) {
        resolved[index].run(() -> {
            LinkGeometry geometry = links.get(index);
            RoadTopology.Piece piece = pieces.get(index);
            probe(index);
            int last = geometry.stationCount() - 1;
            Pins pins = new Pins();
            if (piece.startJunction >= 0) {
                pins.add(0, junctionHeight(piece.startJunction));
            } else if (piece.startAttach >= 0) {
                pins.add(0, link(piece.startAttach).deck(piece.startAttachAt));
            }
            if (piece.endJunction >= 0) {
                pins.add(last, junctionHeight(piece.endJunction));
            } else if (piece.endAttach >= 0) {
                pins.add(last, link(piece.endAttach).deck(piece.endAttachAt));
            }
            IntArrayList forced = new IntArrayList();
            int count = piece.crossings.size() / 3;
            if (count > 0) {
                RoadProfile.Profile own = unpinned(index);
                Integer[] order = new Integer[count];
                for (int c = 0; c < count; c++) {
                    order[c] = c * 3;
                }
                Arrays.sort(order, (a, b) -> Integer.compare(piece.crossings.getInt(a), piece.crossings.getInt(b)));
                int[] stations = new int[count];
                int[][] options = new int[count][];
                double[] skip = new double[count];
                for (int c = 0; c < count; c++) {
                    int station = piece.crossings.getInt(order[c]);
                    int partner = piece.crossings.getInt(order[c] + 1);
                    int partnerStation = piece.crossings.getInt(order[c] + 2);
                    LinkGeometry other = link(partner);
                    int slack = endSlack(geometry, other);
                    stations[c] = station;
                    options[c] = crossingOptions(geometry.spec, own.deck()[station], other.spec,
                            other.deck(partnerStation), other.mode(partnerStation),
                            partnerStation <= slack || partnerStation >= other.stationCount() - 1 - slack);
                    skip[c] = SKIP_COST * (pieces.size() - partner);
                }
                int[] chosen = chooseCrossings(stations, options, skip, pins.at(), pins.value(),
                        geometry.spec.grade().max() * RoadCurve.STATION_SPACING);
                for (int c = 0; c < count; c++) {
                    if (chosen[c] == Integer.MIN_VALUE) {
                        continue;
                    }
                    pins.add(stations[c], chosen[c]);
                    int partner = piece.crossings.getInt(order[c] + 1);
                    int partnerStation = piece.crossings.getInt(order[c] + 2);
                    LinkGeometry other = link(partner);
                    if (chosen[c] - other.deck(partnerStation) >= headroom(other.spec, other.mode(partnerStation)) + 2) {
                        int reach = overpassReach(geometry, stations[c], other, partnerStation);
                        forced.add(stations[c] - reach);
                        forced.add(stations[c] + reach);
                    }
                }
            }
            RoadProfile.Profile profile = RoadProfile.solve(geometry, pins.at(), pins.value(), forced.toIntArray());
            geometry.setProfile(profile.deck(), profile.mode(), profile.portal());
        });
    }

    /** Stations ascending; first-added wins on a shared station. */
    private static final class Pins {

        private final IntArrayList at = new IntArrayList(4);
        private final IntArrayList value = new IntArrayList(4);

        void add(int station, int height) {
            int i = 0;
            while (i < at.size() && at.getInt(i) < station) {
                i++;
            }
            if (i < at.size() && at.getInt(i) == station) {
                return;
            }
            at.add(i, station);
            value.add(i, height);
        }

        int[] at() {
            return at.toIntArray();
        }

        int[] value() {
            return value.toIntArray();
        }
    }

    static int headroom(RoadSpec spec, byte mode) {
        return mode == RoadProfile.TUNNEL ? spec.tunnelHeight() + 1 : spec.clearance();
    }

    private static int endSlack(LinkGeometry geometry, LinkGeometry other) {
        return (int) Math.ceil(geometry.spec.outerHalfWidth() + other.spec.outerHalfWidth()
                + RoadTopology.SHADOW_GAP);
    }

    /** Per-crossing skip cost unit; above any height deviation, scaled by partner importance. */
    static final double SKIP_COST = 1.0e6;
    /** Per block two consecutive pins exceed grade by; between deviation and skip. */
    static final double STEEP_COST = 1.0e5;
    /** Consecutive pins may need at most this x grade; beyond => skip. */
    static final double MAX_STEEPNESS = 3.0;

    /**
     * Heights the lesser road may take where it crosses the greater one at {@code partner}, as (height, cost =
     * |height - own|) pairs: at grade, over / under by the lower road's headroom + deck, and its own height when
     * already that clear. The lesser road's headroom assumes a tunnel: its final mode is unknown until pinned. A
     * crossing at the greater road's end is a T => at grade only.
     */
    static int[] crossingOptions(RoadSpec spec, int own, RoadSpec partnerSpec, int partner, byte partnerMode,
            boolean partnerEnd) {
        int over = partner + headroom(partnerSpec, partnerMode) + 2;
        int under = partner - Math.max(spec.clearance(), spec.tunnelHeight() + 1) - 2;
        IntArrayList out = new IntArrayList(8);
        out.add(partner);
        out.add(Math.abs(partner - own));
        if (!partnerEnd) {
            out.add(over);
            out.add(Math.abs(over - own));
            out.add(under);
            out.add(Math.abs(under - own));
            if (own >= over || own <= under) {
                out.add(own);
                out.add(0);
            }
        }
        return out.toIntArray();
    }

    /**
     * Viterbi over crossings in station order: each takes one of its (height, cost) options or is skipped (cost
     * {@code skip}); consecutive pins, fixed end pins included, must be reachable within {@code MAX_STEEPNESS x
     * maxRise} per station, paying {@link #STEEP_COST} per block over {@code maxRise}. Minimises option costs +
     * steepness + skips. Fixed pins never skip; two fixed pins are never tested against each other.
     *
     * @return height per crossing, {@link Integer#MIN_VALUE} = skipped
     */
    static int[] chooseCrossings(int[] stations, int[][] options, double[] skip, int[] fixedAt, int[] fixedY,
            double maxRise) {
        int crossings = stations.length;
        int nodes = crossings + fixedAt.length;
        int[] at = new int[nodes];
        int[][] values = new int[nodes][];
        double[] skipCost = new double[nodes];
        int[] crossingOf = new int[nodes];
        Integer[] order = new Integer[nodes];
        for (int i = 0; i < nodes; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int sa = a < crossings ? stations[a] : fixedAt[a - crossings];
            int sb = b < crossings ? stations[b] : fixedAt[b - crossings];
            return sa != sb ? Integer.compare(sa, sb) : Integer.compare(b, a);
        });
        for (int k = 0; k < nodes; k++) {
            int i = order[k];
            if (i < crossings) {
                at[k] = stations[i];
                values[k] = options[i];
                skipCost[k] = skip[i];
                crossingOf[k] = i;
            } else {
                at[k] = fixedAt[i - crossings];
                values[k] = new int[] {fixedY[i - crossings], 0};
                crossingOf[k] = -1;
            }
        }
        // Fixed nodes cannot be skipped: counted apart, never summed as infinity (inf - inf = NaN).
        double[] skipPrefix = new double[nodes + 1];
        int[] fixedPrefix = new int[nodes + 1];
        for (int k = 0; k < nodes; k++) {
            skipPrefix[k + 1] = skipPrefix[k] + skipCost[k];
            fixedPrefix[k + 1] = fixedPrefix[k] + (crossingOf[k] < 0 ? 1 : 0);
        }
        double[][] best = new double[nodes][];
        int[][] fromNode = new int[nodes][];
        int[][] fromOption = new int[nodes][];
        for (int j = 0; j < nodes; j++) {
            int m = values[j].length / 2;
            best[j] = new double[m];
            fromNode[j] = new int[m];
            fromOption[j] = new int[m];
            for (int o = 0; o < m; o++) {
                int v = values[j][o * 2];
                double local = values[j][o * 2 + 1];
                best[j][o] = fixedPrefix[j] == 0 ? skipPrefix[j] + local : Double.POSITIVE_INFINITY;
                fromNode[j][o] = -1;
                for (int i = 0; i < j; i++) {
                    if (fixedPrefix[j] != fixedPrefix[i + 1]) {
                        continue;
                    }
                    double between = skipPrefix[j] - skipPrefix[i + 1];
                    for (int p = 0; p < values[i].length / 2; p++) {
                        double rise = Math.abs(v - values[i][p * 2]);
                        double budget = maxRise * (at[j] - at[i]);
                        double steep = 0.0;
                        if (crossingOf[i] >= 0 || crossingOf[j] >= 0) {
                            if (rise > MAX_STEEPNESS * budget + 1e-9) {
                                continue;
                            }
                            steep = Math.max(0.0, rise - budget) * STEEP_COST;
                        }
                        double cost = best[i][p] + between + local + steep;
                        if (cost < best[j][o]) {
                            best[j][o] = cost;
                            fromNode[j][o] = i;
                            fromOption[j][o] = p;
                        }
                    }
                }
            }
        }
        double total = fixedPrefix[nodes] == 0 ? skipPrefix[nodes] : Double.POSITIVE_INFINITY;
        int lastNode = -1;
        int lastOption = -1;
        for (int i = 0; i < nodes; i++) {
            if (fixedPrefix[nodes] != fixedPrefix[i + 1]) {
                continue;
            }
            for (int p = 0; p < values[i].length / 2; p++) {
                double cost = best[i][p] + skipPrefix[nodes] - skipPrefix[i + 1];
                if (cost < total) {
                    total = cost;
                    lastNode = i;
                    lastOption = p;
                }
            }
        }
        int[] chosen = new int[crossings];
        Arrays.fill(chosen, Integer.MIN_VALUE);
        while (lastNode >= 0) {
            if (crossingOf[lastNode] >= 0) {
                chosen[crossingOf[lastNode]] = values[lastNode][lastOption * 2];
            }
            int previous = fromNode[lastNode][lastOption];
            lastOption = previous >= 0 ? fromOption[lastNode][lastOption] : -1;
            lastNode = previous;
        }
        return chosen;
    }

    /** Half-length, in stations, of the stretch of {@code link} over the partner's deck. */
    private static int overpassReach(LinkGeometry link, int station, LinkGeometry other, int partnerStation) {
        double ax = direction(link, station, true);
        double az = direction(link, station, false);
        double bx = direction(other, partnerStation, true);
        double bz = direction(other, partnerStation, false);
        double sin = Math.max(0.3, Math.abs(ax * bz - az * bx));
        return (int) Math.ceil((other.spec.outerHalfWidth() + 1.0) / sin);
    }

    private static double direction(LinkGeometry link, int station, boolean x) {
        int a = Math.max(0, station - 2);
        int b = Math.min(link.stationCount() - 1, station + 2);
        double dx = link.x(b) - link.x(a);
        double dz = link.z(b) - link.z(a);
        double length = Math.sqrt(dx * dx + dz * dz);
        return length == 0.0 ? 0.0 : (x ? dx : dz) / length;
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

    /** Index into {@code ranked} of the highest-priority type matching, or -1. */
    private static int choose(List<RoadType> ranked, RoadNetwork.Link link, int length) {
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).match().matches(link, length)) {
                return i;
            }
        }
        return -1;
    }

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

        private final RoadSpec spec;
        private final int type;
        private final double[] xs;
        private final double[] zs;
        private short[] ground;
        private short[] water;
        private short[] deck;
        private byte[] mode;
        private boolean[] portal;

        LinkGeometry(RoadSpec spec, int type, double[] xs, double[] zs) {
            this.spec = spec;
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

        public RoadSpec spec() {
            return spec;
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

    /** Noise-router height: never loads or generates a chunk. */
    private record GeneratorTerrain(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heights,
            int seaLevel) implements RoadProfile.Terrain {

        GeneratorTerrain(ChunkGenerator generator, RandomState randomState, LevelHeightAccessor heights) {
            this(generator, randomState, heights, generator.getSeaLevel());
        }

        @Override
        public long sample(int x, int z) {
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState) - 1;
            // Above sea level nothing can hold water => skip the second density evaluation.
            int ground = surface > seaLevel ? surface
                    : generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, randomState) - 1;
            return ((long) ground << 32) | (surface & 0xFFFFFFFFL);
        }
    }
}
