package com.norwood.wfcore.common.worldgen.road;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Solves what height a road sits at, station by station, and decides where it rides on the ground, on a bridge or
 * inside a tunnel.
 *
 * <p>The input is real terrain — sampled straight out of this world's chunk generator, not out of the router's
 * approximation of it — so the profile is correct for the mountains Minecraft actually builds. The output has to
 * satisfy two things at once that pull against each other: it should hug the ground so the road does not stand on
 * an endless viaduct, and its gradient has to stay inside a budget a vehicle can drive without the surface
 * reading as a staircase.
 *
 * <p>That is solved by relaxation rather than by any closed form. The profile starts on the terrain, then each
 * pass blurs it (which is what creates the vertical curves — a road crests a hill on an arc, it does not turn a
 * corner) and projects it back inside the gradient limit with a symmetric Gauss-Seidel sweep. The last step is a
 * Lipschitz raise onto the hard floor, which is the one operation that enforces water clearance and the gradient
 * limit simultaneously: raising a point to clear the water pulls its neighbours up by no more than one step's
 * worth of grade, so the approach ramps fall out of it for free.
 */
public final class RoadProfile {

    public static final byte GROUND = 0;
    public static final byte BRIDGE = 1;
    public static final byte TUNNEL = 2;

    /** Modes flicker on noisy ground; anything shorter than this is absorbed into its neighbour. */
    private static final int MIN_MODE_RUN = 16;
    /** Stations at each end of a tunnel that get a portal face rather than an open bore. */
    static final int PORTAL_STATIONS = 2;

    private RoadProfile() {}

    /**
     * Reads terrain height without touching or generating chunks.
     *
     * <p>Both heights come back from one call because each one costs a full evaluation of the dimension's
     * density-function graph — on a terrain mod's noise settings that is the single most expensive thing this
     * system does, and asking twice per column doubles the world's load time for nothing.
     */
    public interface Terrain {
        /** Ground and water-surface height for one column, packed as {@code (ground << 32) | surface}. */
        long sample(int x, int z);

        static int ground(long packed) {
            return (int) (packed >> 32);
        }

        /** Top of the column including fluid, so {@code surface > ground} means water. */
        static int surface(long packed) {
            return (int) packed;
        }
    }

    /**
     * Probes terrain for every link at once, spreading the columns over all cores.
     *
     * <p>Batched across links rather than per link because link lengths vary by a factor of five: probing them
     * one at a time leaves most of the pool idle waiting on the longest one. Probing is a pure function of
     * position, so running it out of order changes nothing about the result — only the profile solve that follows
     * has to stay sequential, and that part is arithmetic.
     */
    public static void probeAll(List<RoadIndex.LinkGeometry> links, Terrain terrain) {
        int[][] stations = new int[links.size()][];
        int total = 0;
        for (int i = 0; i < links.size(); i++) {
            stations[i] = probeStations(links.get(i));
            total += stations[i].length;
        }

        int[] owner = new int[total];
        int[] station = new int[total];
        int at = 0;
        for (int i = 0; i < links.size(); i++) {
            for (int index : stations[i]) {
                owner[at] = i;
                station[at] = index;
                at++;
            }
        }

        long[] samples = new long[total];
        IntStream.range(0, total).parallel().forEach(k -> {
            RoadIndex.LinkGeometry link = links.get(owner[k]);
            samples[k] = terrain.sample((int) Math.floor(link.x(station[k])), (int) Math.floor(link.z(station[k])));
        });

        at = 0;
        for (int i = 0; i < links.size(); i++) {
            RoadIndex.LinkGeometry link = links.get(i);
            int n = link.stationCount();
            short[] ground = new short[n];
            short[] water = new short[n];
            int previous = -1;
            for (int index : stations[i]) {
                long packed = samples[at++];
                ground[index] = (short) Terrain.ground(packed);
                water[index] = (short) Math.max(Terrain.ground(packed), Terrain.surface(packed));
                if (previous >= 0) {
                    interpolate(ground, previous, index);
                    interpolate(water, previous, index);
                }
                previous = index;
            }
            link.setTerrain(ground, water);
        }
    }

