package com.norwood.wfcore.common.worldgen.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Junction pads, conforming T / crossings, grade separation by type, side slopes, structures, furniture. */
class RoadNodeTest {

    private static final RoadSpec ROAD = RoadWorld.spec().markings(1, 5, 5).furniture(0, 4, 3, true).build();
    private static final RoadSpec HIGHWAY = RoadWorld.spec().priority(30).width(11).shoulder(2).clearance(6)
            .markings(1, 6, 6).grade(0.07).margin(14).tunnel(7, 10).tunnelMinLength(48).piers(16, 3)
            .bridge(7, 6, 96).gradeSeparated().corner(6).furniture(48, 5, 3, true).build();

    private static RoadWorld rolling() {
        return new RoadWorld((x, z) -> (int) Math.round(72 + 7 * RoadFuzzTest.noise(3, x / 120.0, z / 120.0)
                + 2 * RoadFuzzTest.noise(4, x / 35.0, z / 35.0)), 60);
    }

    private static RoadIndex.Source link(RoadSpec spec, int from, int to, int... xz) {
        return RoadWorld.source(spec, RoadWorld.link("paved", from, to, xz));
    }

    private static List<RoadIndex.Source> star(RoadSpec spec, double... degrees) {
        List<RoadIndex.Source> sources = new ArrayList<>();
        for (int a = 0; a < degrees.length; a++) {
            double angle = Math.toRadians(degrees[a]);
            sources.add(link(spec, 0, a + 1, 0, 0, (int) Math.round(Math.cos(angle) * 260),
                    (int) Math.round(Math.sin(angle) * 260)));
        }
        return sources;
    }

    private static RoadIndex build(RoadWorld world, List<RoadIndex.Source> sources) {
        RoadIndex index = RoadIndex.build(world, sources, List.of(), 1L, null);
        world.rasterize(index);
        return index;
    }

