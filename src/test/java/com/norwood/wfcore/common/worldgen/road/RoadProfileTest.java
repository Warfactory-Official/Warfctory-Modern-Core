package com.norwood.wfcore.common.worldgen.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.function.IntUnaryOperator;

class RoadProfileTest {

    private static RoadIndex.LinkGeometry line(RoadSpec spec, int n, IntUnaryOperator ground, IntUnaryOperator water) {
        double[] xs = new double[n];
        double[] zs = new double[n];
        short[] g = new short[n];
        short[] w = new short[n];
        for (int i = 0; i < n; i++) {
            xs[i] = i;
            g[i] = (short) ground.applyAsInt(i);
            w[i] = (short) Math.max(g[i], water.applyAsInt(i));
        }
        RoadIndex.LinkGeometry link = new RoadIndex.LinkGeometry(spec, 0, xs, zs);
        link.setTerrain(g, w);
        return link;
    }

    private static RoadIndex.LinkGeometry line(RoadSpec spec, int n, IntUnaryOperator ground) {
        return line(spec, n, ground, i -> Integer.MIN_VALUE);
    }

    /** Rounded profile of a {@code m}-Lipschitz curve: any window of k steps rises at most ceil(m k) + 1. */
    private static void assertGrade(short[] deck, double max, int from, int to) {
        for (int i = from; i < to; i++) {
            for (int k = 1; i + k <= to && k <= 64; k++) {
                int rise = Math.abs(deck[i + k] - deck[i]);
                assertTrue(rise <= Math.ceil(max * k) + 1,
                        "rise " + rise + " over " + k + " stations at " + i + " exceeds grade " + max);
            }
        }
    }

