package com.norwood.wfcore.common.worldgen.road;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Plan-view topology, before any terrain: links taken most important first; each is compared with every piece
 * already kept. Two centrelines closer than {@code outer_a + outer_b + SHADOW_GAP} overlap.
 *
 * <ul>
 * <li>overlap longer than {@code LONG_* x touch} (crossing angle < ~20 deg) => shared corridor: cut from the
 * lesser link, which then joins the kept piece where it leaves it;
 * <li>short overlap at a free end => that end attaches (connector to the kept centreline when it falls short);
 * <li>short interior overlap => crossing, height decided per crossing once profiles exist.
 * </ul>
 */
final class RoadTopology {

    static final double SHADOW_GAP = 2.0;
    static final double LONG_INTERIOR = 6.0;
    static final double LONG_END = 3.0;
    static final int MIN_PIECE = 8;
    /** cot 30 deg. */
    private static final double SLIP = 1.7320508;
    private static final int CELL = 16;

    /** Ends: {@code junction >= 0} => network junction key; else {@code attach >= 0} => kept piece + station. */
    static final class Piece {

        final RoadIndex.Source origin;
        final double[] xs;
        final double[] zs;
        long startJunction = -1;
        long endJunction = -1;
        int startAttach = -1;
        int startAttachAt;
        int endAttach = -1;
        int endAttachAt;
        /** (station, partner, partner station) triples. */
        final IntArrayList crossings = new IntArrayList();

        Piece(RoadIndex.Source origin, double[] xs, double[] zs) {
            this.origin = origin;
            this.xs = xs;
            this.zs = zs;
        }

        int stationCount() {
            return xs.length;
        }

        boolean hasJunction(long key) {
            return key >= 0 && (startJunction == key || endJunction == key);
        }
    }

    static long junctionKey(int network, int junction) {
        return ((long) network << 32) | junction;
    }

    private final List<Piece> kept = new ArrayList<>();
    private final Long2ObjectOpenHashMap<LongArrayList> grid = new Long2ObjectOpenHashMap<>();
    private double maxOuter;

    private RoadTopology() {}

    /** {@code ranked} most important first; result in the same order, which pins depend on. */
    static List<Piece> build(List<RoadIndex.Source> ranked) {
        RoadTopology topology = new RoadTopology();
        for (RoadIndex.Source candidate : ranked) {
            topology.add(candidate);
        }
        return topology.kept;
    }

    private static final class Track {

        final float[] distance;
        final int[] at;

        Track(int n) {
            distance = new float[n];
            at = new int[n];
            Arrays.fill(distance, Float.POSITIVE_INFINITY);
        }
    }