    private static int nearest(RoadIndex.LinkGeometry link, double x, double z) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int s = 0; s < link.stationCount(); s++) {
            double d = Math.hypot(link.x(s) - x, link.z(s) - z);
            if (d < bestDistance) {
                bestDistance = d;
                best = s;
            }
        }
        return best;
    }

    /** Adjacent single-deck columns differ by at most one block inside the box. */
    private static void assertSmooth(RoadWorld world, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                IntArrayList here = world.decks(x, z, 30, 130);
                if (here.size() != 1) {
                    continue;
                }
                for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
                    IntArrayList next = world.decks(x + d[0], z + d[1], 30, 130);
                    if (next.size() == 1) {
                        assertTrue(Math.abs(next.getInt(0) - here.getInt(0)) <= 1,
                                "step " + here.getInt(0) + " -> " + next.getInt(0) + " at " + x + "," + z);
                    }
                }
            }
        }
    }

    private static int count(RoadWorld world, RoadRasterizer.Part part, int minX, int minZ, int maxX, int maxZ) {
        int found = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = 30; y <= 130; y++) {
                    if (world.part(x, y, z) == part) {
                        found++;
                    }
                }
            }
        }
        return found;
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void junctionPadIsLevelOnRollingGround(int arms) {
        double[] degrees = new double[arms];
        for (int a = 0; a < arms; a++) {
            degrees[a] = 360.0 * a / arms + 7;
        }
        RoadWorld world = rolling();
        RoadIndex index = build(world, star(ROAD, degrees));
        assertEquals(1, index.nodes().size());
        RoadNodes.Node node = index.nodes().get(0);
        assertTrue(node.flat);
        int height = index.link(0).deck(0);
        int throat = Integer.MAX_VALUE;
        for (RoadNodes.Arm arm : node.arms) {
            assertEquals(height, arm.link.deck(arm.station + arm.dir * arm.throat), "pad edge height");
            throat = Math.min(throat, arm.throat);
        }
        int decks = 0;
        for (int x = -throat; x <= throat; x++) {
            for (int z = -throat; z <= throat; z++) {
                if (x * x + z * z > throat * throat) {
                    continue;
                }
                IntArrayList here = world.decks(x, z, 30, 130);
                if (!here.isEmpty()) {
                    assertEquals(1, here.size());
                    assertEquals(height, here.getInt(0), "pad deck at " + x + "," + z);
                    decks++;
                }
            }
        }
        assertTrue(decks > 100, "pad columns " + decks);
        assertSmooth(world, -90, -90, 90, 90);
    }

    @Test
    void cornersAreFilletedAndKerbFollowsThem() {
        RoadWorld world = RoadWorld.flat(64);
        RoadIndex index = build(world, star(ROAD, 0, 90, 180, 270));
        RoadNodes.Node node = index.nodes().get(0);
        assertEquals(4, node.corners.length);
        double outer = ROAD.outerHalfWidth();
        int filled = 0;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                // Half-open bands: which boundary column belongs depends on the link's direction.
                if (Math.abs(x + 0.5) == outer || Math.abs(z + 0.5) == outer) {
                    continue;
                }
                boolean inBand = Math.abs(x + 0.5) < outer || Math.abs(z + 0.5) < outer;
                RoadRasterizer.Part part = world.part(x, 64, z);
                if (!inBand && part != null && RoadWorld.isDeck(part)) {
                    filled++;
                    // Fillet: inside the corner square of side r, outside the circle of radius r.
                    double dx = Math.abs(x + 0.5) - outer - ROAD.cornerRadius();
                    double dz = Math.abs(z + 0.5) - outer - ROAD.cornerRadius();
                    assertTrue(dx < 0 && dz < 0 && Math.hypot(dx, dz) >= ROAD.cornerRadius() - 1e-6,
                            "fillet block outside its arc at " + x + "," + z);
                }
                if (Math.abs(x + 0.5) < outer - 1 && Math.abs(z + 0.5) < outer - 1) {
                    assertTrue(part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.MARKING,
                            "pad centre at " + x + "," + z + ": " + part);
                }
            }
        }
        // 4 x (r^2 - pi r^2 / 4), r = 5: ~21 columns, less the skipped boundary rows.
        assertTrue(filled >= 8 && filled <= 28, "fillet columns " + filled);
        // Shoulder strips of an arm inside a fillet are paved over.
        assertEquals(RoadRasterizer.Part.SURFACE, world.part((int) Math.floor(outer - 0.5), 64, (int) outer + 1));
    }

    @Test
    void stopLinesOnTheApproachSideAndNoMarkingsInsideThePad() {
        RoadWorld world = RoadWorld.flat(64);
        RoadIndex index = build(world, star(ROAD, 0, 90, 180, 270));
        RoadNodes.Node node = index.nodes().get(0);
        int stopping = 0;
        for (RoadNodes.Arm arm : node.arms) {
            RoadIndex.LinkGeometry link = arm.link;
            int stop = arm.station + arm.dir * arm.throat;
            // Unit outward direction and the side to its right seen by inbound traffic (north when heading west).
            double ox = Math.signum(link.x(stop) - node.x);
            double oz = Math.signum(link.z(stop) - node.z);
            if (Math.abs(link.x(stop) - node.x) < 1) {
                ox = 0;
            } else {
                oz = 0;
            }
            int lineBlocks = 0;
            for (int k = 1; k <= 3; k++) {
                int approachX = (int) Math.floor(link.x(stop) + oz * (k - 0.5));
                int approachZ = (int) Math.floor(link.z(stop) - ox * (k - 0.5));
                int farX = (int) Math.floor(link.x(stop) - oz * (k - 0.5));
                int farZ = (int) Math.floor(link.z(stop) + ox * (k - 0.5));
                // Station on a block edge: the line lands on either column along the arm.
                if (world.part(approachX, 64, approachZ) == RoadRasterizer.Part.MARKING
                        || world.part(approachX - (int) Math.abs(ox), 64, approachZ - (int) Math.abs(oz))
                                == RoadRasterizer.Part.MARKING) {
                    lineBlocks++;
                }
                assertEquals(RoadRasterizer.Part.SURFACE, world.part(farX, 64, farZ),
                        "far side of the stop line at " + farX + "," + farZ);
            }
            // A through arm shows at most its own centre line dash here.
            assertTrue(arm.major ? lineBlocks <= 1 : lineBlocks == 3,
                    "stop line blocks " + lineBlocks + ", arm at " + Math.toDegrees(arm.angle));
            stopping += arm.major ? 0 : 1;
            // Centre line inside the pad: kept only by the through arms.
            boolean marked = false;
            for (int t = 1; t < arm.throat - 1; t++) {
                int s = arm.station + arm.dir * t;
                // Centreline on a block edge: the half-open marking band picks either neighbour.
                for (int dx = -1; dx <= 0; dx++) {
                    for (int dz = -1; dz <= 0; dz++) {
                        marked |= world.part((int) Math.floor(link.x(s)) + dx, 64, (int) Math.floor(link.z(s)) + dz)
                                == RoadRasterizer.Part.MARKING;
                    }
                }
            }
            assertEquals(arm.major, marked, "centre line inside the pad, arm at " + Math.toDegrees(arm.angle));
        }
        assertEquals(2, stopping, "one straight pair runs through");
        assertEquals(2, count(world, RoadRasterizer.Part.SIGN, -40, -40, 40, 40));
        assertEquals(2, count(world, RoadRasterizer.Part.LAMP_LIGHT, -40, -40, 40, 40));
    }

    @Test
    void teeConformsToASlopingMajorRoad() {
        RoadWorld world = new RoadWorld((x, z) -> (int) Math.round(70 + 0.06 * x + 0.02 * z), RoadWorld.MIN_Y);
        RoadIndex index = build(world, List.of(link(ROAD, 0, 1, -300, 0, 300, 0),
                RoadWorld.source(RoadWorld.track(), RoadWorld.link("track", -1, -1, 40, 0, 90, 220))));
        RoadIndex.LinkGeometry road = index.link(0);
        RoadIndex.LinkGeometry track = index.link(1);
        assertTrue(Math.abs(road.deck(nearest(road, 200, 0)) - road.deck(nearest(road, -200, 0))) >= 15,
                "major road keeps climbing through the T");
        for (int s = 0; s < track.stationCount() && Math.abs(track.z(s)) < ROAD.outerHalfWidth(); s++) {
            assertEquals(road.deck(nearest(road, track.x(s), track.z(s))), track.deck(s), "track station " + s);
        }
        assertSmooth(world, 0, -20, 110, 80);
        assertEquals(0, count(world, RoadRasterizer.Part.SIGN, 0, -20, 110, 80), "track has no furniture");
    }

    @Test
    void roadsCrossAtGradeHighwaysAreBridged() {
        RoadWorld world = rolling();
        RoadIndex index = build(world, List.of(link(ROAD, 0, 1, -300, 0, 300, 30),
                link(ROAD, 2, 3, -100, -250, -60, 250)));
        RoadIndex.LinkGeometry major = index.link(0);
        RoadIndex.LinkGeometry minor = index.link(1);
        RoadNodes.Node node = index.nodes().get(0);
        assertTrue(index.active(node));
        int at = nearest(minor, node.x, node.z);
        assertEquals(major.deck(nearest(major, node.x, node.z)), minor.deck(at));
        assertSmooth(world, (int) node.x - 40, (int) node.z - 40, (int) node.x + 40, (int) node.z + 40);
        assertEquals(2, count(world, RoadRasterizer.Part.SIGN, (int) node.x - 40, (int) node.z - 40,
                (int) node.x + 40, (int) node.z + 40));

        world = rolling();
        index = build(world, List.of(link(HIGHWAY, 0, 1, -300, 0, 300, 30),
                link(ROAD, 2, 3, -100, -250, -60, 250)));
        RoadIndex.LinkGeometry highway = index.link(0);
        minor = index.link(1);
        node = index.nodes().get(0);
        assertFalse(index.active(node));
        at = nearest(minor, node.x, node.z);
        int gap = minor.deck(at) - highway.deck(nearest(highway, node.x, node.z));
        assertTrue(gap >= HIGHWAY.clearance() + 2 || -gap >= ROAD.tunnelHeight() + 3, "separation " + gap);
        if (gap > 0) {
            assertEquals(RoadProfile.BRIDGE, minor.mode(at));
            // Level over the highway: the structure layer fits under the whole span.
            for (int s = at - 8; s <= at + 8; s++) {
                assertEquals(minor.deck(at), minor.deck(s), "overpass deck at " + s);
            }
        }
        assertEquals(0, count(world, RoadRasterizer.Part.SIGN, (int) node.x - 40, (int) node.z - 40,
                (int) node.x + 40, (int) node.z + 40), "no stop lines on a flyover");
    }

    @Test
    void levelGroundBesideARoadIsUntouched() {
        RoadWorld world = RoadWorld.flat(64);
        RoadIndex index = build(world, List.of(link(ROAD, 0, 1, -200, 0, 200, 0)));
        assertEquals(64, index.link(0).deck(50));
        for (int x = -150; x <= 150; x++) {
            for (int z = -30; z <= 30; z++) {
                if (Math.abs(z + 0.5) > ROAD.outerHalfWidth()) {
                    assertEquals(RoadWorld.STONE, world.get(x, 64, z), "ground at " + x + "," + z);
                    assertTrue(world.air(x, 65, z));
                }
            }
        }
    }

    @Test
    void hillsideGetsBatteredTurfedSlopes() {
        RoadWorld world = new RoadWorld((x, z) -> (int) Math.round(80 + 0.4 * z), RoadWorld.MIN_Y);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = build(world, List.of(link(spec, 0, 1, -200, 0, 200, 0)));
        int deck = index.link(0).deck(200);
        int outer = (int) Math.ceil(spec.outerHalfWidth());
        // Downhill (z < 0): fill, one block down per 1 / batter out, turf on top.
        int turf = 0;
        for (int k = 0; k < spec.margin(); k++) {
            int z = -outer - 1 - k;
            int top = world.top(0, z);
            int line = deck - (int) Math.ceil((k + 0.5) * spec.batter());
            int natural = (int) Math.round(80 + 0.4 * z);
            assertEquals(Math.max(line, natural), top, "fill slope at z=" + z);
            if (line > natural) {
                assertEquals(RoadWorld.TURF, world.get(0, top, z));
                assertEquals(line - natural > 1 ? RoadWorld.PART_BASE + RoadRasterizer.Part.EMBANKMENT.ordinal()
                        : RoadWorld.STONE, world.get(0, top - 1, z));
                turf++;
            }
        }
        assertTrue(turf >= 3, "fill columns " + turf);
        // Uphill: cut, one block up per 1 / cut_batter out; nothing above the cut line.
        int cut = 0;
        for (int k = 0; k < spec.margin(); k++) {
            // Link runs +x: the half-open band [-4.5, 4.5) ends at z = 3.
            int z = outer - 1 + k;
            int line = deck + (int) Math.floor((k + 0.5) * spec.cutBatter());
            int natural = (int) Math.round(80 + 0.4 * z);
            assertEquals(Math.min(line, natural), world.top(0, z), "cut slope at z=" + z);
            cut += natural > line ? 1 : 0;
        }
        assertTrue(cut >= 2, "cut columns " + cut);
        assertEquals((int) Math.round(80 + 0.4 * 40), world.top(0, 40), "terrain past the margin");
    }

    @Test
    void guardRailsLineHighFillsAndLampsAlternate() {
        RoadWorld world = new RoadWorld((x, z) -> Math.abs(x) < 30 ? 64 : 70, RoadWorld.MIN_Y);
        RoadIndex index = build(world, List.of(link(HIGHWAY, 0, 1, -400, 0, 400, 0)));
        RoadIndex.LinkGeometry link = index.link(0);
        // Link runs +x: kerb columns of the half-open band [-7.5, 7.5) are z = -8 and z = 6.
        int outer = (int) Math.floor(HIGHWAY.outerHalfWidth()) - 1;
        int rails = 0;
        for (int s = 0; s < link.stationCount(); s++) {
            int x = (int) Math.floor(link.x(s));
            int deck = link.deck(s);
            boolean high = deck - link.ground(s) >= HIGHWAY.guardRailFill() && link.mode(s) == RoadProfile.GROUND;
            for (int z : new int[] {-outer - 2, outer}) {
                RoadRasterizer.Part part = world.part(x, deck + 1, z);
                boolean rail = part == RoadRasterizer.Part.GUARD_RAIL || part == RoadRasterizer.Part.LAMP_POST;
                if (Math.abs(link.x(s) - x - 0.5) < 0.25) {
                    assertEquals(high, rail, "kerb at station " + s + " z " + z + " fill "
                            + (deck - link.ground(s)) + ": " + part);
                }
                rails += rail ? 1 : 0;
            }
        }
        assertTrue(rails > 20, "rails " + rails);
        int north = 0;
        int south = 0;
        for (int x = -400; x <= 400; x++) {
            for (int y = 60; y <= 90; y++) {
                north += world.part(x, y, -outer - 2) == RoadRasterizer.Part.LAMP_LIGHT ? 1 : 0;
                south += world.part(x, y, outer) == RoadRasterizer.Part.LAMP_LIGHT ? 1 : 0;
            }
        }
        assertTrue(Math.abs(north - south) <= 1 && north + south >= 15 && north + south <= 17,
                "lamps " + north + " + " + south);
    }

    @Test
    void bridgeHasAbutmentsEvenPiersAndArches() {
        RoadWorld world = new RoadWorld((x, z) -> Math.abs(x) < 60 ? 45 : 75, 50);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = build(world, List.of(link(spec, 0, 1, -300, 0, 300, 0)));
        RoadIndex.LinkGeometry link = index.link(0);
        int middle = nearest(link, 0, 0);
        assertEquals(RoadProfile.BRIDGE, link.mode(middle));
        int first = middle - link.fromStart(middle);
        int last = middle + link.toEnd(middle);
        int deck = link.deck(first);
        for (int z = -4; z <= 3; z++) {
            int x = (int) Math.floor(link.x(first));
            for (int y = deck - 2; y > world.ground.applyAsInt(x, z); y--) {
                assertEquals(RoadRasterizer.Part.STRUCTURE, world.part(x, y, z), "abutment at " + x + "," + y + "," + z);
            }
        }
        IntArrayList piers = new IntArrayList();
        int thin = Integer.MAX_VALUE;
        int thick = 0;
        for (int s = first + RoadRasterizer.ABUTMENT; s <= last - RoadRasterizer.ABUTMENT; s++) {
            int x = (int) Math.floor(link.x(s));
            int d = link.deck(s);
            int depth = 0;
            while (world.part(x, d - 1 - depth, 0) == RoadRasterizer.Part.STRUCTURE) {
                depth++;
            }
            if (depth > 20) {
                if (piers.isEmpty() || s - piers.getInt(piers.size() - 1) > 2) {
                    piers.add(s);
                }
            } else {
                thin = Math.min(thin, depth);
                thick = Math.max(thick, depth);
            }
        }
        assertTrue(piers.size() >= 3, "piers " + piers);
        for (int k = 1; k < piers.size(); k++) {
            int gap = piers.getInt(k) - piers.getInt(k - 1);
            assertTrue(Math.abs(gap - spec.pierSpacing()) <= 2, "pier spacing " + piers);
        }
        assertEquals(1, thin, "soffit at the crown");
        assertTrue(thick >= 3, "arch depth beside a pier " + thick);
    }

    @Test
    void tunnelPortalHasHeadwallAndWingWalls() {
        RoadWorld world = new RoadWorld((x, z) -> x > 0 ? Math.min(140, 70 + x) : 70, RoadWorld.MIN_Y);
        RoadSpec spec = RoadWorld.road();
        RoadIndex index = build(world, List.of(link(spec, 0, 1, -300, 0, 500, 0)));
        RoadIndex.LinkGeometry link = index.link(0);
        int portal = 0;
        while (link.mode(portal) != RoadProfile.TUNNEL) {
            portal++;
        }
        int x = (int) Math.floor(link.x(portal));
        int deck = link.deck(portal);
        int ceiling = deck + spec.tunnelHeight() + 1;
        int outer = (int) Math.floor(spec.outerHalfWidth());
        // Link runs +x: the half-open band [-4.5, 4.5) covers z = -5 .. 3.
        for (int z : new int[] {-outer - 4, -outer - 2, outer, outer + 2}) {
            for (int y = deck; y <= ceiling + 2; y++) {
                assertEquals(RoadRasterizer.Part.WALL, world.part(x, y, z), "headwall at z " + z + " y " + y);
            }
        }
        assertEquals(RoadRasterizer.Part.WALL, world.part(x, ceiling + 2, 0), "parapet");
        int walled = 0;
        for (int k = 1; k <= RoadRasterizer.WING; k++) {
            int wx = (int) Math.floor(link.x(portal - k));
            int wd = link.deck(portal - k);
            for (int z : new int[] {-outer - 2, outer}) {
                if (world.part(wx, wd + 1, z) == RoadRasterizer.Part.WALL) {
                    walled++;
                }
            }
        }
        assertTrue(walled >= RoadRasterizer.WING, "wing wall columns " + walled);
    }

    @Test
    void furnishedNetworkIsIndependentOfChunkOrder() {
        List<RoadIndex.Source> sources = new ArrayList<>(star(ROAD, 10, 95, 190, 275));
        sources.add(link(HIGHWAY, 5, 6, -250, -180, 250, -120));
        sources.add(RoadWorld.source(RoadWorld.track(), RoadWorld.link("track", -1, -1, 120, 10, 200, 150)));
        RoadWorld forward = rolling();
        RoadIndex index = RoadIndex.build(forward, sources, List.of(), 1L, null);
        List<long[]> chunks = RoadWorld.chunks(index, 32);
        forward.rasterize(index, chunks);
        RoadWorld backward = rolling();
        RoadIndex again = RoadIndex.build(backward, sources, List.of(), 1L, null);
        List<long[]> reversed = new ArrayList<>(chunks);
        Collections.reverse(reversed);
        backward.rasterize(again, reversed);
        assertEquals(forward.snapshot(), backward.snapshot());
        assertNotNull(forward.snapshot());
        assertTrue(count(forward, RoadRasterizer.Part.LAMP_LIGHT, -260, -260, 260, 260) > 8);
    }
}