    /** Station indices to probe: every {@code sample_spacing} along the link, and always both ends. */
    private static int[] probeStations(RoadIndex.LinkGeometry link) {
        int n = link.stationCount();
        int spacing = link.type().grade().sampleSpacing();
        int count = (n - 1) / spacing + 1;
        boolean endIncluded = (n - 1) % spacing == 0;
        int[] out = new int[endIncluded ? count : count + 1];
        for (int i = 0; i < count; i++) {
            out[i] = i * spacing;
        }
        if (!endIncluded) {
            out[count] = n - 1;
        }
        return out;
    }

    /**
     * Fills in {@code geometry}'s deck and mode arrays. {@link #probeAll} must have run first.
     *
     * @param pinStart deck height the first station must take, when it lands on a junction or on another road
     * @param pinEnd   deck height the last station must take
     */
    public static void solve(RoadIndex.LinkGeometry geometry, OptionalInt pinStart, OptionalInt pinEnd) {
        RoadType type = geometry.type();
        int n = geometry.stationCount();
        int[] ground = new int[n];
        int[] surface = new int[n];
        for (int i = 0; i < n; i++) {
            ground[i] = geometry.ground(i);
            surface[i] = geometry.water(i);
        }

        double[] floor = waterFloor(type, ground, surface);
        double[] target = new double[n];
        for (int i = 0; i < n; i++) {
            target[i] = Math.max(ground[i], floor[i]);
        }

        double maxRise = type.grade().max() * RoadCurve.STATION_SPACING;
        double[] y = target.clone();
        pin(y, pinStart, pinEnd);

        double attraction = type.grade().attraction();
        for (int pass = 0; pass < type.grade().relaxPasses(); pass++) {
            y = blur(y, type.grade().smoothingRadius());
            for (int i = 0; i < n; i++) {
                // Pull back onto the ground. Blur alone has no fixed point but the mean height of the entire
                // road, so iterating it walks the profile off the terrain and leaves the whole route standing on
                // a viaduct — smooth, drivable, and nothing like a road.
                y[i] = Math.max(y[i] + (target[i] - y[i]) * attraction, floor[i]);
            }
            pin(y, pinStart, pinEnd);
            limitGrade(y, maxRise, pinStart.isPresent(), pinEnd.isPresent());
        }

        // Cut the profile down to the gradient limit rather than lifting it up to it. This is the step that
        // decides a road's whole character where the ground rises faster than it may climb: lowering leaves the
        // mountain standing above the road, which classify() then reads as a tunnel, whereas raising would send
        // the road over the top on a ramp — and drag every valley for a kilometre either side onto a viaduct
        // holding that ramp up.
        lowerToLipschitz(y, maxRise);

        // Water clearance is the one thing that may still lift the road, and it is applied as its own minimal
        // grade-legal profile so a sea crossing ramps up to its deck without a distant mountain doing the same.
        double[] required = floor.clone();
        raiseToLipschitz(required, maxRise);
        for (int i = 0; i < n; i++) {
            y[i] = Math.max(y[i], required[i]);
        }

        // The pinned ends get the last word: a junction two links share has to agree to the block.
        pin(y, pinStart, pinEnd);
        rampFromPin(y, maxRise, pinStart.isPresent(), pinEnd.isPresent());

        short[] deck = new short[n];
        for (int i = 0; i < n; i++) {
            deck[i] = (short) Math.round(y[i]);
        }

        byte[] mode = classify(type, ground, deck);
        smoothRuns(mode);
        geometry.setProfile(deck, mode, portals(mode));
    }

