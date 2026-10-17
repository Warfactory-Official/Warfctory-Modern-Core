package com.norwood.wfcore.common.worldgen.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntBinaryOperator;

/** Random networks over noise terrain with water: whole-network invariants. */
class RoadFuzzTest {

    private static final int SIZE = 1400;
    /** Skipped crossing (no reachable clear height) still merges into one surface with at most this step. */
    private static final int MERGED_STEP = 2;

    /** Smooth value noise in [-1, 1]. */
    static double noise(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double sx = fx * fx * (3 - 2 * fx);
        double sz = fz * fz * (3 - 2 * fz);
        double a = lattice(seed, x0, z0);
        double b = lattice(seed, x0 + 1, z0);
        double c = lattice(seed, x0, z0 + 1);
        double d = lattice(seed, x0 + 1, z0 + 1);
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz;
    }

    private static double lattice(long seed, int x, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    static IntBinaryOperator terrain(long seed) {
        return (x, z) -> (int) Math.round(66 + 28 * noise(seed, x / 220.0, z / 220.0)
                + 9 * noise(seed + 1, x / 60.0, z / 60.0) + 2 * noise(seed + 2, x / 12.0, z / 12.0));
    }

    record Network(List<RoadIndex.Source> sources) {}

    static Network network(long seed) {
        Random random = new Random(seed);
        int nodes = 6 + random.nextInt(5);
        int[][] node = new int[nodes][];
        for (int i = 0; i < nodes; i++) {
            node[i] = new int[] {random.nextInt(SIZE) - SIZE / 2, random.nextInt(SIZE) - SIZE / 2};
        }
        List<RoadIndex.Source> sources = new ArrayList<>();
        List<int[]> polylines = new ArrayList<>();
        List<int[]> pairs = new ArrayList<>();
        for (int i = 1; i < nodes; i++) {
            pairs.add(new int[] {random.nextInt(i), i});
        }
        for (int k = 0; k < 3; k++) {
            int a = random.nextInt(nodes);
            int b = random.nextInt(nodes);
            if (a != b) {
                pairs.add(new int[] {a, b});
            }
        }
        for (int[] pair : pairs) {
            int[] a = node[pair[0]];
            int[] b = node[pair[1]];
            int bends = random.nextInt(4);
            int[] pts = new int[(bends + 2) * 2];
            pts[0] = a[0];
            pts[1] = a[1];
            for (int k = 1; k <= bends; k++) {
                double t = (double) k / (bends + 1);
                pts[k * 2] = (int) Math.round(a[0] + (b[0] - a[0]) * t + random.nextGaussian() * 80);
                pts[k * 2 + 1] = (int) Math.round(a[1] + (b[1] - a[1]) * t + random.nextGaussian() * 80);
            }
            pts[pts.length - 2] = b[0];
            pts[pts.length - 1] = b[1];
            polylines.add(pts);
            double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
            RoadSpec spec = length > 900 ? RoadWorld.highway() : RoadWorld.road();
            sources.add(RoadIndex.Source.of(0, RoadWorld.link("paved", pair[0], pair[1], pts), spec, 0));
        }
        for (int k = 0; k < 2; k++) {
            int[] base = polylines.get(random.nextInt(polylines.size()));
            int offset = random.nextInt(7);
            int[] copy = base.clone();
            for (int i = 1; i < copy.length; i += 2) {
                copy[i] += offset;
            }
            sources.add(RoadIndex.Source.of(0, RoadWorld.link("paved", -1, -1, copy), RoadWorld.road(), 0));
        }
        int tracks = 4 + random.nextInt(5);
        for (int k = 0; k < tracks; k++) {
            int[] base = polylines.get(random.nextInt(polylines.size()));
            int v = random.nextInt(base.length / 2);
            int sx = base[v * 2];
            int sz = base[v * 2 + 1];
            double angle = random.nextDouble() * Math.PI * 2;
            int length = 100 + random.nextInt(300);
            int mx = (int) Math.round(sx + Math.cos(angle) * length / 2 + random.nextGaussian() * 30);
            int mz = (int) Math.round(sz + Math.sin(angle) * length / 2 + random.nextGaussian() * 30);
            int ex = (int) Math.round(sx + Math.cos(angle) * length);
            int ez = (int) Math.round(sz + Math.sin(angle) * length);
            int[] pts = {sx, sz, mx, mz, ex, ez};
            polylines.add(pts);
            sources.add(RoadIndex.Source.of(0, RoadWorld.link("track", -1, -1, pts), RoadWorld.track(), 0));
        }
        return new Network(sources);
    }

    static RoadIndex build(long seed, RoadWorld world) {
        return RoadIndex.build(world, network(seed).sources(), List.of(), seed, null);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            25, 26, 27, 28, 29, 30})
    void networkInvariants(int seed) {
        RoadWorld world = new RoadWorld(terrain(seed), seed % 3 == 0 ? 60 : RoadWorld.MIN_Y);
        RoadIndex index = build(seed, world);

        int stations = 0;
        int steep = 0;
        int crossings = 0;
        int unclear = 0;
        double worstGrade = 0.0;
        for (int i = 0; i < index.linkCount(); i++) {
            RoadIndex.LinkGeometry link = index.link(i);
            RoadTopology.Piece piece = index.piece(i);
            stations += link.stationCount();
            for (int s = 1; s < link.stationCount(); s++) {
                if (Math.abs(link.deck(s) - link.deck(s - 1)) > 1) {
                    steep++;
                }
            }
            for (int s = 0; s + 20 < link.stationCount(); s++) {
                worstGrade = Math.max(worstGrade,
                        Math.abs(link.deck(s + 20) - link.deck(s)) / 20.0 / link.spec().grade().max());
            }
            if (piece.startAttach >= 0) {
                assertEquals(index.link(piece.startAttach).deck(piece.startAttachAt), link.deck(0),
                        "start attachment height, link " + i);
            }
            if (piece.endAttach >= 0) {
                assertEquals(index.link(piece.endAttach).deck(piece.endAttachAt),
                        link.deck(link.stationCount() - 1), "end attachment height, link " + i);
            }
            for (int c = 0; c < piece.crossings.size(); c += 3) {
                RoadIndex.LinkGeometry other = index.link(piece.crossings.getInt(c + 1));
                int sl = piece.crossings.getInt(c);
                int sk = piece.crossings.getInt(c + 2);
                int gap = link.deck(sl) - other.deck(sk);
                boolean ok = Math.abs(gap) <= MERGED_STEP
                        || gap >= RoadIndex.headroom(other.spec(), other.mode(sk)) + 2
                        || -gap >= RoadIndex.headroom(link.spec(), link.mode(sl)) + 2;
                if (!ok) {
                    System.out.printf("seed %d link %d station %d: crossing gap %d%n", seed, i, sl, gap);
                    unclear++;
                }
                crossings++;
            }
        }
        assertJunctionsAgree(index);
        assertEquals(0, unclear, "crossings neither merged nor clear");
        assertEquals(0, steep, "adjacent-station steps > 1 block");
        // Rounded decks: 1 block over 20 stations on top of the steepest allowed ramp.
        assertTrue(worstGrade <= RoadIndex.MAX_STEEPNESS + 1.0 / 20 / 0.05, "worst grade " + worstGrade);

        world.rasterize(index);
        int surfaces = 0;
        int blocked = 0;
        for (int i = 0; i < index.linkCount(); i++) {
            RoadIndex.LinkGeometry link = index.link(i);
            for (int s = 0; s < link.stationCount(); s += 3) {
                int x = (int) Math.floor(link.x(s));
                int z = (int) Math.floor(link.z(s));
                int deck = link.deck(s);
                boolean found = false;
                for (int y = deck - RoadRasterizer.SAME_SURFACE; y <= deck + RoadRasterizer.SAME_SURFACE; y++) {
                    RoadRasterizer.Part part = world.part(x, y, z);
                    if (part != null && RoadWorld.isDeck(part)) {
                        found = true;
                        surfaces++;
                        if (part != RoadRasterizer.Part.TUNNEL_FLOOR && part != RoadRasterizer.Part.ACCENT) {
                            for (int h = 1; h <= 3; h++) {
                                if (!world.air(x, y + h, z)) {
                                    blocked++;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
                assertTrue(found, "no deck at link " + i + " station " + s + " (" + x + "," + z + ") near "
                        + deck + " mode " + link.mode(s));
            }
        }
        assertEquals(0, blocked, "driving surfaces with blocked headroom, of " + surfaces);
        System.out.printf("seed %d: %d links, %d stations, %d crossings, %d steep steps, worst 20-station grade"
                + " %.2fx design%n", seed, index.linkCount(), stations, crossings, steep, worstGrade);
    }

    private static void assertJunctionsAgree(RoadIndex index) {
        java.util.Map<Long, Integer> height = new java.util.HashMap<>();
        for (int i = 0; i < index.linkCount(); i++) {
            RoadTopology.Piece piece = index.piece(i);
            RoadIndex.LinkGeometry link = index.link(i);
            check(height, piece.startJunction, link.deck(0));
            check(height, piece.endJunction, link.deck(link.stationCount() - 1));
        }
    }

    private static void check(java.util.Map<Long, Integer> height, long junction, int deck) {
        if (junction < 0) {
            return;
        }
        Integer seen = height.putIfAbsent(junction, deck);
        if (seen != null) {
            assertEquals((int) seen, deck, "junction " + junction + " heights disagree");
        }
    }

    @Test
    void lazyParallelResolutionAndChunkOrderAreDeterministic() throws Exception {
        long seed = 77;
        RoadWorld reference = new RoadWorld(terrain(seed), 60);
        RoadIndex sequential = build(seed, reference);
        reference.rasterize(sequential);

        RoadWorld shuffled = new RoadWorld(terrain(seed), 60);
        RoadIndex parallel = build(seed, shuffled);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < parallel.linkCount(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(seed));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i : order) {
                futures.add(pool.submit(() -> parallel.link(i)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
        List<long[]> chunks = RoadWorld.chunks(parallel, 32);
        Collections.shuffle(chunks, new Random(seed + 1));
        shuffled.rasterize(parallel, chunks);

        for (int i = 0; i < sequential.linkCount(); i++) {
            RoadIndex.LinkGeometry a = sequential.link(i);
            RoadIndex.LinkGeometry b = parallel.link(i);
            for (int s = 0; s < a.stationCount(); s++) {
                assertEquals(a.deck(s), b.deck(s));
                assertEquals(a.mode(s), b.mode(s));
            }
        }
        assertEquals(reference.snapshot(), shuffled.snapshot());
    }
}
