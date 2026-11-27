package com.norwood.wfcore.common.worldgen.road;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Arrays;

/**
 * Road network -> blocks, one column at a time, as a distance field: the column's signed offset from each nearby
 * centreline picks lane / shoulder / railing / wall / side slope. Every block is a function of its own column =>
 * chunk order cannot change the result.
 *
 * <ul>
 * <li>Bands are half-open, {@code -h <= o < h}: an axis-aligned road of width w covers exactly w columns at any
 * sub-block phase. Closed bands gave w + 1 whenever the centreline sat on a block edge => width flickered
 * through curves.
 * <li>Roads within {@link #SAME_SURFACE} blocks of deck share one surface; the column takes the road it is most
 * inside (paved > shoulder > earthworks, then priority, then depth inside the edge). Nearest-centreline choice
 * punched holes where a narrow road crosses a wide one.
 * <li>Separated surfaces stamp lowest first; the upper writes nothing below the lower headroom except its own
 * deck. Bridge piers and side slopes skip columns over a lower deck.
 * <li>Side slopes touch a column only where its ground lies outside {@code [fill line, cut line]} => level ground
 * beside a road keeps its terrain and trees.
 * </ul>
 */
public final class RoadRasterizer {

    public enum Part {
        SURFACE, EDGE, MARKING, BASE, EMBANKMENT, BRIDGE_DECK, STRUCTURE, ACCENT, RAILING, TRUSS, WALL,
        TUNNEL_FLOOR, CEILING, LIGHT, GUARD_RAIL, LAMP_POST, LAMP_LIGHT, SIGN
    }

    /** Block access; {@code cell} answers a bitmask of the flags below. */
    public interface Canvas {

        int AIR = 1;
        /** Air, fluid or replaceable: fills pass through it. */
        int HOLLOW = 2;
        int BEDROCK = 4;

        /** Lowest y with a block; {@link #maxY()} exclusive. */
        int minY();

        int maxY();

        /** Top non-air block of the column, decoration included. */
        int top(int x, int z);

        int cell(int x, int y, int z);

        /** Decoration cleared out of headroom: logs, leaves, plants, surface rocks. */
        boolean vegetation(int x, int y, int z);

        void clear(int x, int y, int z);

        /** {@code data}: RAILING / GUARD_RAIL sides N=1 E=2 S=4 W=8; SIGN rotation 0-15. */
        void place(int x, int y, int z, Part part, RoadIndex.LinkGeometry link, int station, int data);

        /**
         * Side slope top at {@code y}, taking the column's natural surface block from {@code fromY}. {@code fill}:
         * new ground over hollow; else ground exposed by a cut (rock stays rock).
         */
        void turf(int x, int y, int z, int fromY, boolean fill, RoadIndex.LinkGeometry link, int station);
    }

    static final int MAX_HITS = 8;
    static final int SAME_SURFACE = 4;
    private static final int PIER_THICKNESS = 2;
    static final int ABUTMENT = 2;
    private static final int MAX_VERTICAL = 96;
    static final int CANOPY_REACH = 6;
    private static final int NO_FLOOR = Integer.MIN_VALUE;
    /** Ground stations before a portal whose cut sides are walled. */
    static final int WING = 6;
    /** Heading change over +-{@link #CURVE_SPAN} stations past which the outside kerb gets a guard rail. */
    private static final double CURVE_TURN = 0.45;
    private static final int CURVE_SPAN = 8;
    private static final int TRUSS_BAY = 4;

    private final RoadIndex index;
    private final Canvas canvas;
    private final Hits hits = new Hits();
    private final Hits probe = new Hits();
    private final Pad pad = new Pad(hits);
    private final Pad probePad = new Pad(probe);
    private IntArrayList runs;

    public RoadRasterizer(RoadIndex index, Canvas canvas) {
        this.index = index;
        this.canvas = canvas;
    }

