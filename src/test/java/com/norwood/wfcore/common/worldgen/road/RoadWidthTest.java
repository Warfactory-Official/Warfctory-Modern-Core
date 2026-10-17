package com.norwood.wfcore.common.worldgen.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Cross-section width: exact on axis-aligned roads at any phase, geometric elsewhere, uniform through curves. */
class RoadWidthTest {

    private static final int Y = 64;

    static Stream<Arguments> widths() {
        List<Arguments> out = new ArrayList<>();
        for (int width = 1; width <= 13; width++) {
            for (int shoulder = 0; shoulder <= 3; shoulder++) {
                out.add(Arguments.of(width, shoulder));
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("widths")
    void axisAlignedWidthIsExactOnBlockEdgeCentreline(int width, int shoulder) {
        assertAxisWidth(width, shoulder, 0);
    }

    @ParameterizedTest
    @MethodSource("widths")
    void axisAlignedWidthIsExactOnBlockCentreCentreline(int width, int shoulder) {
        assertAxisWidth(width, shoulder, 1);
    }

    private static void assertAxisWidth(int width, int shoulder, int phase) {
        RoadWorld world = RoadWorld.flat(Y);
        RoadSpec spec = RoadWorld.spec().width(width).shoulder(shoulder).build();
        RoadIndex index = phase == 0
                ? RoadWorld.index(world, RoadWorld.source(spec, RoadWorld.link("paved", -1, -1, -100, 0, 100, 0)))
                : RoadIndex.build(world, List.of(halfPhase(spec)), List.of(), 1L, null);
        world.rasterize(index);
        for (int x = -80; x <= 80; x++) {
            int paved = 0;
            int edge = 0;
            for (int z = -20; z <= 20; z++) {
                RoadRasterizer.Part part = world.part(x, Y, z);
                if (part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.MARKING) {
                    paved++;
                } else if (part == RoadRasterizer.Part.EDGE) {
                    edge++;
                }
            }
            assertEquals(width, paved, "paved columns at x=" + x);
            assertEquals(2 * shoulder, edge, "shoulder columns at x=" + x);
        }
    }

    /** Straight road along x with its centreline at z = 0.5 (block centre). */
    private static RoadIndex.Source halfPhase(RoadSpec spec) {
        int n = 201;
        double[] xs = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = -100 + i;
            zs[i] = 0.5;
        }
        return new RoadIndex.Source(0, -1, -1, spec, 0, xs, zs);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 15, 22, 30, 37, 45, 52, 60, 67, 75, 82, 89})
    void anyAngleRasterMatchesTrueDistance(int degrees) {
        double angle = Math.toRadians(degrees);
        int dx = (int) Math.round(Math.cos(angle) * 150);
        int dz = (int) Math.round(Math.sin(angle) * 150);
        RoadWorld world = RoadWorld.flat(Y);
        RoadSpec spec = RoadWorld.spec().width(7).shoulder(1).build();
        RoadIndex index = RoadWorld.index(world,
                RoadWorld.source(spec, RoadWorld.link("paved", -1, -1, -dx, -dz, dx, dz)));
        world.rasterize(index);
        assertBandsMatchGeometry(world, index.link(0), spec, 20);
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 18, 25, 40, 64, 100, 160, 250, 400})
    void circularArcHasUniformWidth(int radius) {
        RoadWorld world = RoadWorld.flat(Y);
        RoadSpec spec = RoadWorld.spec().width(9).shoulder(1).build();
        RoadIndex index = RoadIndex.build(world, List.of(arc(spec, radius, 0.0, Math.PI * 1.5)), List.of(), 1L,
                null);
        world.rasterize(index);
        RoadIndex.LinkGeometry link = index.link(0);
        assertBandsMatchGeometry(world, link, spec, 12);

        // Annulus area over an angle == width x centreline arc length, inner and outer side alike.
        int paved = 0;
        int window = 0;
        for (int x = -radius - 20; x <= radius + 20; x++) {
            for (int z = -radius - 20; z <= radius + 20; z++) {
                double r = Math.sqrt((x + 0.5) * (x + 0.5) + (z + 0.5) * (z + 0.5));
                double theta = Math.atan2(z + 0.5, x + 0.5);
                if (theta < 0) {
                    theta += Math.PI * 2;
                }
                if (theta < 0.2 || theta > Math.PI * 1.5 - 0.2 || Math.abs(r - radius) > 10) {
                    continue;
                }
                window++;
                RoadRasterizer.Part part = world.part(x, Y, z);
                if (part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.MARKING) {
                    paved++;
                }
            }
        }
        double length = radius * (Math.PI * 1.5 - 0.4);
        double perBlock = paved / length;
        assertTrue(window > 0);
        assertEquals(9.0, perBlock, 0.25, "paved area per block of centreline, r=" + radius);
    }

