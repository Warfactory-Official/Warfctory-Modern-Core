package com.norwood.wfcore.common.worldgen.road;

/**
 * Turns a link's routed polyline into the centreline the rasteriser actually walks: a centripetal Catmull-Rom
 * spline through the routed vertices, then resampled at a fixed arc length so station {@code i} sits exactly
 * {@code i} blocks along the road.
 *
 * <p>The spline is not decoration. The router emits straights meeting at Bezier fillets, and those fillets arrive
 * as a handful of vertices tens of blocks apart — interpolating them linearly would face every curve, and a
 * faceted curve is exactly what a vehicle feels as a series of steering corrections. Centripetal parameterisation
 * (alpha = 0.5) is what keeps the spline from looping or cusping where a dense fillet meets a long straight.
 */
public final class RoadCurve {

    /** Arc length between stations. One block keeps the nearest-station error under an eighth of a block. */
    public static final double STATION_SPACING = 1.0;

    private static final double ALPHA = 0.5;
    /** Spline evaluation step, as a fraction of a station. Finer than the resample so arc length is accurate. */
    private static final double EVAL_STEP = 0.25;

    private RoadCurve() {}

    /** Stations along {@code link}, as {@code {xs, zs}}. Never shorter than two stations. */
    public static double[][] stations(RoadNetwork.Link link) {
        double[][] input = dedupe(link);
        double[][] dense = spline(input[0], input[1]);
        return resample(dense[0], dense[1]);
    }

    /** Consecutive duplicate vertices make the Catmull-Rom knot spacing zero, so they go first. */
    private static double[][] dedupe(RoadNetwork.Link link) {
        int n = link.pointCount();
        double[] xs = new double[n];
        double[] zs = new double[n];
        int kept = 0;
        for (int i = 0; i < n; i++) {
            double x = link.x(i);
            double z = link.z(i);
            if (kept > 0 && Math.abs(x - xs[kept - 1]) < 1.0E-6 && Math.abs(z - zs[kept - 1]) < 1.0E-6) {
                continue;
            }
            xs[kept] = x;
            zs[kept] = z;
            kept++;
        }
        if (kept < 2) {
            // A degenerate link still has to produce a walkable pair so callers need no special case.
            return new double[][] { { xs[0], xs[0] + 1.0 }, { zs[0], zs[0] } };
        }
        return new double[][] { java.util.Arrays.copyOf(xs, kept), java.util.Arrays.copyOf(zs, kept) };
    }

    private static double[][] spline(double[] px, double[] pz) {
        int n = px.length;
        // Flat growable arrays, not a list of two-element arrays. The dense sampling runs to four points per
        // block over the whole network, and a boxed point each would be the largest single allocation the road
        // system makes — it measured as the top wfcore allocation site before this.
        double[] xs = new double[Math.max(16, n * 8)];
        double[] zs = new double[xs.length];
        int count = 0;
        xs[count] = px[0];
        zs[count] = pz[0];
        count++;

        for (int i = 0; i < n - 1; i++) {
            double x0 = i > 0 ? px[i - 1] : 2 * px[i] - px[i + 1];
            double z0 = i > 0 ? pz[i - 1] : 2 * pz[i] - pz[i + 1];
            double x3 = i < n - 2 ? px[i + 2] : 2 * px[i + 1] - px[i];
            double z3 = i < n - 2 ? pz[i + 2] : 2 * pz[i + 1] - pz[i];

            double t0 = 0.0;
            double t1 = t0 + knot(x0, z0, px[i], pz[i]);
            double t2 = t1 + knot(px[i], pz[i], px[i + 1], pz[i + 1]);
            double t3 = t2 + knot(px[i + 1], pz[i + 1], x3, z3);

            double chord = length(px[i + 1] - px[i], pz[i + 1] - pz[i]);
            int steps = Math.max(2, (int) Math.ceil(chord / (STATION_SPACING * EVAL_STEP)));
            for (int s = 1; s <= steps; s++) {
                double t = t1 + (t2 - t1) * ((double) s / steps);
                xs = grow(xs, count);
                zs = grow(zs, count);
                xs[count] = eval(t, t0, t1, t2, t3, x0, px[i], px[i + 1], x3);
                zs[count] = eval(t, t0, t1, t2, t3, z0, pz[i], pz[i + 1], z3);
                count++;
            }
        }
        return new double[][] { java.util.Arrays.copyOf(xs, count), java.util.Arrays.copyOf(zs, count) };
    }

    private static double knot(double ax, double az, double bx, double bz) {
        // Guard the zero-length case the dedupe above cannot see (collinear extrapolated end knots).
        return Math.max(1.0E-4, Math.pow(length(bx - ax, bz - az), ALPHA));
    }

    private static double eval(double t, double t0, double t1, double t2, double t3,
            double p0, double p1, double p2, double p3) {
        double a1 = ((t1 - t) * p0 + (t - t0) * p1) / (t1 - t0);
        double a2 = ((t2 - t) * p1 + (t - t1) * p2) / (t2 - t1);
        double a3 = ((t3 - t) * p2 + (t - t2) * p3) / (t3 - t2);
        double b1 = ((t2 - t) * a1 + (t - t0) * a2) / (t2 - t0);
        double b2 = ((t3 - t) * a2 + (t - t1) * a3) / (t3 - t1);
        return ((t2 - t) * b1 + (t - t1) * b2) / (t2 - t1);
    }

    /** Walks the dense spline and emits a point every {@link #STATION_SPACING} of arc length. */
    private static double[][] resample(double[] dx, double[] dz) {
        int estimate = 16;
        double[] xs = new double[estimate];
        double[] zs = new double[estimate];
        int count = 0;

        double carry = 0.0;
        double cx = dx[0];
        double cz = dz[0];
        xs = grow(xs, count);
        zs = grow(zs, count);
        xs[count] = cx;
        zs[count] = cz;
        count++;

        for (int i = 1; i < dx.length; i++) {
            double segX = dx[i] - cx;
            double segZ = dz[i] - cz;
            double segLen = length(segX, segZ);
            while (carry + segLen >= STATION_SPACING) {
                double advance = STATION_SPACING - carry;
                double f = advance / segLen;
                cx += segX * f;
                cz += segZ * f;
                xs = grow(xs, count);
                zs = grow(zs, count);
                xs[count] = cx;
                zs[count] = cz;
                count++;
                segX = dx[i] - cx;
                segZ = dz[i] - cz;
                segLen = length(segX, segZ);
                carry = 0.0;
            }
            carry += segLen;
            cx = dx[i];
            cz = dz[i];
        }
        // Always finish on the routed endpoint; a link has to reach its junction exactly.
        if (count < 2 || length(cx - xs[count - 1], cz - zs[count - 1]) > 0.25) {
            xs = grow(xs, count);
            zs = grow(zs, count);
            xs[count] = cx;
            zs[count] = cz;
            count++;
        }
        return new double[][] { java.util.Arrays.copyOf(xs, count), java.util.Arrays.copyOf(zs, count) };
    }

    /** Euclidean length. Math.hypot's overflow guards cost more than they buy at block coordinates. */
    private static double length(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double[] grow(double[] array, int needed) {
        return needed < array.length ? array : java.util.Arrays.copyOf(array, Math.max(16, array.length * 2));
    }
}
