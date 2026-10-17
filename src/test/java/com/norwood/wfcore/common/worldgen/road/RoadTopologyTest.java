package com.norwood.wfcore.common.worldgen.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

class RoadTopologyTest {

    private static List<RoadTopology.Piece> topology(RoadIndex.Source... ranked) {
        return RoadTopology.build(List.of(ranked));
    }

    private static RoadIndex.Source src(RoadSpec spec, int network, int from, int to, int... xz) {
        return RoadIndex.Source.of(network, RoadWorld.link("paved", from, to, xz), spec, 0);
    }

    private static double distanceTo(RoadTopology.Piece piece, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < piece.stationCount(); i++) {
            double ax = piece.xs[i];
            double az = piece.zs[i];
            double sx = piece.xs[i + 1] - ax;
            double sz = piece.zs[i + 1] - az;
            double l2 = sx * sx + sz * sz;
            double t = l2 == 0 ? 0 : Math.clamp(((x - ax) * sx + (z - az) * sz) / l2, 0, 1);
            double dx = x - (ax + sx * t);
            double dz = z - (az + sz * t);
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    @Test
    void exactDuplicateIsDropped() {
        RoadSpec spec = RoadWorld.road();
        List<RoadTopology.Piece> pieces = topology(src(spec, 0, 0, 1, 0, 0, 500, 0),
                src(spec, 0, 0, 1, 0, 0, 500, 0));
        assertEquals(1, pieces.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5, 8, 10})
    void parallelOverlapIsDropped(int offset) {
        RoadSpec spec = RoadWorld.road();
        List<RoadTopology.Piece> pieces = topology(src(spec, 0, -1, -1, 0, 0, 500, 0),
                src(spec, 0, -1, -1, 0, offset, 500, offset));
        assertEquals(1, pieces.size(), "offset " + offset);
    }

    @Test
    void separateParallelRoadsBothKept() {
        RoadSpec spec = RoadWorld.road();
        double touch = 2 * spec.outerHalfWidth() + RoadTopology.SHADOW_GAP;
        int offset = (int) Math.ceil(touch) + 1;
        List<RoadTopology.Piece> pieces = topology(src(spec, 0, -1, -1, 0, 0, 500, 0),
                src(spec, 0, -1, -1, 0, offset, 500, offset));
        assertEquals(2, pieces.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 6, 10, 15})
    void shallowFanFromSharedJunctionIsTrimmedAndAttached(int degrees) {
        RoadSpec spec = RoadWorld.road();
        double angle = Math.toRadians(degrees);
        int ex = (int) Math.round(Math.cos(angle) * 600);
        int ez = (int) Math.round(Math.sin(angle) * 600);
        List<RoadTopology.Piece> pieces = topology(src(spec, 0, 0, 1, 0, 0, 600, 0),
                src(spec, 0, 0, 2, 0, 0, ex, ez));
        assertEquals(2, pieces.size());
        RoadTopology.Piece lesser = pieces.get(1);
        assertEquals(-1, lesser.startJunction, "start no longer at the shared node");
        assertEquals(0, lesser.startAttach);
        assertTrue(distanceTo(pieces.get(0), lesser.xs[0], lesser.zs[0]) <= 1.0 + 1e-6,
                "new start lies on the kept centreline");
        int overlapping = 0;
        double touch = 2 * spec.outerHalfWidth() + RoadTopology.SHADOW_GAP;
        for (int s = 0; s < lesser.stationCount(); s++) {
            if (distanceTo(pieces.get(0), lesser.xs[s], lesser.zs[s]) < touch) {
                overlapping++;
            }
        }
        assertTrue(overlapping < RoadTopology.LONG_END * touch + 2, "residual overlap " + overlapping);
        // Slip road: leaves the kept road at ~30 deg, not a right-angle stub.
        double join = Math.toDegrees(Math.atan2(Math.abs(lesser.zs[5] - lesser.zs[0]), lesser.xs[5] - lesser.xs[0]));
        assertTrue(join <= 45.0, "join angle " + join);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 45, 60, 90, 135})
    void wideJunctionAngleKeepsJunction(int degrees) {
        RoadSpec spec = RoadWorld.road();
        double angle = Math.toRadians(degrees);
        int ex = (int) Math.round(Math.cos(angle) * 400);
        int ez = (int) Math.round(Math.sin(angle) * 400);
        List<RoadTopology.Piece> pieces = topology(src(spec, 0, 0, 1, 0, 0, 600, 0),
                src(spec, 0, 0, 2, 0, 0, ex, ez));
        assertEquals(2, pieces.size());
        assertEquals(RoadTopology.junctionKey(0, 0), pieces.get(1).startJunction);
        assertEquals(-1, pieces.get(1).startAttach);
        assertEquals(0, pieces.get(1).crossings.size());
    }