    /** Rasterises chunk columns {@code [minX, minX + 15] x [minZ, minZ + 15]}. */
    public boolean chunk(int minX, int minZ, IntArrayList scratch) {
        if (!runs(minX, minZ, scratch)) {
            return false;
        }
        boolean placed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                placed |= column(minX + dx, minZ + dz);
            }
        }
        return placed;
    }

    /**
     * Vegetation sweep of the chunk; runs once all 8 neighbours have decorated (they write up to 16 blocks in,
     * after this chunk's road). Touches only {@link Canvas#vegetation} and {@link Canvas#clear}.
     */
    public void sweep(int minX, int minZ, IntArrayList scratch) {
        if (!runs(minX, minZ, scratch)) {
            return;
        }
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                hits.collect(minX + dx, minZ + dz);
                for (int i = 0; i < hits.count; i++) {
                    RoadSpec spec = hits.link[i].spec();
                    if (hits.distance[i] < spec.outerHalfWidth() + spec.vegetationMargin()) {
                        sweepVegetation(hits.link[i], hits.station[i], minX + dx, minZ + dz);
                    }
                }
            }
        }
    }

    private boolean runs(int minX, int minZ, IntArrayList scratch) {
        index.collectRuns(minX >> 4, minZ >> 4, scratch);
        scratch.removeIf(run -> !index.runReaches(run, minX, minZ, minX + 15, minZ + 15));
        runs = scratch;
        return !scratch.isEmpty();
    }

    static boolean inBand(double offset, double half) {
        return offset >= -half && offset < half;
    }

    /** Nearby roads of one column, one hit per link, grouped into surfaces by deck. */
    private final class Hits {

        final RoadIndex.LinkGeometry[] link = new RoadIndex.LinkGeometry[MAX_HITS];
        final int[] station = new int[MAX_HITS];
        final double[] offset = new double[MAX_HITS];
        final double[] distance = new double[MAX_HITS];
        final int[] deck = new int[MAX_HITS];
        int count;
        /** Groups ascend by winner deck; {@code groupOf[hit]} indexes them. */
        final int[] groupOf = new int[MAX_HITS];
        final int[] winner = new int[MAX_HITS];
        int groups;
        int x;
        int z;

        void collect(int x, int z) {
            this.x = x;
            this.z = z;
            double cx = x + 0.5;
            double cz = z + 0.5;
            count = 0;
            for (int i = 0, n = runs.size(); i < n; i++) {
                int run = runs.getInt(i);
                if (!index.runReaches(run, x, z, x, z)) {
                    continue;
                }
                double reach = index.runReach(run);
                RoadIndex.LinkGeometry geometry = index.link(index.runLink(run));
                int last = index.runLast(run);
                int best = -1;
                double bestDistance = (reach + 1.0) * (reach + 1.0);
                for (int s = index.runFirst(run); s <= last; s++) {
                    double dx = geometry.x(s) - cx;
                    double dz = geometry.z(s) - cz;
                    double d = dx * dx + dz * dz;
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = s;
                    }
                }
                if (best >= 0) {
                    add(geometry, best, cx, cz, reach);
                }
            }
            group();
        }

        private void add(RoadIndex.LinkGeometry geometry, int s, double cx, double cz, double reach) {
            double ax = geometry.x(s);
            double az = geometry.z(s);
            double best = (cx - ax) * (cx - ax) + (cz - az) * (cz - az);
            int lo = Math.max(0, s - 1);
            int hi = Math.min(geometry.stationCount() - 1, s + 1);
            double dirX = geometry.x(hi) - geometry.x(lo);
            double dirZ = geometry.z(hi) - geometry.z(lo);
            double side = dirX * (cz - az) - dirZ * (cx - ax);
            for (int other = s - 1; other <= s + 1; other += 2) {
                if (other < 0 || other >= geometry.stationCount()) {
                    continue;
                }
                int from = Math.min(s, other);
                double fx = geometry.x(from);
                double fz = geometry.z(from);
                double segX = geometry.x(from + 1) - fx;
                double segZ = geometry.z(from + 1) - fz;
                double lengthSquared = segX * segX + segZ * segZ;
                if (lengthSquared <= 0.0) {
                    continue;
                }
                double t = Math.clamp(((cx - fx) * segX + (cz - fz) * segZ) / lengthSquared, 0.0, 1.0);
                double offX = cx - (fx + segX * t);
                double offZ = cz - (fz + segZ * t);
                double d = offX * offX + offZ * offZ;
                if (d < best) {
                    best = d;
                    side = segX * (cz - fz) - segZ * (cx - fx);
                }
            }
            double distance = Math.sqrt(best);
            if (distance > reach) {
                return;
            }
            double signed = side < 0.0 ? -distance : distance;
            for (int i = 0; i < count; i++) {
                if (link[i] == geometry) {
                    if (distance < this.distance[i]) {
                        station[i] = s;
                        offset[i] = signed;
                        this.distance[i] = distance;
                        deck[i] = geometry.deck(s);
                    }
                    return;
                }
            }
            if (count == MAX_HITS) {
                return;
            }
            link[count] = geometry;
            station[count] = s;
            offset[count] = signed;
            this.distance[count] = distance;
            deck[count] = geometry.deck(s);
            count++;
        }

        /**
         * Strongest unassigned hit claims every unassigned hit within {@link #SAME_SURFACE} of its deck; repeat.
         * Chaining from the lowest deck instead split 67 / 71 / 72 into {67, 71} + {72}: a kerb one block over a
         * deck.
         */
        private void group() {
            groups = 0;
            Arrays.fill(groupOf, 0, count, -1);
            for (int assigned = 0; assigned < count; ) {
                int best = -1;
                for (int i = 0; i < count; i++) {
                    if (groupOf[i] < 0 && (best < 0 || better(i, best))) {
                        best = i;
                    }
                }
                for (int i = 0; i < count; i++) {
                    if (groupOf[i] < 0 && Math.abs(deck[i] - deck[best]) <= SAME_SURFACE) {
                        groupOf[i] = groups;
                        assigned++;
                    }
                }
                winner[groups++] = best;
            }
            for (int g = 1; g < groups; g++) {
                for (int h = g; h > 0 && deck[winner[h - 1]] > deck[winner[h]]; h--) {
                    int w = winner[h];
                    winner[h] = winner[h - 1];
                    winner[h - 1] = w;
                    for (int i = 0; i < count; i++) {
                        groupOf[i] = groupOf[i] == h ? h - 1 : groupOf[i] == h - 1 ? h : groupOf[i];
                    }
                }
            }
        }

        int find(RoadIndex.LinkGeometry geometry) {
            for (int i = 0; i < count; i++) {
                if (link[i] == geometry) {
                    return i;
                }
            }
            return -1;
        }

        private int band(int i) {
            RoadSpec spec = link[i].spec();
            double o = offset[i];
            if (inBand(o, spec.pavedHalfWidth())) {
                return 0;
            }
            if (inBand(o, spec.outerHalfWidth())) {
                return 1;
            }
            return inBand(o, spec.gradedHalfWidth()) ? 2 : 3;
        }

        private boolean better(int a, int b) {
            int bandA = band(a);
            int bandB = band(b);
            if (bandA != bandB) {
                return bandA < bandB;
            }
            int priorityA = link[a].spec().priority();
            int priorityB = link[b].spec().priority();
            if (priorityA != priorityB) {
                return priorityA > priorityB;
            }
            return distance[a] - link[a].spec().outerHalfWidth() < distance[b] - link[b].spec().outerHalfWidth();
        }

        boolean onDeck(int g) {
            int w = winner[g];
            return inBand(offset[w], link[w].spec().outerHalfWidth());
        }

        /** Lowest y group g may write: above every lower deck's headroom. */
        int floor(int g) {
            int floor = NO_FLOOR;
            for (int h = 0; h < g; h++) {
                if (onDeck(h)) {
                    int w = winner[h];
                    floor = Math.max(floor,
                            deck[w] + RoadIndex.headroom(link[w].spec(), link[w].mode(station[w])) + 1);
                }
            }
            return floor;
        }

        /** Another road of group g also covers this column with its own deck or shoulder. */
        boolean covered(int g) {
            for (int i = 0; i < count; i++) {
                if (groupOf[i] == g && i != winner[g] && inBand(offset[i], link[i].spec().outerHalfWidth())) {
                    return true;
                }
            }
            return false;
        }

        boolean kerb(int i) {
            double outer = link[i].spec().outerHalfWidth();
            return inBand(offset[i], outer) && !inBand(offset[i], outer - 1.0);
        }
    }

    /** What the junction pads around a column change about it. */
    private final class Pad {

        private final Hits hits;
        /** Per hit: station lies on a pad arm that drops its markings. */
        final boolean[] quiet = new boolean[MAX_HITS];
        /** Per hit: station lies on any pad arm. */
        final boolean[] local = new boolean[MAX_HITS];
        /** Per hit: column is on the arm's stop line. */
        final boolean[] stop = new boolean[MAX_HITS];
        /** Per hit: -1 = sign, +1 = lamp of the arm's stop line stands here. */
        final int[] post = new int[MAX_HITS];
        final int[] postFacing = new int[MAX_HITS];
        /** Inside a corner fillet, a shoulder's width or more from its arc. */
        boolean interior;
        /** Hit owning a fillet column outside every band, -1 none. */
        int fill;
        Part fillPart;
        /** Distance to a fillet arc, for a column outside it. */
        double outline;
        private final int[] armHit = new int[8];

        Pad(Hits hits) {
            this.hits = hits;
        }

        /** Hit w's kerb here lies inside its pad, not on the outline. */
        boolean interior(int w) {
            return interior && local[w];
        }

        void evaluate() {
            Arrays.fill(quiet, 0, hits.count, false);
            Arrays.fill(local, 0, hits.count, false);
            Arrays.fill(stop, 0, hits.count, false);
            Arrays.fill(post, 0, hits.count, 0);
            interior = false;
            fill = -1;
            outline = Double.POSITIVE_INFINITY;
            RoadNodes.Node[] nodes = index.nodesNear(hits.x >> 4, hits.z >> 4);
            if (nodes == null) {
                return;
            }
            double cx = hits.x + 0.5;
            double cz = hits.z + 0.5;
            for (RoadNodes.Node node : nodes) {
                double dx = cx - node.x;
                double dz = cz - node.z;
                if (dx * dx + dz * dz > node.radius * node.radius || node.arms.length > armHit.length
                        || !index.active(node)) {
                    continue;
                }
                for (int k = 0; k < node.arms.length; k++) {
                    armHit[k] = arm(node.arms[k]);
                }
                for (RoadNodes.Corner corner : node.corners) {
                    corner(node, corner);
                }
            }
        }

        private int arm(RoadNodes.Arm arm) {
            int h = hits.find(arm.link);
            if (h < 0) {
                return -1;
            }
            int along = arm.along(hits.station[h]);
            if (along < 0) {
                return -1;
            }
            local[h] = true;
            if (arm.major) {
                return h;
            }
            quiet[h] = true;
            RoadSpec spec = arm.link.spec();
            int stopStation = arm.station + arm.dir * arm.throat;
            // Inbound traffic keeps right: the side of decreasing angle seen along the outward direction.
            boolean approach = hits.offset[h] * arm.dir < 0.0;
            if (along == arm.throat && spec.markingWidth() > 0 && approach
                    && inBand(hits.offset[h], spec.pavedHalfWidth())) {
                stop[h] = true;
            }
            if (Math.abs(hits.station[h] - stopStation) <= 2) {
                if (spec.stopSign() && anchored(arm.link, stopStation, -arm.dir, hits.x, hits.z)) {
                    post[h] = -1;
                    postFacing[h] = facing(arm.link, stopStation, arm.dir);
                } else if (spec.lampHeight() > 0 && anchored(arm.link, stopStation, arm.dir, hits.x, hits.z)) {
                    post[h] = 1;
                }
            }
            return h;
        }

        /**
         * Edges of two arms meeting at angle theta, fillet centre c at distance r from both. With a, b the signed
         * distances from the lines through c parallel to the edges: {@code |p - c|^2 = (a^2 + b^2 + 2ab cos
         * theta) / sin^2 theta}; p lies between the tangent points iff {@code a + b cos theta <= 0} and
         * {@code b + a cos theta <= 0}.
         */
        private void corner(RoadNodes.Node node, RoadNodes.Corner corner) {
            int ha = armHit[corner.a()];
            int hb = armHit[corner.b()];
            if (ha < 0 || hb < 0 || ha == hb) {
                return;
            }
            RoadNodes.Arm armA = node.arms[corner.a()];
            RoadNodes.Arm armB = node.arms[corner.b()];
            if (hits.offset[ha] * armA.dir <= 0.0 || hits.offset[hb] * armB.dir >= 0.0) {
                return;
            }
            double da = hits.distance[ha] - armA.link.spec().outerHalfWidth();
            double db = hits.distance[hb] - armB.link.spec().outerHalfWidth();
            double a = da - corner.radius();
            double b = db - corner.radius();
            if (a + b * corner.cos() > 0.0 || b + a * corner.cos() > 0.0) {
                return;
            }
            double fromCentre = Math.sqrt(Math.max(0.0, a * a + b * b + 2.0 * a * b * corner.cos())
                    / corner.sinSquared());
            if (fromCentre > corner.limit()) {
                return;
            }
            double inside = fromCentre - corner.radius();
            if (inside < 0.0) {
                outline = Math.min(outline, -inside);
                return;
            }
            boolean deep = inside >= corner.shoulder();
            interior |= deep;
            if (da >= 0.0 && db >= 0.0 && (fill < 0 || deep)) {
                fill = da <= db ? ha : hb;
                fillPart = deep ? Part.SURFACE : Part.EDGE;
            }
        }
    }

    /**
     * Column is the one kerb column of {@code station} on the side {@code side} (sign of the offset): of the
     * kerb line's columns, the nearest to the station's normal. Straight-road approximation at the station.
     */
    private static boolean anchored(RoadIndex.LinkGeometry link, int station, int side, int x, int z) {
        int lo = Math.max(0, station - 1);
        int hi = Math.min(link.stationCount() - 1, station + 1);
        double tx = link.x(hi) - link.x(lo);
        double tz = link.z(hi) - link.z(lo);
        double length = Math.sqrt(tx * tx + tz * tz);
        if (length == 0.0) {
            return false;
        }
        tx /= length;
        tz /= length;
        double qx = x + 0.5 - link.x(station);
        double qz = z + 0.5 - link.z(station);
        double half = link.spec().outerHalfWidth();
        double along = Math.abs(qx * tx + qz * tz);
        if (along > 1.0 || !kerbOffset(tx * qz - tz * qx, half, side)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                double nx = qx + dx;
                double nz = qz + dz;
                double other = Math.abs(nx * tx + nz * tz);
                if ((dx != 0 || dz != 0) && kerbOffset(tx * nz - tz * nx, half, side)
                        && (other < along - 1e-9 || (other < along + 1e-9 && (dx < 0 || (dx == 0 && dz < 0))))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean kerbOffset(double offset, double half, int side) {
        return side > 0 ? offset >= half - 1.0 && offset < half : offset >= -half && offset < -half + 1.0;
    }

    /** Sign rotation (0 = south, 4 = west) facing along {@code dir} x the station tangent. */
    private static int facing(RoadIndex.LinkGeometry link, int station, int dir) {
        int lo = Math.max(0, station - 1);
        int hi = Math.min(link.stationCount() - 1, station + 1);
        double fx = (link.x(hi) - link.x(lo)) * dir;
        double fz = (link.z(hi) - link.z(lo)) * dir;
        return (int) Math.round(Math.toDegrees(Math.atan2(-fx, fz)) / 22.5) & 15;
    }

    /** +1 / -1: offset sign of the outside of a tight curve at the station, 0 on a straight. */
    private static int curveOutside(RoadIndex.LinkGeometry link, int station) {
        int a = station - CURVE_SPAN;
        int b = station + CURVE_SPAN;
        if (a < 0 || b >= link.stationCount()) {
            return 0;
        }
        double inX = link.x(station) - link.x(a);
        double inZ = link.z(station) - link.z(a);
        double outX = link.x(b) - link.x(station);
        double outZ = link.z(b) - link.z(station);
        double turn = Math.atan2(inX * outZ - inZ * outX, inX * outX + inZ * outZ);
        return Math.abs(turn) < CURVE_TURN ? 0 : turn > 0.0 ? -1 : 1;
    }

    boolean column(int x, int z) {
        hits.collect(x, z);
        if (hits.count == 0) {
            return false;
        }
        pad.evaluate();
        int top = canvas.top(x, z);
        for (int g = 0; g < hits.groups; g++) {
            int w = hits.winner[g];
            RoadIndex.LinkGeometry link = hits.link[w];
            int station = hits.station[w];
            double offset = hits.offset[w];
            // Too-close decks: the upper surface still lands, its structure may not.
            int lower = hits.floor(g);
            int floor = Math.min(lower, hits.deck[w]);
            RoadSpec spec = link.spec();
            if (inBand(offset, spec.outerHalfWidth())) {
                switch (link.mode(station)) {
                    case RoadProfile.BRIDGE -> bridge(g, x, z, top, floor);
                    case RoadProfile.TUNNEL -> tunnel(w, x, z, floor, true);
                    default -> onGround(g, x, z, top, floor);
                }
            } else if (pad.fill >= 0 && hits.groupOf[pad.fill] == g
                    && hits.link[pad.fill].mode(hits.station[pad.fill]) == RoadProfile.GROUND) {
                RoadIndex.LinkGeometry owner = hits.link[pad.fill];
                int at = hits.station[pad.fill];
                int deck = owner.deck(at);
                clear(x, z, deck + 1, Math.max(deck + owner.spec().clearance(), top));
                place(x, deck, z, pad.fillPart, owner, at, Math.min(lower, deck));
                foundation(owner, at, x, z, deck, Math.min(lower, deck));
            } else if (link.mode(station) == RoadProfile.TUNNEL) {
                tunnel(w, x, z, floor, false);
            } else if (link.mode(station) == RoadProfile.GROUND && lower == NO_FLOOR) {
                slope(link, station, Math.min(outward(offset, spec.outerHalfWidth()), pad.outline), x, z, top);
            }
        }
        return true;
    }

    /**
     * Mid-distance of the column's 1-wide ring outside the band: 0.5 for the first on either side. The half-open
     * band puts the first outside centre at 0 on one side and 1 on the other.
     */
    private static double outward(double offset, double half) {
        return (offset >= 0.0 ? Math.floor(offset - half) : Math.ceil(-offset - half) - 1.0) + 0.5;
    }

    private Part deckPart(int w, Part paved) {
        RoadIndex.LinkGeometry link = hits.link[w];
        RoadSpec spec = link.spec();
        double offset = hits.offset[w];
        if (pad.stop[w] || (!pad.quiet[w] && spec.paintedAt(hits.station[w])
                && inBand(offset, spec.markingWidth() / 2.0))) {
            return Part.MARKING;
        }
        return inBand(offset, spec.pavedHalfWidth()) || pad.interior(w) ? paved : Part.EDGE;
    }

    private void onGround(int g, int x, int z, int top, int floor) {
        int w = hits.winner[g];
        RoadIndex.LinkGeometry link = hits.link[w];
        int station = hits.station[w];
        RoadSpec spec = link.spec();
        int deck = link.deck(station);
        // To the column top: a road stays on the ground up to the tunnel threshold => hillside to remove.
        clear(x, z, deck + 1, Math.max(deck + spec.clearance(), top));
        place(x, deck, z, deckPart(w, Part.SURFACE), link, station, floor);
        foundation(link, station, x, z, deck, floor);
        if (!posts(w, x, z, deck, floor) && guardRail(hits, pad, g)) {
            place(x, deck + 1, z, Part.GUARD_RAIL, link, station, floor, railSides(x, z, deck + 1));
        }
    }

    /** Sign or lamp standing on this kerb column. */
    private boolean posts(int w, int x, int z, int deck, int floor) {
        RoadIndex.LinkGeometry link = hits.link[w];
        int station = hits.station[w];
        RoadSpec spec = link.spec();
        if (pad.interior(w) || hits.covered(hits.groupOf[w])) {
            return false;
        }
        if (pad.post[w] < 0) {
            place(x, deck + 1, z, Part.SIGN, link, station, floor, pad.postFacing[w]);
            return true;
        }
        boolean lamp = pad.post[w] > 0;
        if (!lamp && spec.lampHeight() > 0 && spec.lampSpacing() > 0 && !pad.local[w]) {
            int nearest = Math.round((float) station / spec.lampSpacing());
            int at = nearest * spec.lampSpacing();
            lamp = at < link.stationCount() && Math.abs(at - station) <= 2
                    && anchored(link, at, (nearest & 1) == 0 ? 1 : -1, x, z);
        }
        if (!lamp) {
            return false;
        }
        for (int h = 1; h < spec.lampHeight(); h++) {
            place(x, deck + h, z, Part.LAMP_POST, link, station, floor);
        }
        place(x, deck + spec.lampHeight(), z, Part.LAMP_LIGHT, link, station, floor);
        return true;
    }

    /** Group g's winner carries a guard rail on this column: high fill, or outside of a tight curve. */
    private static boolean guardRail(Hits hits, Pad pad, int g) {
        int w = hits.winner[g];
        RoadIndex.LinkGeometry link = hits.link[w];
        int station = hits.station[w];
        RoadSpec spec = link.spec();
        if (spec.guardRailFill() == 0 || link.mode(station) != RoadProfile.GROUND || !hits.kerb(w)
                || pad.local[w] || hits.covered(g)) {
            return false;
        }
        if (link.deck(station) - link.ground(station) >= spec.guardRailFill()) {
            return true;
        }
        int outside = curveOutside(link, station);
        return outside != 0 && (hits.offset[w] < 0.0 ? -1 : 1) == outside;
    }

    /** Group g's winner carries a bridge railing on this column. */
    private static boolean railing(Hits hits, Pad pad, int g) {
        int w = hits.winner[g];
        RoadIndex.LinkGeometry link = hits.link[w];
        return link.mode(hits.station[w]) == RoadProfile.BRIDGE && link.spec().railing() && hits.kerb(w)
                && !pad.interior(w) && !hits.covered(g);
    }

    private int railSides(int x, int z, int y) {
        return (railAt(x, z - 1, y) ? 1 : 0) | (railAt(x + 1, z, y) ? 2 : 0) | (railAt(x, z + 1, y) ? 4 : 0)
                | (railAt(x - 1, z, y) ? 8 : 0);
    }

    /** A railing or guard rail stands at this column at height y. */
    private boolean railAt(int x, int z, int y) {
        probe.collect(x, z);
        if (probe.count == 0) {
            return false;
        }
        probePad.evaluate();
        for (int g = 0; g < probe.groups; g++) {
            int w = probe.winner[g];
            int deck = probe.deck[w];
            if (y == deck + 1 ? railing(probe, probePad, g) || guardRail(probe, probePad, g)
                    : y > deck && y <= deck + probe.link[w].spec().railingHeight() && railing(probe, probePad, g)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Side slope at {@code o} blocks outside the shoulder: ground below the fill line is raised to it, ground
     * above the cut line lowered to it, ground between left alone. Before a portal the first column is a wing
     * wall.
     */
    private void slope(RoadIndex.LinkGeometry link, int station, double o, int x, int z, int top) {
        RoadSpec spec = link.spec();
        if (o >= spec.margin()) {
            return;
        }
        int deck = link.deck(station);
        int soil = soil(x, z, top);
        int fillTop = deck - (int) Math.ceil(o * spec.batter());
        int cutTop = deck + (int) Math.floor(o * spec.cutBatter());
        int wing = soil > deck ? wing(link, station) : 0;
        if (wing > 0 && o < 1.0) {
            int wallTop = Math.min(deck + wing, soil);
            for (int y = deck; y <= wallTop; y++) {
                place(x, y, z, Part.WALL, link, station, NO_FLOOR);
            }
            clear(x, z, wallTop + 1, top);
            return;
        }
        if (wing > 0) {
            cutTop = deck + wing + (int) Math.floor((o - 1.0) * spec.cutBatter());
        }
        if (soil < fillTop) {
            for (int y = Math.max(soil + 1, fillTop - MAX_VERTICAL); y < fillTop; y++) {
                place(x, y, z, Part.EMBANKMENT, link, station, NO_FLOOR);
            }
            if (fillTop > canvas.minY() && fillTop < canvas.maxY()) {
                canvas.turf(x, fillTop, z, soil, true, link, station);
            }
            clear(x, z, fillTop + 1, top);
        } else if (soil > cutTop) {
            if (cutTop > canvas.minY() && (canvas.cell(x, cutTop, z) & (Canvas.HOLLOW | Canvas.BEDROCK)) == 0) {
                canvas.turf(x, cutTop, z, soil, false, link, station);
            }
            clear(x, z, cutTop + 1, top);
        }
    }

    /** Wing wall height over the deck at a ground station leading into a tunnel, 0 elsewhere. */
    private static int wing(RoadIndex.LinkGeometry link, int station) {
        int k = Integer.MAX_VALUE;
        if (link.neighbourMode(station, true) == RoadProfile.TUNNEL) {
            k = link.toEnd(station);
        }
        if (link.neighbourMode(station, false) == RoadProfile.TUNNEL) {
            k = Math.min(k, link.fromStart(station));
        }
        if (k >= WING) {
            return 0;
        }
        return Math.max(1, (link.spec().tunnelHeight() + 2) * (WING - k) / WING);
    }

    /** Highest block of the column that is neither hollow nor vegetation. */
    private int soil(int x, int z, int top) {
        int y = Math.min(top, canvas.maxY() - 1);
        while (y > canvas.minY() && ((canvas.cell(x, y, z) & Canvas.HOLLOW) != 0 || canvas.vegetation(x, y, z))) {
            y--;
        }
        return y;
    }

    private void bridge(int g, int x, int z, int top, int floor) {
        int w = hits.winner[g];
        RoadIndex.LinkGeometry link = hits.link[w];
        int station = hits.station[w];
        double offset = hits.offset[w];
        RoadSpec spec = link.spec();
        int deck = link.deck(station);
        boolean kerb = hits.kerb(w) && !pad.interior(w) && !hits.covered(g);
        clear(x, z, deck + 1, Math.max(deck + spec.clearance(), top));
        place(x, deck, z, kerb ? Part.ACCENT : deckPart(w, Part.BRIDGE_DECK), link, station, floor);

        boolean truss = link.truss(station);
        int from = link.fromStart(station);
        int to = link.toEnd(station);
        int length = from + to + 1;
        int spans = Math.max(1, Math.round((float) length / (spec.pierSpacing() * (truss ? 2 : 1))));
        double span = (double) length / spans;
        int nearest = (int) Math.round(from / span);
        double toPier = Math.abs(from - nearest * span);
        int rise = Math.min(spec.archRise(), (int) (span / 2.0) - 1);
        int depth = 1;
        if (rise > 0) {
            double u = Math.min(1.0, toPier / (span / 2.0));
            depth += (int) Math.round(rise * (1.0 - Math.sqrt(1.0 - (1.0 - u) * (1.0 - u))));
        }
        for (int d = 1; d <= depth; d++) {
            place(x, deck - d, z, Part.STRUCTURE, link, station, floor);
        }
        boolean abutment = from < ABUTMENT || to < ABUTMENT;
        boolean pier = nearest > 0 && nearest < spans && toPier < PIER_THICKNESS / 2.0 + 0.5
                && inBand(offset, spec.pierRadius() + 0.5);
        if (floor == NO_FLOOR && (abutment || pier)) {
            fillDown(link, station, x, z, deck - depth - 1, floor, Part.STRUCTURE);
        }
        if (posts(w, x, z, deck, floor) || !kerb) {
            return;
        }
        if (spec.railing()) {
            int sides = railSides(x, z, deck + 1);
            for (int h = 1; h <= spec.railingHeight(); h++) {
                place(x, deck + h, z, Part.RAILING, link, station, floor, sides);
            }
        }
        if (truss) {
            int chord = deck + spec.clearance() + 1;
            int bay = Math.floorMod(from, TRUSS_BAY * 2);
            int diagonal = bay < TRUSS_BAY ? bay : TRUSS_BAY * 2 - 1 - bay;
            for (int y = deck + 1; y <= chord; y++) {
                int rung = (y - deck - 1) * TRUSS_BAY / (chord - deck);
                if (y == chord || bay == 0 || rung == diagonal) {
                    place(x, y, z, Part.TRUSS, link, station, floor);
                }
            }
        }
    }

    private void tunnel(int w, int x, int z, int floor, boolean lining) {
        RoadIndex.LinkGeometry link = hits.link[w];
        int station = hits.station[w];
        double offset = hits.offset[w];
        RoadSpec spec = link.spec();
        int deck = link.deck(station);
        int ceiling = deck + spec.tunnelHeight() + 1;
        boolean portal = link.portal(station);
        if (!lining) {
            // Headwall: flanks + parapet over the bore, two blocks proud of the lining.
            if (portal && inBand(offset, spec.outerHalfWidth() + 3.0)) {
                for (int y = deck; y <= ceiling + 2; y++) {
                    place(x, y, z, Part.WALL, link, station, floor);
                }
            }
            return;
        }
        boolean bore = inBand(offset, spec.pavedHalfWidth());
        place(x, deck, z, bore ? deckPart(w, Part.SURFACE) : Part.TUNNEL_FLOOR, link, station, floor);
        foundation(link, station, x, z, deck, floor);
        if (bore) {
            clear(x, z, deck + 1, deck + spec.tunnelHeight());
        } else {
            for (int y = deck + 1; y <= deck + spec.tunnelHeight(); y++) {
                place(x, y, z, Part.WALL, link, station, floor);
            }
        }
        boolean lit = spec.tunnelLight() && inBand(offset, 1.0)
                && Math.floorMod(station, spec.lightSpacing()) == 0;
        place(x, ceiling, z, lit ? Part.LIGHT : Part.CEILING, link, station, floor);
        if (portal) {
            for (int y = ceiling + 1; y <= ceiling + 2; y++) {
                place(x, y, z, Part.WALL, link, station, floor);
            }
        }
    }

    /** Leaves never decay: worldgen placed them without updates, so their distance is stale forever. */
    private void sweepVegetation(RoadIndex.LinkGeometry link, int station, int x, int z) {
        RoadSpec spec = link.spec();
        int deck = link.deck(station);
        int from = link.mode(station) == RoadProfile.TUNNEL ? deck + spec.tunnelHeight() + 4 : deck + 1;
        int to = Math.min(from + spec.clearance() + CANOPY_REACH, canvas.maxY() - 1);
        for (int y = from; y <= to; y++) {
            if (canvas.vegetation(x, y, z)) {
                canvas.clear(x, y, z);
            }
        }
    }

    /** Declared depth, then on while hollow: a road over a cave or low embankment is carried, not floating. */
    private void foundation(RoadIndex.LinkGeometry link, int station, int x, int z, int deck, int floor) {
        int depth = link.spec().baseDepth();
        for (int k = 1; k <= depth + MAX_VERTICAL; k++) {
            int y = deck - k;
            if (y <= canvas.minY() || y < floor) {
                return;
            }
            boolean hollow = (canvas.cell(x, y, z) & Canvas.HOLLOW) != 0;
            if (!hollow && k > depth) {
                return;
            }
            place(x, y, z, Part.BASE, link, station, floor);
        }
    }

    private void fillDown(RoadIndex.LinkGeometry link, int station, int x, int z, int from, int floor, Part part) {
        for (int y = from; from - y <= MAX_VERTICAL; y--) {
            if (y <= canvas.minY() || y < floor) {
                return;
            }
            if ((canvas.cell(x, y, z) & Canvas.HOLLOW) == 0) {
                return;
            }
            place(x, y, z, part, link, station, floor);
        }
    }

    /** Bedrock stays. */
    private void clear(int x, int z, int from, int to) {
        int top = Math.min(to, Math.min(from + MAX_VERTICAL, canvas.maxY() - 1));
        for (int y = Math.max(from, canvas.minY()); y <= top; y++) {
            int cell = canvas.cell(x, y, z);
            if ((cell & (Canvas.AIR | Canvas.BEDROCK)) == 0) {
                canvas.clear(x, y, z);
            }
        }
    }

    private void place(int x, int y, int z, Part part, RoadIndex.LinkGeometry link, int station,
            int floor) {
        place(x, y, z, part, link, station, floor, 0);
    }

    private void place(int x, int y, int z, Part part, RoadIndex.LinkGeometry link, int station, int floor,
            int data) {
        if (y <= canvas.minY() || y >= canvas.maxY() || y < floor
                || (canvas.cell(x, y, z) & Canvas.BEDROCK) != 0) {
            return;
        }
        canvas.place(x, y, z, part, link, station, data);
    }
}
