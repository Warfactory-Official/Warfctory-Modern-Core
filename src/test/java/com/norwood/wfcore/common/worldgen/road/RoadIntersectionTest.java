package com.norwood.wfcore.common.worldgen.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

/** Junctions, at-grade crossings, overpasses, underpasses, tunnels under roads, T attachments, shared corridors. */
class RoadIntersectionTest {

    private static boolean paved(RoadRasterizer.Part part) {
        return part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.MARKING
                || part == RoadRasterizer.Part.BRIDGE_DECK;
    }

    private static double distance(RoadIndex.LinkGeometry link, double x, double z) {
        return RoadWidthTest.distance(link, x, z, 0);
    }

    private static RoadIndex.Source paved(RoadSpec spec, int from, int to, int... xz) {
        return RoadWorld.source(spec, RoadWorld.link("paved", from, to, xz));
    }

    /** Every column inside any link's paved band carries a deck within SAME_SURFACE of that link's deck. */
    private static void assertNoHoles(RoadWorld world, RoadIndex index) {
        for (int i = 0; i < index.linkCount(); i++) {
            RoadIndex.LinkGeometry link = index.link(i);
            double half = link.spec().pavedHalfWidth() - 1e-6;
            for (int s = 0; s < link.stationCount(); s++) {
                int cx = (int) Math.floor(link.x(s));
                int cz = (int) Math.floor(link.z(s));
                for (int x = cx - 8; x <= cx + 8; x++) {
                    for (int z = cz - 8; z <= cz + 8; z++) {
                        if (distance(link, x + 0.5, z + 0.5) >= half) {
                            continue;
                        }
                        int deck = link.deck(s);
                        boolean found = false;
                        for (int y = deck - RoadRasterizer.SAME_SURFACE; y <= deck + RoadRasterizer.SAME_SURFACE; y++) {
                            RoadRasterizer.Part part = world.part(x, y, z);
                            if (part != null && RoadWorld.isDeck(part)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            fail("hole at " + x + "," + z + " in link " + i + " near deck " + deck);
                        }
                    }
                }
            }
        }
    }

    /** Adjacent paved columns differ by at most one block inside the box. */
    private static void assertSmooth(RoadWorld world, int minX, int minZ, int maxX, int maxZ, int from, int to) {
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                IntArrayList here = world.decks(x, z, from, to);
                if (here.size() != 1) {
                    continue;
                }
                for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
                    IntArrayList next = world.decks(x + d[0], z + d[1], from, to);
                    if (next.size() == 1) {
                        assertTrue(Math.abs(next.getInt(0) - here.getInt(0)) <= 1,
                                "step " + here.getInt(0) + " -> " + next.getInt(0) + " at " + x + "," + z);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6})
    void multiWayJunctionHasNoHolesOrKerbsInside(int arms) {
        RoadWorld world = RoadWorld.flat(64);
        RoadSpec spec = RoadWorld.spec().markings(1, 4, 4).build();
        List<RoadIndex.Source> sources = new ArrayList<>();
        for (int a = 0; a < arms; a++) {
            double angle = Math.PI * 2 * a / arms + 0.3;
            sources.add(paved(spec, 0, a + 1, 0, 0, (int) Math.round(Math.cos(angle) * 200),
                    (int) Math.round(Math.sin(angle) * 200)));
        }
        RoadIndex index = RoadIndex.build(world, sources, List.of(), 1L, null);
        assertEquals(arms, index.linkCount());
        world.rasterize(index);
        assertNoHoles(world, index);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                RoadRasterizer.Part part = world.part(x, 64, z);
                if (part != RoadRasterizer.Part.EDGE) {
                    continue;
                }
                for (int i = 0; i < index.linkCount(); i++) {
                    assertTrue(distance(index.link(i), x + 0.5, z + 0.5) >= spec.pavedHalfWidth() - 1e-6,
                            "shoulder inside carriageway at " + x + "," + z);
                }
            }
        }
        assertSmooth(world, -30, -30, 30, 30, 50, 80);
    }

    @Test
    void narrowRoadCrossingHighwayKeepsHighwayIntact() {
        RoadWorld world = RoadWorld.flat(64);
        RoadSpec highway = RoadWorld.highway();
        RoadIndex index = RoadWorld.index(world, paved(highway, -1, -1, -400, 0, 400, 0),
                paved(RoadWorld.track(), -1, -1, 0, -200, 0, 200));
        world.rasterize(index);
        assertNoHoles(world, index);
        RoadIndex.LinkGeometry main = index.link(0);
        for (int x = -30; x <= 30; x++) {
            int paved = 0;
            for (int z = -10; z <= 10; z++) {
                RoadRasterizer.Part part = world.part(x, 64, z);
                if (part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.MARKING) {
                    paved++;
                }
                if (world.owner(x, 64, z) != null && Math.abs(z + 0.5) < highway.pavedHalfWidth()) {
                    assertEquals(main, world.owner(x, 64, z), "highway carriageway taken over at " + x + "," + z);
                }
            }
            assertTrue(paved >= highway.width(), "highway width broken at x=" + x + ": " + paved);
        }
        int marked = 0;
        for (int x = -6; x <= 6; x++) {
            for (int z = -1; z <= 0; z++) {
                if (world.part(x, 64, z) == RoadRasterizer.Part.MARKING) {
                    marked++;
                }
            }
        }
        assertTrue(marked > 0, "highway centre line interrupted at the crossing");
    }

    @Test
    void atGradeCrossingMeetsAtOneHeight() {
        RoadWorld world = new RoadWorld((x, z) -> Math.abs(x) < 10 ? 61 : 64, RoadWorld.MIN_Y);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -500, 0, 500, 0),
                paved(spec, -1, -1, 0, -300, 0, 300));
        world.rasterize(index);
        RoadIndex.LinkGeometry major = index.link(0);
        RoadIndex.LinkGeometry minor = index.link(1);
        int a = major.deck(nearest(major, 0, 0));
        int b = minor.deck(nearest(minor, 0, 0));
        assertEquals(a, b, "decks at the crossing");
        assertNoHoles(world, index);
        assertSmooth(world, -15, -15, 15, 15, 40, 90);
    }

    @Test
    void overpassSpansValleyRoadWithFullHeadroom() {
        RoadWorld world = new RoadWorld((x, z) -> Math.abs(z) < 30 ? 60 : 75, RoadWorld.MIN_Y);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -500, 0, 500, 0),
                paved(spec, -1, -1, 0, -300, 0, 300));
        world.rasterize(index);
        RoadIndex.LinkGeometry low = index.link(0);
        RoadIndex.LinkGeometry high = index.link(1);
        int lowDeck = low.deck(nearest(low, 0, 0));
        int crossing = nearest(high, 0, 0);
        int highDeck = high.deck(crossing);
        assertTrue(highDeck - lowDeck >= spec.clearance() + 2, "separation " + (highDeck - lowDeck));
        assertEquals(RoadProfile.BRIDGE, high.mode(crossing));

        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                int upper = high.deck(nearest(high, x + 0.5, z + 0.5));
                if (Math.abs(z + 0.5) < spec.pavedHalfWidth() - 1e-6) {
                    assertTrue(paved(world.part(x, lowDeck, z)), "lower deck missing at " + x + "," + z);
                }
                if (Math.abs(z + 0.5) < spec.outerHalfWidth() - 1e-6) {
                    for (int y = lowDeck + 1; y <= lowDeck + spec.clearance(); y++) {
                        assertTrue(world.air(x, y, z), "lower headroom blocked at " + x + "," + y + "," + z
                                + " by " + world.part(x, y, z));
                    }
                }
                if (Math.abs(x + 0.5) < spec.pavedHalfWidth() - 1e-6) {
                    assertTrue(paved(world.part(x, upper, z)), "upper deck missing at " + x + "," + z);
                    assertEquals(RoadRasterizer.Part.STRUCTURE, world.part(x, upper - 1, z));
                }
            }
        }
        for (int z = -12; z <= 12; z++) {
            int upper = high.deck(nearest(high, 0, z + 0.5));
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int x = -12; x <= 12; x++) {
                RoadRasterizer.Part part = world.part(x, upper, z);
                if (part != null && RoadWorld.isDeck(part)) {
                    min = Math.min(min, x);
                    max = Math.max(max, x);
                }
            }
            assertEquals((int) Math.round(spec.outerHalfWidth() * 2), max - min + 1, "upper deck width at z=" + z);
            for (int x = min; x <= max; x++) {
                boolean rail = world.part(x, upper + 1, z) == RoadRasterizer.Part.RAILING;
                assertEquals(x == min || x == max, rail, "railing at " + x + "," + z);
            }
        }
        assertRailingsSymmetric(world);
    }

    @Test
    void valleyRoadIsNotBuriedByPiers() {
        RoadWorld world = new RoadWorld((x, z) -> Math.abs(z) < 60 ? 40 : 75, RoadWorld.MIN_Y);
        RoadSpec spec = RoadWorld.spec().piers(4, 3).build();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -500, 0, 500, 0),
                paved(spec, -1, -1, 0, -300, 0, 300));
        world.rasterize(index);
        int lowDeck = index.link(0).deck(nearest(index.link(0), 0, 0));
        int highDeck = index.link(1).deck(nearest(index.link(1), 0, 0));
        for (int x = -6; x <= 6; x++) {
            for (int z = -5; z <= 5; z++) {
                if (Math.abs(z + 0.5) >= spec.outerHalfWidth() - 1e-6) {
                    continue;
                }
                for (int y = lowDeck + 1; y < highDeck - 1; y++) {
                    assertTrue(world.air(x, y, z), "pier or fill over the lower road at " + x + "," + y + "," + z);
                }
            }
        }
        boolean pier = false;
        for (int z = -55; z <= 55 && !pier; z++) {
            pier = Math.abs(z) > 10 && world.part(0, 41, z) == RoadRasterizer.Part.STRUCTURE;
        }
        assertTrue(pier, "piers still stand outside the lower corridor");
    }

    @Test
    void tunnelUnderRoadLeavesBothIntact() {
        RoadWorld world = new RoadWorld((x, z) -> Math.abs(x) < 150 ? 130 : 64, RoadWorld.MIN_Y);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, 0, -500, 0, 500),
                paved(spec, -1, -1, -400, 0, 400, 0));
        world.rasterize(index);
        RoadIndex.LinkGeometry top = index.link(0);
        RoadIndex.LinkGeometry bore = index.link(1);
        int crossing = nearest(bore, 0, 0);
        assertEquals(RoadProfile.TUNNEL, bore.mode(crossing));
        int boreDeck = bore.deck(crossing);
        int topDeck = top.deck(nearest(top, 0, 0));
        int ceiling = boreDeck + spec.tunnelHeight() + 1;
        assertTrue(topDeck > ceiling + 1);
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                assertTrue(paved(world.part(x, topDeck, z)) || world.part(x, topDeck, z) == RoadRasterizer.Part.MARKING,
                        "surface road broken over tunnel at " + x + "," + z);
                RoadRasterizer.Part roof = world.part(x, ceiling, z);
                assertTrue(roof == RoadRasterizer.Part.CEILING || roof == RoadRasterizer.Part.LIGHT,
                        "tunnel roof at " + x + "," + z + ": " + roof);
                for (int y = boreDeck + 1; y <= boreDeck + spec.tunnelHeight(); y++) {
                    if (Math.abs(z + 0.5) < spec.pavedHalfWidth() - 1e-6) {
                        assertTrue(world.air(x, y, z), "bore blocked at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void trackAttachesAtRoadHeight() {
        RoadWorld world = new RoadWorld((x, z) -> z > 0 ? 64 + z / 8 : 64, RoadWorld.MIN_Y);
        RoadIndex index = RoadWorld.index(world, paved(RoadWorld.road(), 0, 1, -400, 0, 400, 0),
                RoadWorld.source(RoadWorld.track(), RoadWorld.link("track", -1, -1, 100, 0, 100, 300)));
        world.rasterize(index);
        RoadIndex.LinkGeometry road = index.link(0);
        RoadIndex.LinkGeometry track = index.link(1);
        assertEquals(road.deck(nearest(road, track.x(0), track.z(0))), track.deck(0));
        assertConnected(world, track.x(track.stationCount() / 2), track.z(track.stationCount() / 2),
                road.x(nearest(road, -200, 0)), road.z(nearest(road, -200, 0)));
        assertSmooth(world, 85, -10, 115, 20, 50, 120);
    }

    @Test
    void sharedCorridorDrawsOneRoad() {
        RoadWorld world = RoadWorld.flat(64);
        RoadSpec spec = RoadWorld.spec().markings(1, 4, 0).build();
        double angle = Math.toRadians(5);
        RoadIndex index = RoadWorld.index(world, paved(spec, 0, 1, 0, 0, 600, 0),
                paved(spec, 0, 2, 0, 0, (int) Math.round(Math.cos(angle) * 500),
                        (int) Math.round(Math.sin(angle) * 500)));
        world.rasterize(index);
        for (int x = 20; x <= 80; x += 10) {
            int paved = 0;
            int markingRuns = 0;
            boolean inMarking = false;
            for (int z = -20; z <= 20; z++) {
                RoadRasterizer.Part part = world.part(x, 64, z);
                if (part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.MARKING) {
                    paved++;
                }
                boolean marking = part == RoadRasterizer.Part.MARKING;
                if (marking && !inMarking) {
                    markingRuns++;
                }
                inMarking = marking;
            }
            assertEquals(spec.width(), paved, "one carriageway at x=" + x);
            assertEquals(1, markingRuns, "one centre line at x=" + x);
        }
        assertNoHoles(world, index);
        assertConnected(world, index.link(1).x(index.link(1).stationCount() - 1),
                index.link(1).z(index.link(1).stationCount() - 1), 590, 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 6})
    void overlappingParallelRoadsCollapse(int offset) {
        RoadWorld world = RoadWorld.flat(64);
        RoadSpec spec = RoadWorld.spec().markings(1, 4, 0).build();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -300, 0, 300, 0),
                paved(spec, -1, -1, -250, offset, 250, offset));
        assertEquals(1, index.linkCount());
        world.rasterize(index);
        for (int x = -200; x <= 200; x += 25) {
            int markings = 0;
            for (int z = -20; z <= 20; z++) {
                if (world.part(x, 64, z) == RoadRasterizer.Part.MARKING) {
                    markings++;
                }
            }
            assertEquals(1, markings);
        }
    }

    @Test
    void canopyOverRoadIsSwept() {
        RoadWorld world = RoadWorld.flat(64);
        for (int x = -5; x <= 5; x++) {
            for (int z = -10; z <= 10; z++) {
                world.leaves(x, 72, z);
                world.leaves(x, 73, z);
            }
        }
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -100, 0, 100, 0));
        world.rasterize(index);
        for (int x = -5; x <= 5; x++) {
            for (int z = -10; z <= 10; z++) {
                if (Math.abs(z + 0.5) < spec.clearedHalfWidth() - 1) {
                    assertTrue(world.air(x, 72, z) && world.air(x, 73, z), "canopy left at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void stalkAboveClearedBaseIsRemoved() {
        RoadWorld world = RoadWorld.flat(64);
        RoadSpec spec = RoadWorld.road();
        int beside = (int) Math.ceil(spec.clearedHalfWidth()) + 2;
        world.stalk(0, 0, 30);
        world.stalk(0, beside, 30);
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -100, 0, 100, 0));
        world.rasterize(index);
        for (int y = 65; y <= 94; y++) {
            assertTrue(world.air(0, y, 0), "floating stalk at y=" + y);
            assertEquals(RoadWorld.STALK, world.get(0, y, beside), "stalk off the road cut at y=" + y);
        }
    }

    @Test
    void lateCanopyIsSwept() {
        RoadWorld world = RoadWorld.flat(64);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -100, 0, 100, 0));
        List<long[]> chunks = RoadWorld.chunks(index, 32);
        world.rasterize(index, chunks);
        // Neighbour decoration after the road: canopy over the deck, a vine tip at deck + 1.
        int above = 64 + 1 + spec.clearance() + RoadRasterizer.CANOPY_REACH;
        for (int z = -3; z <= 3; z++) {
            world.leaves(0, 72, z);
            world.leaves(0, above + 1, z);
        }
        world.leaves(5, 65, 0);
        world.sweep(index, chunks);
        for (int z = -3; z <= 3; z++) {
            assertTrue(world.air(0, 72, z), "late canopy left at z=" + z);
            assertEquals(RoadWorld.LEAVES, world.get(0, above + 1, z), "swept above reach at z=" + z);
            assertTrue(RoadWorld.isDeck(world.part(0, 64, z)), "deck lost at z=" + z);
        }
        assertTrue(world.air(5, 65, 0));
    }

    @Test
    void setbackReachesClearedBandPlusReach() {
        RoadWorld world = RoadWorld.flat(64);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = RoadWorld.index(world, paved(spec, -1, -1, -100, 0, 100, 0));
        double reach = 5.0;
        int inside = (int) Math.floor(spec.clearedHalfWidth() + reach - 1.5);
        int outside = (int) Math.ceil(spec.clearedHalfWidth() + reach + 0.5);
        assertTrue(index.near(0, inside, reach, 65, 97));
        assertTrue(index.near(0, -inside, reach, 65, 97));
        assertFalse(index.near(0, outside, reach, 65, 97));
        assertFalse(index.near(0, inside, reach, 64 + spec.clearance() + 1, 120), "above headroom");
        assertFalse(index.near(0, inside, reach, 0, 64 - spec.baseDepth() - 2), "below foundation");
    }

    @Test
    void setbackIgnoresTunnels() {
        RoadWorld world = new RoadWorld((x, z) -> Math.abs(x) < 150 ? 130 : 64, RoadWorld.MIN_Y);
        RoadIndex index = RoadWorld.index(world, paved(RoadWorld.road(), -1, -1, -400, 0, 400, 0));
        RoadIndex.LinkGeometry bore = index.link(0);
        int s = nearest(bore, 100, 0);
        assertEquals(RoadProfile.TUNNEL, bore.mode(s));
        assertFalse(index.near(100, 0, 5.0, bore.deck(s) - 8, 162));
        assertTrue(index.near(200, 0, 5.0, 0, 200));
    }

    // ---------------------------------------------------------------------------------------------------------

    static int nearest(RoadIndex.LinkGeometry link, double x, double z) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int s = 0; s < link.stationCount(); s++) {
            double d = (link.x(s) - x) * (link.x(s) - x) + (link.z(s) - z) * (link.z(s) - z);
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
    }

    /** Flood over deck blocks (one step up/down allowed) from one point to another. */
    static void assertConnected(RoadWorld world, double fromX, double fromZ, double toX, double toZ) {
        int sx = (int) Math.floor(fromX);
        int sz = (int) Math.floor(fromZ);
        int tx = (int) Math.floor(toX);
        int tz = (int) Math.floor(toZ);
        IntArrayList start = world.decks(sx, sz, RoadWorld.MIN_Y + 1, RoadWorld.MAX_Y - 2);
        assertTrue(!start.isEmpty(), "no deck at start " + sx + "," + sz);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long first = RoadWorld.key(sx, start.getInt(0), sz);
        queue.enqueue(first);
        seen.add(first);
        int limit = 400_000;
        while (!queue.isEmpty() && limit-- > 0) {
            long k = queue.dequeueLong();
            int x = (int) (k >> 38);
            x = (x << 6) >> 6;
            int z = (int) ((k >> 12) & 0x3FFFFFF);
            z = (z << 6) >> 6;
            int y = (int) (k & 0xFFF) + RoadWorld.MIN_Y;
            if (x == tx && z == tz) {
                return;
            }
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = x + d[0];
                    int nz = z + d[1];
                    int ny = y + dy;
                    RoadRasterizer.Part part = world.part(nx, ny, nz);
                    if (part == null || !RoadWorld.isDeck(part) || RoadWorld.isDeck(world.part(nx, ny + 1, nz))) {
                        continue;
                    }
                    long nk = RoadWorld.key(nx, ny, nz);
                    if (seen.add(nk)) {
                        queue.enqueue(nk);
                    }
                }
            }
        }
        fail("no drivable path from " + sx + "," + sz + " to " + tx + "," + tz);
    }

    /** A railing connecting to a side has a railing there connecting back. */
    static void assertRailingsSymmetric(RoadWorld world) {
        assertNotNull(world.railings);
        world.railings.long2IntEntrySet().forEach(entry -> {
            long k = entry.getLongKey();
            int x = ((int) (k >> 38) << 6) >> 6;
            int z = ((int) ((k >> 12) & 0x3FFFFFF) << 6) >> 6;
            int y = (int) (k & 0xFFF) + RoadWorld.MIN_Y;
            int c = entry.getIntValue();
            int[][] sides = {{0, -1, 1, 4}, {1, 0, 2, 8}, {0, 1, 4, 1}, {-1, 0, 8, 2}};
            for (int[] side : sides) {
                long nk = RoadWorld.key(x + side[0], y, z + side[1]);
                boolean there = world.railings.containsKey(nk);
                assertEquals(there, (c & side[2]) != 0, "railing connection at " + x + "," + y + "," + z);
                if (there) {
                    assertTrue((world.railings.get(nk) & side[3]) != 0, "one-way railing at " + x + "," + z);
                }
            }
        });
    }
}
