package com.norwood.wfcore.common.worldgen.road;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Deck height and ground / bridge / tunnel mode per station, against probed terrain.
 *
 * <p>Exact fit: {@code min sum (ground - y)+ + fill_cost (y - ground)+} subject to {@code |y[i+1] - y[i]| <= max}
 * and the water floor / pins as hard terms (slope trick, O(n log n)). Then box blur (Lipschitz-preserving =>
 * vertical curves), floor raise, pin envelope clamp.
 */
public final class RoadProfile {

    public static final byte GROUND = 0;
    public static final byte BRIDGE = 1;
    public static final byte TUNNEL = 2;

    static final int MIN_MODE_RUN = 16;
    static final int PORTAL_STATIONS = 2;

    private static final int[] NONE = new int[0];

    private RoadProfile() {}

    /** Terrain height without generating chunks. */
    public interface Terrain {
        /** {@code (ground << 32) | surface}; {@code surface > ground} => water. */
        long sample(int x, int z);

        static int ground(long packed) {
            return (int) (packed >> 32);
        }

        static int surface(long packed) {
            return (int) packed;
        }
    }

    public static void probe(RoadIndex.LinkGeometry link, Terrain terrain) {
        int[] stations = probeStations(link);
        long[] samples = new long[stations.length];
        IntStream.range(0, stations.length).parallel().forEach(k -> samples[k] = terrain.sample(
                (int) Math.floor(link.x(stations[k])), (int) Math.floor(link.z(stations[k]))));

        int n = link.stationCount();
        short[] ground = new short[n];
        short[] water = new short[n];
        int previous = -1;
        for (int k = 0; k < stations.length; k++) {
            int index = stations[k];
            ground[index] = (short) Terrain.ground(samples[k]);
            water[index] = (short) Math.max(Terrain.ground(samples[k]), Terrain.surface(samples[k]));
            if (previous >= 0) {
                interpolate(ground, previous, index);
                interpolate(water, previous, index);
            }
            previous = index;
        }
        link.setTerrain(ground, water);
    }

    public record Profile(short[] deck, byte[] mode, boolean[] portal) {}