    @Test
    void interiorSharedStretchSplitsLesserLink() {
        RoadSpec spec = RoadWorld.road();
        List<RoadTopology.Piece> pieces = topology(src(spec, 0, -1, -1, -500, 0, 500, 0),
                src(spec, 0, -1, -1, -300, -300, -150, 0, 150, 0, 300, 300));
        assertEquals(3, pieces.size());
        RoadTopology.Piece first = pieces.get(1);
        RoadTopology.Piece second = pieces.get(2);
        assertEquals(0, first.endAttach);
        assertEquals(0, second.startAttach);
        assertTrue(distanceTo(pieces.get(0), first.xs[first.stationCount() - 1],
                first.zs[first.stationCount() - 1]) <= 1.0 + 1e-6);
        assertTrue(distanceTo(pieces.get(0), second.xs[0], second.zs[0]) <= 1.0 + 1e-6);
    }

    @ParameterizedTest
    @ValueSource(ints = {25, 45, 70, 90})
    void crossingIsRecordedNotTrimmed(int degrees) {
        RoadSpec spec = RoadWorld.road();
        double angle = Math.toRadians(degrees);
        int ex = (int) Math.round(Math.cos(angle) * 300);
        int ez = (int) Math.round(Math.sin(angle) * 300);
        List<RoadTopology.Piece> pieces = topology(src(spec, 0, -1, -1, -400, 0, 400, 0),
                src(spec, 0, -1, -1, -ex, -ez, ex, ez));
        assertEquals(2, pieces.size());
        RoadTopology.Piece lesser = pieces.get(1);
        assertEquals(3, lesser.crossings.size());
        int station = lesser.crossings.getInt(0);
        assertTrue(Math.abs(lesser.xs[station]) < 1.5 && Math.abs(lesser.zs[station]) < 1.5,
                "crossing station at the intersection");
        assertEquals(0, lesser.crossings.getInt(1));
    }

    @Test
    void trackStartingOnRoadAttaches() {
        List<RoadTopology.Piece> pieces = topology(src(RoadWorld.road(), 0, 0, 1, -400, 0, 400, 0),
                src(RoadWorld.track(), 0, -1, -1, 100, 0, 100, 300));
        assertEquals(2, pieces.size());
        RoadTopology.Piece track = pieces.get(1);
        assertEquals(0, track.startAttach);
        assertEquals(100, pieces.get(0).xs[track.startAttachAt], 1.0);
    }

    @Test
    void trackEndingShortOfRoadGetsConnector() {
        RoadSpec road = RoadWorld.road();
        RoadSpec track = RoadWorld.track();
        int gap = (int) Math.floor(road.outerHalfWidth() + track.outerHalfWidth());
        List<RoadTopology.Piece> pieces = topology(src(road, 0, 0, 1, -400, 0, 400, 0),
                src(track, 0, -1, -1, 100, 300, 100, gap));
        RoadTopology.Piece t = pieces.get(1);
        assertEquals(0, t.endAttach);
        double end = distanceTo(pieces.get(0), t.xs[t.stationCount() - 1], t.zs[t.stationCount() - 1]);
        assertTrue(end <= Math.max(1.0, road.outerHalfWidth() - track.outerHalfWidth()) + 1e-6,
                "track end reaches the road surface: " + end);
        for (int s = 1; s < t.stationCount(); s++) {
            double step = Math.hypot(t.xs[s] - t.xs[s - 1], t.zs[s] - t.zs[s - 1]);
            assertTrue(step <= 1.3, "station spacing " + step + " at " + s);
        }
    }

    @Test
    void shortConnectorAcrossHighwayIsAbsorbed() {
        RoadSpec highway = RoadWorld.highway();
        RoadSpec road = RoadWorld.road();
        List<RoadTopology.Piece> pieces = topology(src(highway, 0, -1, -1, -500, 0, 500, 0),
                src(road, 0, 5, 6, 0, -8, 0, 8), src(road, 0, 5, 7, 0, -8, 0, -400),
                src(road, 0, 6, 8, 0, 8, 0, 400));
        assertEquals(3, pieces.size(), "connector inside the highway corridor dropped");
        for (int i = 1; i < 3; i++) {
            RoadTopology.Piece piece = pieces.get(i);
            assertTrue(piece.startAttach == 0 || piece.endAttach == 0, "spur " + i + " attaches to the highway");
        }
    }

    @Test
    void rankDecidesSurvivor() {
        RoadSpec low = RoadWorld.track();
        RoadSpec high = RoadWorld.highway();
        List<RoadIndex.Source> sources = new ArrayList<>(List.of(src(high, 0, -1, -1, 0, 2, 300, 2),
                src(low, 0, -1, -1, -200, 0, 500, 0)));
        List<RoadTopology.Piece> pieces = RoadTopology.build(sources);
        assertEquals(high, pieces.get(0).origin.spec());
        for (RoadTopology.Piece piece : pieces.subList(1, pieces.size())) {
            for (int s = 0; s < piece.stationCount(); s++) {
                assertTrue(piece.xs[s] < 10 || piece.xs[s] > 290
                        || distanceTo(pieces.get(0), piece.xs[s], piece.zs[s]) <= 4.5, "track left on highway");
            }
        }
    }
}