    @Test
    void flatGroundStaysOnGround() {
        RoadProfile.Profile p = RoadProfile.solve(line(RoadWorld.road(), 300, i -> 70));
        for (int i = 0; i < 300; i++) {
            assertEquals(70, p.deck()[i]);
            assertEquals(RoadProfile.GROUND, p.mode()[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void randomTerrainRespectsGrade(int seed) {
        Random random = new Random(seed);
        int[] g = new int[800];
        double y = 80;
        for (int i = 0; i < g.length; i++) {
            y += random.nextGaussian() * 1.5;
            g[i] = (int) Math.round(y);
        }
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, g.length, i -> g[i]));
        assertGrade(p.deck(), spec.grade().max(), 0, g.length - 1);
    }

    @Test
    void mountainBecomesTunnel() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 600, i -> i > 200 && i < 400 ? 140 : 70));
        assertEquals(RoadProfile.TUNNEL, p.mode()[300]);
        assertEquals(0, p.fromStart()[firstOf(p.mode(), RoadProfile.TUNNEL)]);
        assertEquals(p.toEnd()[300] + p.fromStart()[300], p.toEnd()[firstOf(p.mode(), RoadProfile.TUNNEL)]);
        assertGrade(p.deck(), spec.grade().max(), 0, 599);
    }

    @Test
    void valleyBecomesBridge() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 600, i -> i > 250 && i < 350 ? 40 : 90));
        assertEquals(RoadProfile.BRIDGE, p.mode()[300]);
        assertTrue(p.deck()[300] >= 90 - 20, "sag " + (90 - p.deck()[300]));
        for (int i = 0; i < 600; i++) {
            assertTrue(p.mode()[i] != RoadProfile.TUNNEL, "plateau tunnelled at " + i);
        }
    }

    @Test
    void cliffStepRampsWithinGrade() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 2000, i -> i < 1000 ? 64 : 114));
        assertEquals(64, p.deck()[0]);
        assertEquals(114, p.deck()[1999]);
        assertGrade(p.deck(), spec.grade().max(), 0, 1999);
        int cut = 0;
        int fill = 0;
        for (int i = 0; i < 2000; i++) {
            int g = i < 1000 ? 64 : 114;
            cut += Math.max(0, g - p.deck()[i]);
            fill += Math.max(0, p.deck()[i] - g);
        }
        assertTrue(cut > fill, "fill_cost > 1 => more cutting than filling: cut " + cut + " fill " + fill);
        assertTrue(fill > 0, "ramp straddles the cliff");
    }

    @Test
    void riverClearsWaterSurface() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 400, i -> i > 190 && i < 210 ? 55 : 64,
                i -> i > 190 && i < 210 ? 62 : Integer.MIN_VALUE));
        for (int i = 191; i < 210; i++) {
            assertTrue(p.deck()[i] >= 62 + spec.water().clearance(), "deck over water at " + i);
        }
    }

    @Test
    void seaCrossingLiftedToMinDeck() {
        RoadSpec spec = RoadWorld.spec().water(4, 48, 0).build();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 600, i -> i > 200 && i < 400 ? 30 : 64,
                i -> i > 200 && i < 400 ? 62 : Integer.MIN_VALUE));
        for (int i = 201; i < 400; i++) {
            assertTrue(p.deck()[i] >= spec.water().minDeckY(), "sea deck at " + i);
        }
    }

    @Test
    void pinsHoldExactlyAndGradeHoldsAroundThem() {
        RoadSpec spec = RoadWorld.road();
        RoadIndex.LinkGeometry link = line(spec, 500, i -> 70 + (int) (10 * Math.sin(i / 40.0)));
        int[] at = {0, 120, 260, 499};
        int[] y = {72, 80, 74, 70};
        RoadProfile.Profile p = RoadProfile.solve(link, at, y, new int[0]);
        for (int k = 0; k < at.length; k++) {
            assertEquals(y[k], p.deck()[at[k]]);
        }
        assertGrade(p.deck(), spec.grade().max(), 0, 499);
    }

    @Test
    void unreachablePinsStillHold() {
        RoadSpec spec = RoadWorld.road();
        RoadIndex.LinkGeometry link = line(spec, 100, i -> 70);
        RoadProfile.Profile p = RoadProfile.solve(link, new int[] {40, 50}, new int[] {60, 90}, new int[0]);
        assertEquals(60, p.deck()[40]);
        assertEquals(90, p.deck()[50]);
    }

    @Test
    void forcedBridgeOnlyConvertsGround() {
        RoadSpec spec = RoadWorld.road();
        RoadIndex.LinkGeometry link = line(spec, 600, i -> i > 200 && i < 400 ? 140 : 70);
        RoadProfile.Profile p = RoadProfile.solve(link, new int[0], new int[0], new int[] {50, 60, 290, 310});
        for (int i = 50; i <= 60; i++) {
            assertEquals(RoadProfile.BRIDGE, p.mode()[i]);
        }
        for (int i = 290; i <= 310; i++) {
            assertEquals(RoadProfile.TUNNEL, p.mode()[i]);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {11, 12, 13, 14})
    void noShortModeRuns(int seed) {
        Random random = new Random(seed);
        int[] g = new int[1200];
        for (int i = 0; i < g.length; i++) {
            g[i] = 70 + (int) (40 * Math.sin(i / (20.0 + random.nextInt(5)))) + random.nextInt(6);
        }
        RoadProfile.Profile p = RoadProfile.solve(line(RoadWorld.road(), g.length, i -> g[i]));
        int start = 0;
        for (int i = 1; i <= g.length; i++) {
            if (i == g.length || p.mode()[i] != p.mode()[start]) {
                int min = switch (p.mode()[start]) {
                    case RoadProfile.TUNNEL -> RoadProfile.MIN_MODE_RUN;
                    case RoadProfile.BRIDGE -> RoadProfile.MIN_MODE_RUN;
                    default -> 0;
                };
                assertTrue(i - start >= min, "mode " + p.mode()[start] + " run " + start + ".." + i);
                start = i;
            }
        }
    }

    @Test
    void clampToPinsIsLipschitzAndExact() {
        double[] y = new double[200];
        for (int i = 0; i < y.length; i++) {
            y[i] = 70;
        }
        RoadProfile.clampToPins(y, 0.1, new int[] {50, 150}, new int[] {80, 74});
        assertEquals(80, y[50]);
        assertEquals(74, y[150]);
        for (int i = 1; i < y.length; i++) {
            assertTrue(Math.abs(y[i] - y[i - 1]) <= 0.1 + 1e-9);
        }
        assertEquals(75, y[0], 1e-9);
        assertEquals(70, y[199], 1e-9);
    }

    @Test
    void lakeShorterThanSeaCrossingStaysLow() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 600, i -> i > 200 && i < 400 ? 56 : 64,
                i -> i > 200 && i < 400 ? 62 : Integer.MIN_VALUE));
        for (int i = 201; i < 400; i++) {
            assertEquals(RoadProfile.BRIDGE, p.mode()[i]);
            assertTrue(p.deck()[i] >= 62 + spec.water().clearance() && p.deck()[i] <= 62 + spec.water().clearance() + 2,
                    "lake deck " + p.deck()[i] + " at " + i);
        }
    }

    @Test
    void shallowStreamIsFordedNotBridged() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 400, i -> i >= 200 && i < 206 ? 62 : 64,
                i -> i >= 200 && i < 206 ? 63 : Integer.MIN_VALUE));
        for (int i = 0; i < 400; i++) {
            assertEquals(RoadProfile.GROUND, p.mode()[i], "mode at " + i);
            assertEquals(64, p.deck()[i], "deck at " + i);
        }
    }

    @Test
    void shortHighGroundIsCutNotTunnelled() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 600, i -> i >= 290 && i < 310 ? 86 : 70));
        for (int i = 0; i < 600; i++) {
            assertEquals(RoadProfile.GROUND, p.mode()[i], "mode at " + i);
        }
        assertTrue(86 - p.deck()[300] > spec.clearance() + spec.minCover(), "cut " + (86 - p.deck()[300]));
    }

    @Test
    void shortDeepRockStaysTunnel() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 600, i -> i >= 285 && i < 315 ? 130 : 70));
        assertEquals(RoadProfile.TUNNEL, p.mode()[300]);
    }

    @Test
    void shortDipIsFilledNotBridged() {
        RoadSpec spec = RoadWorld.road();
        RoadProfile.Profile p = RoadProfile.solve(line(spec, 600, i -> i >= 295 && i < 305 ? 40 : 70));
        for (int i = 0; i < 600; i++) {
            assertEquals(RoadProfile.GROUND, p.mode()[i], "mode at " + i);
        }
    }

    @Test
    void crossingOptionsAreAllUsable() {
        RoadSpec a = RoadWorld.road();
        RoadSpec b = RoadWorld.highway();
        int tunnelHeadroom = Math.max(a.clearance(), a.tunnelHeight() + 1);
        for (int own = 40; own <= 100; own++) {
            for (byte partnerMode = 0; partnerMode < 3; partnerMode++) {
                for (boolean separated : new boolean[] {false, true}) {
                    int[] options = RoadIndex.crossingOptions(a, own, b, 70, partnerMode, false, separated);
                    int cheapest = 0;
                    for (int k = 0; k < options.length; k += 2) {
                        int gap = options[k] - 70;
                        assertTrue(options[k + 1] >= Math.abs(options[k] - own));
                        assertTrue(gap == 0 || gap >= RoadIndex.headroom(b, partnerMode) + 2
                                || -gap >= tunnelHeadroom + 2, "own " + own + " option " + options[k]);
                        if (options[k + 1] < options[cheapest + 1]) {
                            cheapest = k;
                        }
                    }
                    int gap = options[cheapest] - 70;
                    boolean clear = own - 70 >= RoadIndex.headroom(b, partnerMode) + 2
                            || 70 - own >= tunnelHeadroom + 2;
                    assertEquals(clear ? own : separated ? Integer.MIN_VALUE : 70,
                            clear || !separated ? options[cheapest] : gap == 0 ? 70 : Integer.MIN_VALUE,
                            "own " + own + " separated " + separated);
                }
            }
        }
        assertEquals(2, RoadIndex.crossingOptions(a, 95, b, 70, RoadProfile.GROUND, true, true).length,
                "T junction: at grade only");
        assertEquals(25, RoadIndex.crossingOptions(a, 95, b, 70, RoadProfile.GROUND, true, true)[1]);
    }

    /** (height, cost) pairs, cost = |height - own|. */
    private static int[] options(int own, int... heights) {
        int[] out = new int[heights.length * 2];
        for (int i = 0; i < heights.length; i++) {
            out[i * 2] = heights[i];
            out[i * 2 + 1] = Math.abs(heights[i] - own);
        }
        return out;
    }

    @Test
    void chooseCrossingsPrefersCheapestReachable() {
        int[] chosen = RoadIndex.chooseCrossings(new int[] {100, 200},
                new int[][] {options(71, 70, 78, 63), options(71, 64, 72, 57)}, new double[] {1e6, 1e6},
                new int[0], new int[0], 0.07);
        assertEquals(70, chosen[0]);
        assertEquals(72, chosen[1], "over (|72 - 71| = 1) beats at grade (7)");
    }

    @Test
    void chooseCrossingsSwitchesOptionWhenGradeForbidsGreedy() {
        int[] chosen = RoadIndex.chooseCrossings(new int[] {50, 70},
                new int[][] {options(70, 70, 78, 63), options(70, 64, 72, 57)}, new double[] {1e6, 2e6},
                new int[0], new int[0], 0.07);
        assertTrue(chosen[0] != Integer.MIN_VALUE && chosen[1] != Integer.MIN_VALUE);
        assertTrue(Math.abs(chosen[0] - chosen[1]) <= 0.07 * 20 + 1e-9, chosen[0] + " / " + chosen[1]);
    }

    @Test
    void chooseCrossingsSkipsLesserPartnerWhenNothingFits() {
        int[] chosen = RoadIndex.chooseCrossings(new int[] {100, 101},
                new int[][] {options(55, 54), options(55, 58)}, new double[] {2e6, 1e6}, new int[0], new int[0],
                0.07);
        assertEquals(54, chosen[0]);
        assertEquals(Integer.MIN_VALUE, chosen[1]);
    }

    @Test
    void chooseCrossingsTakesSteepRampOverSkip() {
        // 4 blocks in 20 stations: 2.9x a 0.07 grade.
        int[] chosen = RoadIndex.chooseCrossings(new int[] {100, 120},
                new int[][] {options(55, 54), options(55, 58)}, new double[] {2e6, 1e6}, new int[0], new int[0],
                0.07);
        assertEquals(54, chosen[0]);
        assertEquals(58, chosen[1]);
    }

    @Test
    void chooseCrossingsKeepsFixedEndsAndSkipsUnreachable() {
        int[] chosen = RoadIndex.chooseCrossings(new int[] {20}, new int[][] {options(64, 58, 85, 40)},
                new double[] {1e6}, new int[] {0, 99}, new int[] {66, 64}, 0.07);
        assertEquals(Integer.MIN_VALUE, chosen[0]);
        chosen = RoadIndex.chooseCrossings(new int[] {20}, new int[][] {options(64, 66, 74, 59)},
                new double[] {1e6}, new int[] {0, 99}, new int[] {66, 64}, 0.07);
        assertEquals(66, chosen[0]);
    }

    @Test
    void unreachablePinsRampLinearly() {
        double[] y = new double[30];
        RoadProfile.clampToPins(y, 0.1, new int[] {10, 20}, new int[] {60, 70});
        for (int i = 10; i <= 20; i++) {
            assertEquals(60 + (i - 10), y[i], 1e-9);
        }
        assertEquals(60 - 1.0, y[0], 1e-9);
        assertEquals(70 - 0.9, y[29], 1e-9);
    }

    private static int firstOf(byte[] mode, byte wanted) {
        for (int i = 0; i < mode.length; i++) {
            if (mode[i] == wanted) {
                return i;
            }
        }
        return -1;
    }
}