    private static int[] probeStations(RoadIndex.LinkGeometry link) {
        int n = link.stationCount();
        int spacing = link.spec().grade().sampleSpacing();
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

    public static Profile solve(RoadIndex.LinkGeometry geometry) {
        return solve(geometry, NONE, NONE, NONE);
    }

    /**
     * @param pinAt        stations, ascending and distinct
     * @param pinY         deck each pinned station must take
     * @param forcedBridge {@code [from, to]} station pairs that become BRIDGE where classified GROUND
     */
    public static Profile solve(RoadIndex.LinkGeometry geometry, int[] pinAt, int[] pinY, int[] forcedBridge) {
        RoadSpec spec = geometry.spec();
        int n = geometry.stationCount();
        int[] ground = new int[n];
        int[] surface = new int[n];
        for (int i = 0; i < n; i++) {
            ground[i] = geometry.ground(i);
            surface[i] = geometry.water(i);
        }
        double[] floor = waterFloor(spec, ground, surface);
        double maxRise = spec.grade().max() * RoadCurve.STATION_SPACING;
        double[] y = blur(fit(ground, floor, maxRise, spec.grade().fillCost(), pinAt, pinY),
                spec.grade().smoothingRadius());
        double[] required = floor.clone();
        raiseToLipschitz(required, maxRise);
        for (int i = 0; i < n; i++) {
            y[i] = Math.max(y[i], required[i]);
        }
        clampToPins(y, maxRise, pinAt, pinY);

        short[] deck = new short[n];
        for (int i = 0; i < n; i++) {
            deck[i] = (short) Math.round(y[i]);
        }

        byte[] mode = classify(spec, ground, deck);
        smoothRuns(mode);
        for (int k = 0; k + 1 < forcedBridge.length; k += 2) {
            for (int i = Math.max(0, forcedBridge[k]); i <= Math.min(n - 1, forcedBridge[k + 1]); i++) {
                if (mode[i] == GROUND) {
                    mode[i] = BRIDGE;
                }
            }
        }
        return new Profile(deck, mode, portals(mode));
    }

    private static void interpolate(short[] values, int from, int to) {
        int span = to - from;
        for (int i = from + 1; i < to; i++) {
            double f = (double) (i - from) / span;
            values[i] = (short) Math.round(values[from] + (values[to] - values[from]) * f);
        }
    }

    /** Deck lower bound: water surface + clearance; runs >= {@code sea_crossing_length} also >= {@code min_deck_y}. */
    private static double[] waterFloor(RoadSpec spec, int[] ground, int[] surface) {
        int n = ground.length;
        double[] floor = new double[n];
        Arrays.fill(floor, Double.NEGATIVE_INFINITY);
        int runStart = -1;
        for (int i = 0; i <= n; i++) {
            boolean wet = i < n && surface[i] > ground[i];
            if (wet && runStart < 0) {
                runStart = i;
            } else if (!wet && runStart >= 0) {
                boolean sea = i - runStart >= spec.water().seaCrossingLength();
                for (int k = runStart; k < i; k++) {
                    double required = surface[k] + spec.water().clearance();
                    floor[k] = sea ? Math.max(required, spec.water().minDeckY()) : required;
                }
                runStart = -1;
            }
        }
        return floor;
    }

    /** Weight of a hard term: above any total earthwork cost a link can accrue. */
    private static final double HARD = 1.0e9;

    /**
     * Slope trick. F_i(y) = min cost of stations 0..i with y_i = y, convex piecewise linear: {@code left} holds
     * the breakpoints of its descending part, {@code right} of its ascending part, as (position, slope change).
     * Grade step = {@code left} shifts -m, {@code right} +m (lazy offsets). Backtrack: y_i = clamp(argmin F_i,
     * y_{i+1} -+ m).
     */
    static double[] fit(int[] ground, double[] floor, double maxRise, double fillCost, int[] pinAt, int[] pinY) {
        int n = ground.length;
        Slopes slopes = new Slopes(n);
        double[] argmin = new double[n];
        int pin = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                slopes.leftOffset -= maxRise;
                slopes.rightOffset += maxRise;
            }
            slopes.addDescending(ground[i], 1.0);
            slopes.addAscending(ground[i], fillCost);
            if (floor[i] != Double.NEGATIVE_INFINITY) {
                slopes.addDescending(floor[i], HARD);
            }
            if (pin < pinAt.length && pinAt[pin] == i) {
                slopes.addDescending(pinY[pin], HARD);
                slopes.addAscending(pinY[pin], HARD);
                pin++;
            }
            argmin[i] = Math.max(slopes.left.top() + slopes.leftOffset,
                    Math.min(slopes.right.top() + slopes.rightOffset, ground[i]));
        }
        double[] y = new double[n];
        y[n - 1] = argmin[n - 1];
        for (int i = n - 2; i >= 0; i--) {
            y[i] = Math.clamp(argmin[i], y[i + 1] - maxRise, y[i + 1] + maxRise);
        }
        return y;
    }

    private static final class Slopes {

        final Heap left;
        final Heap right;
        double leftOffset;
        double rightOffset;

        Slopes(int n) {
            left = new Heap(true, n * 2);
            right = new Heap(false, n * 2);
        }

        /** + w (a - y)+ */
        void addDescending(double a, double w) {
            right.push(a - rightOffset, w);
            move(right, rightOffset, left, leftOffset, w);
        }

        /** + w (y - a)+ */
        void addAscending(double a, double w) {
            left.push(a - leftOffset, w);
            move(left, leftOffset, right, rightOffset, w);
        }

        private static void move(Heap from, double fromOffset, Heap to, double toOffset, double weight) {
            while (weight > 0.0) {
                double taken = Math.min(weight, from.topWeight());
                to.push(from.top() + fromOffset - toOffset, taken);
                from.take(taken);
                weight -= taken;
            }
        }
    }

    /** Binary heap of (position, weight); max-heap when {@code max}. */
    private static final class Heap {

        private final boolean max;
        private double[] position;
        private double[] weight;
        private int size;

        Heap(boolean max, int capacity) {
            this.max = max;
            position = new double[Math.max(4, capacity)];
            weight = new double[position.length];
        }

        double top() {
            return position[0];
        }

        double topWeight() {
            return weight[0];
        }

        void push(double p, double w) {
            if (size == position.length) {
                position = Arrays.copyOf(position, size * 2);
                weight = Arrays.copyOf(weight, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int parent = (i - 1) >> 1;
                if (!above(p, position[parent])) {
                    break;
                }
                position[i] = position[parent];
                weight[i] = weight[parent];
                i = parent;
            }
            position[i] = p;
            weight[i] = w;
        }

        /** Removes {@code w} from the top entry, popping it when spent. */
        void take(double w) {
            if (w < weight[0]) {
                weight[0] -= w;
                return;
            }
            double p = position[--size];
            double pw = weight[size];
            int i = 0;
            while (true) {
                int child = i * 2 + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && above(position[child + 1], position[child])) {
                    child++;
                }
                if (!above(position[child], p)) {
                    break;
                }
                position[i] = position[child];
                weight[i] = weight[child];
                i = child;
            }
            position[i] = p;
            weight[i] = pw;
        }

        private boolean above(double a, double b) {
            return max ? a > b : a < b;
        }
    }

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
     * Clamps each span between consecutive pins into the envelope of its two pins (outer spans: of the one pin).
     * Lipschitz in => Lipschitz out, pins exact. Unreachable pair ({@code |h_b - h_a| > m (b - a)}) => linear
     * ramp between them: grade exceeded evenly, never a step.
     */
    static void clampToPins(double[] y, double maxRise, int[] at, int[] value) {
        int pins = at.length;
        if (pins == 0) {
            return;
        }
        int n = y.length;
        for (int i = 0; i < at[0]; i++) {
            double reach = maxRise * (at[0] - i);
            y[i] = Math.clamp(y[i], value[0] - reach, value[0] + reach);
        }
        for (int i = at[pins - 1] + 1; i < n; i++) {
            double reach = maxRise * (i - at[pins - 1]);
            y[i] = Math.clamp(y[i], value[pins - 1] - reach, value[pins - 1] + reach);
        }
        for (int k = 0; k + 1 < pins; k++) {
            int a = at[k];
            int b = at[k + 1];
            int ha = value[k];
            int hb = value[k + 1];
            boolean reachable = Math.abs(hb - ha) <= maxRise * (b - a) + 1e-9;
            for (int i = a + 1; i < b; i++) {
                if (reachable) {
                    double low = Math.max(ha - maxRise * (i - a), hb - maxRise * (b - i));
                    double high = Math.min(ha + maxRise * (i - a), hb + maxRise * (b - i));
                    y[i] = Math.clamp(y[i], low, high);
                } else {
                    y[i] = ha + (double) (hb - ha) * (i - a) / (b - a);
                }
            }
        }
        for (int k = 0; k < pins; k++) {
            y[at[k]] = value[k];
        }
    }

    private static void raiseToLipschitz(double[] y, double maxRise) {
        for (int i = 1; i < y.length; i++) {
            y[i] = Math.max(y[i], y[i - 1] - maxRise);
        }
        for (int i = y.length - 2; i >= 0; i--) {
            y[i] = Math.max(y[i], y[i + 1] - maxRise);
        }
    }

    private static byte[] classify(RoadSpec spec, int[] ground, short[] deck) {
        int n = ground.length;
        byte[] mode = new byte[n];
        int cover = spec.clearance() + spec.minCover();
        for (int i = 0; i < n; i++) {
            if (ground[i] > deck[i] + cover) {
                mode[i] = TUNNEL;
            } else if (deck[i] - ground[i] >= spec.bridgeMinHeight()) {
                mode[i] = BRIDGE;
            } else {
                mode[i] = GROUND;
            }
        }
        return mode;
    }

    /** Runs shorter than {@link #MIN_MODE_RUN} take the preceding run's mode (following, for a leading run). */
    private static void smoothRuns(byte[] mode) {
        int n = mode.length;
        int start = 0;
        while (start < n) {
            int end = start;
            while (end < n && mode[end] == mode[start]) {
                end++;
            }
            if (end - start < MIN_MODE_RUN) {
                byte replacement = start > 0 ? mode[start - 1] : (end < n ? mode[end] : mode[start]);
                Arrays.fill(mode, start, end, replacement);
            }
            start = end;
        }
    }

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