    private static void interpolate(short[] values, int from, int to) {
        int span = to - from;
        for (int i = from + 1; i < to; i++) {
            double f = (double) (i - from) / span;
            values[i] = (short) Math.round(values[from] + (values[to] - values[from]) * f);
        }
    }

    /**
     * The height the deck may never go below. Over water that is the surface plus clearance; over a crossing long
     * enough to read as open sea it is additionally lifted to {@code min_deck_y} so ships fit under it.
     */
    private static double[] waterFloor(RoadType type, int[] ground, int[] surface) {
        int n = ground.length;
        double[] floor = new double[n];
        java.util.Arrays.fill(floor, Double.NEGATIVE_INFINITY);

        int runStart = -1;
        for (int i = 0; i <= n; i++) {
            boolean wet = i < n && surface[i] > ground[i];
            if (wet && runStart < 0) {
                runStart = i;
            } else if (!wet && runStart >= 0) {
                int runLength = i - runStart;
                boolean sea = runLength >= type.water().seaCrossingLength();
                for (int k = runStart; k < i; k++) {
                    double required = surface[k] + type.water().clearance();
                    floor[k] = sea ? Math.max(required, type.water().minDeckY()) : required;
                }
                runStart = -1;
            }
        }
        return floor;
    }

    private static void pin(double[] y, OptionalInt start, OptionalInt end) {
        if (start.isPresent()) {
            y[0] = start.getAsInt();
        }
        if (end.isPresent()) {
            y[y.length - 1] = end.getAsInt();
        }
    }

    /** Box blur with edge clamping — a running sum, so the radius is free. */
    private static double[] blur(double[] y, int radius) {
        int n = y.length;
        double[] out = new double[n];
        int window = radius * 2 + 1;
        double sum = 0.0;
        for (int i = -radius; i <= radius; i++) {
            sum += y[clamp(i, n)];
        }
        for (int i = 0; i < n; i++) {
            out[i] = sum / window;
            sum -= y[clamp(i - radius, n)];
            sum += y[clamp(i + radius + 1, n)];
        }
        return out;
    }

    private static int clamp(int i, int n) {
        return i < 0 ? 0 : (i >= n ? n - 1 : i);
    }

    /**
     * Projects onto the gradient limit by splitting every excess step between its two ends, which keeps the
     * profile centred on the terrain instead of dragging the whole road up or down the way a one-sided clamp
     * would. Pinned ends take no share.
     */
    private static void limitGrade(double[] y, double maxRise, boolean pinnedStart, boolean pinnedEnd) {
        int n = y.length;
        for (int sweep = 0; sweep < 3; sweep++) {
            boolean forward = sweep % 2 == 0;
            for (int k = 0; k < n - 1; k++) {
                int i = forward ? k : n - 2 - k;
                double delta = y[i + 1] - y[i];
                double excess = Math.abs(delta) - maxRise;
                if (excess <= 0.0) {
                    continue;
                }
                double lowWeight = (i == 0 && pinnedStart) ? 0.0 : 1.0;
                double highWeight = (i + 1 == n - 1 && pinnedEnd) ? 0.0 : 1.0;
                double total = lowWeight + highWeight;
                if (total == 0.0) {
                    continue;
                }
                double signed = Math.signum(delta) * excess;
                y[i] += signed * (lowWeight / total);
                y[i + 1] -= signed * (highWeight / total);
            }
        }
    }