    private void add(RoadIndex.Source c) {
        int n = c.xs().length;
        double outer = c.spec().outerHalfWidth();
        double reach = outer + SHADOW_GAP + maxOuter;
        Int2ObjectOpenHashMap<Track> tracks = new Int2ObjectOpenHashMap<>();
        for (int s = 0; s < n; s++) {
            double x = c.xs()[s];
            double z = c.zs()[s];
            int cx0 = Math.floorDiv((int) Math.floor(x - reach), CELL);
            int cx1 = Math.floorDiv((int) Math.floor(x + reach), CELL);
            int cz0 = Math.floorDiv((int) Math.floor(z - reach), CELL);
            int cz1 = Math.floorDiv((int) Math.floor(z + reach), CELL);
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    LongArrayList entries = grid.get(cellKey(cx, cz));
                    if (entries == null) {
                        continue;
                    }
                    for (int e = 0, size = entries.size(); e < size; e++) {
                        long entry = entries.getLong(e);
                        int k = (int) (entry >>> 32);
                        int sk = (int) entry;
                        Piece other = kept.get(k);
                        double touch = outer + other.origin.spec().outerHalfWidth() + SHADOW_GAP;
                        double dx = other.xs[sk] - x;
                        double dz = other.zs[sk] - z;
                        double d = Math.sqrt(dx * dx + dz * dz);
                        if (d >= touch) {
                            continue;
                        }
                        Track track = tracks.computeIfAbsent(k, key -> new Track(n));
                        if (d < track.distance[s]) {
                            track.distance[s] = (float) d;
                            track.at[s] = sk;
                        }
                    }
                }
            }
        }

        int[] trim = new int[n];
        int startAttach = -1;
        int endAttach = -1;
        float startDistance = Float.POSITIVE_INFINITY;
        float endDistance = Float.POSITIVE_INFINITY;
        IntArrayList crossings = new IntArrayList();
        long fromKey = c.from() >= 0 ? junctionKey(c.network(), c.from()) : -1;
        long toKey = c.to() >= 0 ? junctionKey(c.network(), c.to()) : -1;

        int[] partners = tracks.keySet().toIntArray();
        Arrays.sort(partners);
        for (int k : partners) {
            Track track = tracks.get(k);
            Piece other = kept.get(k);
            double touch = outer + other.origin.spec().outerHalfWidth() + SHADOW_GAP;
            int s = 0;
            while (s < n) {
                if (track.distance[s] == Float.POSITIVE_INFINITY) {
                    s++;
                    continue;
                }
                int a = s;
                while (s < n && track.distance[s] != Float.POSITIVE_INFINITY) {
                    s++;
                }
                int b = s - 1;
                int length = b - a + 1;
                boolean atStart = a == 0;
                boolean atEnd = b == n - 1;
                if (atStart && atEnd) {
                    Arrays.fill(trim, k + 1);
                } else if (atStart || atEnd) {
                    long junction = atStart ? fromKey : toKey;
                    if (length >= LONG_END * touch) {
                        Arrays.fill(trim, a, b + 1, k + 1);
                    } else if (!other.hasJunction(junction)) {
                        int end = atStart ? 0 : n - 1;
                        if (atStart && track.distance[end] < startDistance) {
                            startAttach = k;
                            startDistance = track.distance[end];
                        } else if (atEnd && track.distance[end] < endDistance) {
                            endAttach = k;
                            endDistance = track.distance[end];
                        }
                    }
                } else if (length >= LONG_INTERIOR * touch) {
                    Arrays.fill(trim, a, b + 1, k + 1);
                } else {
                    int closest = a;
                    for (int i = a + 1; i <= b; i++) {
                        if (track.distance[i] < track.distance[closest]) {
                            closest = i;
                        }
                    }
                    crossings.add(closest);
                    crossings.add(k);
                    crossings.add(track.at[closest]);
                }
            }
        }

        int p = 0;
        while (p < n) {
            if (trim[p] != 0) {
                p++;
                continue;
            }
            int q = p;
            while (q + 1 < n && trim[q + 1] == 0) {
                q++;
            }
            if (q - p + 1 >= Math.min(MIN_PIECE, n)) {
                emit(c, tracks, trim, p, q, startAttach, endAttach, fromKey, toKey, crossings);
            }
            p = q + 1;
        }
        maxOuter = Math.max(maxOuter, outer);
    }

    private void emit(RoadIndex.Source c, Int2ObjectOpenHashMap<Track> tracks, int[] trim, int p, int q, int startAttach,
            int endAttach, long fromKey, long toKey, IntArrayList crossings) {
        int n = c.xs().length;
        double outer = c.spec().outerHalfWidth();

        Join left = p > 0 ? extend(c, tracks.get(trim[p - 1] - 1), trim, trim[p - 1], p - 1, -1)
                : startAttach >= 0 ? join(c, tracks.get(startAttach), startAttach, 0) : null;
        Join right = q < n - 1 ? extend(c, tracks.get(trim[q + 1] - 1), trim, trim[q + 1], q + 1, 1)
                : endAttach >= 0 ? join(c, tracks.get(endAttach), endAttach, n - 1) : null;
        int first = left != null ? left.station : p;
        int last = right != null ? right.station : q;

        double[] leftLink = left != null ? left.connector : new double[0];
        double[] rightLink = right != null ? right.connector : new double[0];
        int leftCount = leftLink.length / 2;
        int rightCount = rightLink.length / 2;
        int count = leftCount + (last - first + 1) + rightCount;
        double[] xs = new double[count];
        double[] zs = new double[count];
        for (int i = 0; i < leftCount; i++) {
            // Connector arrays run end -> partner; reversed so the piece starts on the partner.
            xs[i] = leftLink[(leftCount - 1 - i) * 2];
            zs[i] = leftLink[(leftCount - 1 - i) * 2 + 1];
        }
        System.arraycopy(c.xs(), first, xs, leftCount, last - first + 1);
        System.arraycopy(c.zs(), first, zs, leftCount, last - first + 1);
        for (int i = 0; i < rightCount; i++) {
            xs[leftCount + last - first + 1 + i] = rightLink[i * 2];
            zs[leftCount + last - first + 1 + i] = rightLink[i * 2 + 1];
        }

        Piece piece = new Piece(c, xs, zs);
        if (left != null) {
            piece.startAttach = left.partner;
            piece.startAttachAt = left.partnerStation;
        } else if (first == 0) {
            piece.startJunction = fromKey;
        }
        if (right != null) {
            piece.endAttach = right.partner;
            piece.endAttachAt = right.partnerStation;
        } else if (last == n - 1) {
            piece.endJunction = toKey;
        }
        for (int i = 0; i < crossings.size(); i += 3) {
            int station = crossings.getInt(i);
            if (station >= p && station <= q) {
                piece.crossings.add(station - first + leftCount);
                piece.crossings.add(crossings.getInt(i + 1));
                piece.crossings.add(crossings.getInt(i + 2));
            }
        }

        int index = kept.size();
        kept.add(piece);
        for (int s = 0; s < count; s++) {
            grid.computeIfAbsent(cellKey(Math.floorDiv((int) Math.floor(xs[s]), CELL),
                    Math.floorDiv((int) Math.floor(zs[s]), CELL)), key -> new LongArrayList(4))
                    .add(((long) index << 32) | s);
        }
        maxOuter = Math.max(maxOuter, outer);
    }

    /** {@code connector}: flat xz from just past {@code station} to the partner centreline, empty when none. */
    private record Join(int station, int partner, int partnerStation, double[] connector) {}

    /**
     * Walks up to {@code 2 x touch} stations from {@code start} into the trimmed run (direction {@code step}) for
     * a station within join distance of the partner. None => slip road: straight connector meeting the partner
     * ~30 deg back along the shared stretch. Walking further re-adds the side-by-side overlap being removed.
     */
    private Join extend(RoadIndex.Source c, Track track, int[] trim, int mark, int start, int step) {
        int partner = mark - 1;
        Piece other = kept.get(partner);
        double joinDistance = joinDistance(c, other);
        int walk = (int) Math.ceil(2.0 * (c.spec().outerHalfWidth() + other.origin.spec().outerHalfWidth()
                + SHADOW_GAP));
        for (int s = start, i = 0; i < walk && s >= 0 && s < trim.length && trim[s] == mark; s += step, i++) {
            if (track.distance[s] <= joinDistance) {
                return new Join(s, partner, track.at[s], new double[0]);
            }
        }
        int deeper = start + step;
        int direction = deeper >= 0 && deeper < trim.length && trim[deeper] == mark
                ? Integer.signum(track.at[deeper] - track.at[start]) : 0;
        if (direction == 0) {
            return join(c, track, partner, start);
        }
        int target = Math.clamp(track.at[start] + direction * Math.round(track.distance[start] * SLIP), 0,
                other.stationCount() - 1);
        return new Join(start, partner, target,
                connector(c.xs()[start], c.zs()[start], other.xs[target], other.zs[target]));
    }

    private Join join(RoadIndex.Source c, Track track, int partner, int station) {
        Piece other = kept.get(partner);
        double x = c.xs()[station];
        double z = c.zs()[station];
        int at = track.at[station];
        double footX = other.xs[at];
        double footZ = other.zs[at];
        double best = (x - footX) * (x - footX) + (z - footZ) * (z - footZ);
        int footStation = at;
        for (int o = at - 1; o <= at; o++) {
            if (o < 0 || o + 1 >= other.stationCount()) {
                continue;
            }
            double ax = other.xs[o];
            double az = other.zs[o];
            double sx = other.xs[o + 1] - ax;
            double sz = other.zs[o + 1] - az;
            double lengthSquared = sx * sx + sz * sz;
            if (lengthSquared <= 0.0) {
                continue;
            }
            double t = Math.clamp(((x - ax) * sx + (z - az) * sz) / lengthSquared, 0.0, 1.0);
            double px = ax + sx * t;
            double pz = az + sz * t;
            double d = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (d < best) {
                best = d;
                footX = px;
                footZ = pz;
                footStation = t < 0.5 ? o : o + 1;
            }
        }
        double distance = Math.sqrt(best);
        if (distance <= joinDistance(c, other)) {
            return new Join(station, partner, footStation, new double[0]);
        }
        return new Join(station, partner, footStation, connector(x, z, footX, footZ));
    }

    /** Stations after {@code (x, z)} up to and including the target, at most one spacing apart. */
    private static double[] connector(double x, double z, double toX, double toZ) {
        double distance = Math.sqrt((toX - x) * (toX - x) + (toZ - z) * (toZ - z));
        int steps = Math.max(1, (int) Math.ceil(distance / RoadCurve.STATION_SPACING));
        double[] out = new double[steps * 2];
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            out[(i - 1) * 2] = x + (toX - x) * f;
            out[(i - 1) * 2 + 1] = z + (toZ - z) * f;
        }
        return out;
    }

    /** Centreline offset at which this road's surface lies inside the partner's. */
    private static double joinDistance(RoadIndex.Source c, Piece other) {
        return Math.max(1.0, other.origin.spec().outerHalfWidth() - c.spec().outerHalfWidth());
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