    static RoadIndex.Source arc(RoadSpec spec, double radius, double from, double to) {
        int n = (int) Math.ceil(radius * (to - from)) + 1;
        double[] xs = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            double t = from + (to - from) * i / (n - 1);
            xs[i] = Math.cos(t) * radius;
            zs[i] = Math.sin(t) * radius;
        }
        return new RoadIndex.Source(0, -1, -1, spec, 0, xs, zs);
    }

    /**
     * Columns clearly inside the paved band (true distance < h - eps) are paved; clearly outside the outer band
     * are untouched at deck level. Skips {@code skip} stations at each end (round caps).
     */
    static void assertBandsMatchGeometry(RoadWorld world, RoadIndex.LinkGeometry link, RoadSpec spec, int skip) {
        double paved = spec.pavedHalfWidth();
        double outer = spec.outerHalfWidth();
        int checked = 0;
        for (int s = skip; s < link.stationCount() - skip; s++) {
            int cx = (int) Math.floor(link.x(s));
            int cz = (int) Math.floor(link.z(s));
            for (int x = cx - 12; x <= cx + 12; x++) {
                for (int z = cz - 12; z <= cz + 12; z++) {
                    double d = distance(link, x + 0.5, z + 0.5, skip);
                    if (Double.isNaN(d)) {
                        continue;
                    }
                    RoadRasterizer.Part part = world.part(x, Y, z);
                    boolean isPaved = part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.MARKING;
                    if (d < paved - 1e-6 && !isPaved) {
                        fail("column " + x + "," + z + " at distance " + d + " not paved: " + part);
                    }
                    if (d > paved + 1e-6 && isPaved) {
                        fail("column " + x + "," + z + " at distance " + d + " paved past half width " + paved);
                    }
                    if (d > outer + 1e-6 && part != null && RoadWorld.isDeck(part)) {
                        fail("column " + x + "," + z + " at distance " + d + " has deck past outer " + outer);
                    }
                    checked++;
                }
            }
        }
        assertTrue(checked > 0);
    }

    /** True distance to the polyline, NaN when the nearest point is within {@code skip} of an end. */
    static double distance(RoadIndex.LinkGeometry link, double px, double pz, int skip) {
        double best = Double.MAX_VALUE;
        int bestSegment = -1;
        for (int i = 0; i + 1 < link.stationCount(); i++) {
            double ax = link.x(i);
            double az = link.z(i);
            double sx = link.x(i + 1) - ax;
            double sz = link.z(i + 1) - az;
            double l2 = sx * sx + sz * sz;
            double t = l2 == 0 ? 0 : Math.clamp(((px - ax) * sx + (pz - az) * sz) / l2, 0, 1);
            double ox = px - (ax + sx * t);
            double oz = pz - (az + sz * t);
            double d = ox * ox + oz * oz;
            if (d < best) {
                best = d;
                bestSegment = i;
            }
        }
        if (bestSegment < skip || bestSegment >= link.stationCount() - 1 - skip) {
            return Double.NaN;
        }
        return Math.sqrt(best);
    }
}