    /**
     * Walks outward from a pinned end, forcing the grade limit, and stops as soon as the profile is already
     * inside it. Setting a pinned end after the relaxation leaves a step there, and the symmetric sweep cannot
     * clear it because a pinned station takes no share of the correction — the step just sits at the junction. A
     * one-sided ramp spreads it instead, over exactly as many stations as the grade budget needs and no more.
     */
    private static void rampFromPin(double[] y, double maxRise, boolean pinnedStart, boolean pinnedEnd) {
        if (pinnedStart) {
            for (int i = 1; i < y.length; i++) {
                double clamped = Math.clamp(y[i], y[i - 1] - maxRise, y[i - 1] + maxRise);
                if (Math.abs(clamped - y[i]) < 1.0E-9) {
                    break;
                }
                y[i] = clamped;
            }
        }
        if (pinnedEnd) {
            for (int i = y.length - 2; i >= 0; i--) {
                double clamped = Math.clamp(y[i], y[i + 1] - maxRise, y[i + 1] + maxRise);
                if (Math.abs(clamped - y[i]) < 1.0E-9) {
                    break;
                }
                y[i] = clamped;
            }
        }
    }

    /**
     * The smallest profile that is everywhere at least {@code y} and never falls faster than {@code maxRise} per
     * station. Two sweeps are enough: after both, no neighbour differs by more than one step in either direction.
     */
    private static void raiseToLipschitz(double[] y, double maxRise) {
        for (int i = 1; i < y.length; i++) {
            y[i] = Math.max(y[i], y[i - 1] - maxRise);
        }
        for (int i = y.length - 2; i >= 0; i--) {
            y[i] = Math.max(y[i], y[i + 1] - maxRise);
        }
    }

    /**
     * The largest profile that is everywhere at most {@code y} and never climbs faster than {@code maxRise} per
     * station — the mirror of {@link #raiseToLipschitz}. Shaves peaks instead of filling troughs, so a road meets
     * ground it cannot climb by staying put and letting the hill close over it.
     */
    private static void lowerToLipschitz(double[] y, double maxRise) {
        for (int i = 1; i < y.length; i++) {
            y[i] = Math.min(y[i], y[i - 1] + maxRise);
        }
        for (int i = y.length - 2; i >= 0; i--) {
            y[i] = Math.min(y[i], y[i + 1] + maxRise);
        }
    }

    /**
     * Ground, bridge or tunnel. A road is tunnelled only once there is real rock over the headroom — below that
     * it is cheaper and better-looking to cut a trench, which is what the embankment margin does.
     */
    private static byte[] classify(RoadType type, int[] ground, short[] deck) {
        int n = ground.length;
        byte[] mode = new byte[n];
        int cover = type.deck().clearance() + type.tunnel().minCover();
        for (int i = 0; i < n; i++) {
            int fill = deck[i] - ground[i];
            if (ground[i] > deck[i] + cover) {
                mode[i] = TUNNEL;
            } else if (fill >= type.bridge().minHeight()) {
                mode[i] = BRIDGE;
            } else {
                mode[i] = GROUND;
            }
        }
        return mode;
    }

    /** Collapses runs too short to build, so the road does not alternate deck and embankment every few blocks. */
    private static void smoothRuns(byte[] mode) {
        int n = mode.length;
        int start = 0;
        while (start < n) {
            int end = start;
            while (end < n && mode[end] == mode[start]) {
                end++;
            }
            if (end - start < MIN_MODE_RUN) {
                // Absorb into whatever precedes it; that run has already passed the length test, so the merge
                // cannot create a new short one. Falls back to the following run for a short leading run.
                byte replacement = start > 0 ? mode[start - 1] : (end < n ? mode[end] : mode[start]);
                java.util.Arrays.fill(mode, start, end, replacement);
            }
            start = end;
        }
    }

    /** Marks the stations at each end of a tunnel run, where the bore needs a wall face instead of open air. */
    private static boolean[] portals(byte[] mode) {
        int n = mode.length;
        boolean[] portal = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (mode[i] != TUNNEL) {
                continue;
            }
            boolean nearStart = i < PORTAL_STATIONS || mode[i - PORTAL_STATIONS] != TUNNEL;
            boolean nearEnd = i >= n - PORTAL_STATIONS || mode[i + PORTAL_STATIONS] != TUNNEL;
            portal[i] = nearStart || nearEnd;
        }
        return portal;
    }
}
