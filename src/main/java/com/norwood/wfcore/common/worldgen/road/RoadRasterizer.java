package com.norwood.wfcore.common.worldgen.road;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Road network -> blocks, one column at a time, as a distance field: the column's signed offset from each nearby
 * centreline picks lane / shoulder / railing / wall / embankment.
 *
 * <ul>
 * <li>Bands are half-open, {@code -h <= o < h}: an axis-aligned road of width w covers exactly w columns at any
 * sub-block phase. Closed bands gave w + 1 whenever the centreline sat on a block edge => width flickered
 * through curves.
 * <li>Roads within {@link #SAME_SURFACE} blocks of deck share one surface; the column takes the road it is most
 * inside (paved > shoulder > earthworks, then priority, then depth inside the edge). Nearest-centreline choice
 * punched holes where a narrow road crosses a wide one.
 * <li>Separated surfaces stamp lowest first; the upper writes nothing below the lower headroom except its own
 * deck. Bridge piers skip columns over a lower deck.
 * </ul>
 */
public final class RoadRasterizer {

    public enum Part {
        SURFACE, EDGE, MARKING, BASE, EMBANKMENT, BRIDGE_DECK, STRUCTURE, ACCENT, RAILING, WALL, TUNNEL_FLOOR,
        CEILING, LIGHT
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

        /** {@code connections}: railing sides, N=1 E=2 S=4 W=8. */
        void place(int x, int y, int z, Part part, RoadIndex.LinkGeometry link, int station, int connections);
    }

    static final int MAX_HITS = 8;
    static final int SAME_SURFACE = 4;
    private static final int PIER_THICKNESS = 2;
    private static final int MAX_VERTICAL = 96;
    static final int CANOPY_REACH = 6;
    private static final int NO_FLOOR = Integer.MIN_VALUE;

    private final RoadIndex index;
    private final Canvas canvas;
    private final Hits hits = new Hits();
    private final Hits probe = new Hits();
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
                    sweepVegetation(hits.link[i], hits.station[i], minX + dx, minZ + dz);
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

        void collect(int x, int z) {
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
            java.util.Arrays.fill(groupOf, 0, count, -1);
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

        /** Bridge railing stands at this column at height y. */
        boolean railing(int y) {
            for (int g = 0; g < groups; g++) {
                int w = winner[g];
                RoadIndex.LinkGeometry l = link[w];
                RoadSpec spec = l.spec();
                if (l.mode(station[w]) != RoadProfile.BRIDGE || !spec.railing() || !kerb(w) || covered(g)) {
                    continue;
                }
                int d = deck[w];
                if (y > d && y <= d + spec.railingHeight()) {
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

    boolean column(int x, int z) {
        hits.collect(x, z);
        if (hits.count == 0) {
            return false;
        }
        int top = canvas.top(x, z);
        for (int g = 0; g < hits.groups; g++) {
            int w = hits.winner[g];
            RoadIndex.LinkGeometry link = hits.link[w];
            int station = hits.station[w];
            double offset = hits.offset[w];
            // Too-close decks: the upper surface still lands, its structure may not.
            int floor = Math.min(hits.floor(g), hits.deck[w]);
            if (inBand(offset, link.spec().gradedHalfWidth())) {
                switch (link.mode(station)) {
                    case RoadProfile.BRIDGE -> bridge(g, link, station, offset, x, z, top, floor);
                    case RoadProfile.TUNNEL -> tunnel(link, station, offset, x, z, floor);
                    default -> onGround(link, station, offset, x, z, top, floor);
                }
            }
        }
        return true;
    }

    private void onGround(RoadIndex.LinkGeometry link, int station, double offset, int x, int z, int top,
            int floor) {
        RoadSpec spec = link.spec();
        int deck = link.deck(station);
        if (inBand(offset, spec.outerHalfWidth())) {
            // To the column top: a road stays on the ground up to the tunnel threshold => hillside to remove.
            clear(x, z, deck + 1, Math.max(deck + spec.clearance(), top));
            place(x, deck, z, surface(link, station, offset), link, station, floor);
            foundation(link, station, x, z, deck, floor);
            return;
        }
        double flare = (Math.abs(offset) - spec.outerHalfWidth()) * spec.batter();
        int cutCeiling = deck + spec.clearance() + (int) Math.round(flare);
        clear(x, z, cutCeiling + 1, top);
        fillDown(link, station, x, z, deck - (int) Math.round(flare), floor, Part.EMBANKMENT);
    }

    private void bridge(int g, RoadIndex.LinkGeometry link, int station, double offset, int x, int z, int top,
            int floor) {
        RoadSpec spec = link.spec();
        if (!inBand(offset, spec.outerHalfWidth())) {
            return;
        }
        int deck = link.deck(station);
        boolean kerb = hits.kerb(hits.winner[g]) && !hits.covered(g);
        clear(x, z, deck + 1, Math.max(deck + spec.clearance(), top));
        Part surface = kerb ? Part.ACCENT : marked(link, station, offset) ? Part.MARKING : Part.BRIDGE_DECK;
        place(x, deck, z, surface, link, station, floor);
        place(x, deck - 1, z, Part.STRUCTURE, link, station, floor);
        if (kerb && spec.railing()) {
            int connections = 0;
            for (int h = 1; h <= spec.railingHeight(); h++) {
                int y = deck + h;
                if (h == 1) {
                    connections = (railingAt(x, z - 1, y) ? 1 : 0) | (railingAt(x + 1, z, y) ? 2 : 0)
                            | (railingAt(x, z + 1, y) ? 4 : 0) | (railingAt(x - 1, z, y) ? 8 : 0);
                }
                place(x, y, z, Part.RAILING, link, station, floor, connections);
            }
        }
        if (floor == NO_FLOOR && inBand(offset, spec.pierRadius() + 0.5)
                && Math.floorMod(station, spec.pierSpacing()) < PIER_THICKNESS) {
            fillDown(link, station, x, z, deck - 2, floor, Part.STRUCTURE);
        }
    }

    private boolean railingAt(int x, int z, int y) {
        probe.collect(x, z);
        return probe.railing(y);
    }

    private void tunnel(RoadIndex.LinkGeometry link, int station, double offset, int x, int z, int floor) {
        RoadSpec spec = link.spec();
        int deck = link.deck(station);
        int ceiling = deck + spec.tunnelHeight() + 1;
        boolean bore = inBand(offset, spec.pavedHalfWidth());
        boolean lining = inBand(offset, spec.outerHalfWidth());
        if (!lining) {
            if (link.portal(station) && inBand(offset, spec.outerHalfWidth() + 2.0)) {
                for (int y = deck; y <= ceiling; y++) {
                    place(x, y, z, Part.WALL, link, station, floor);
                }
            }
            return;
        }
        place(x, deck, z, bore ? surface(link, station, offset) : Part.TUNNEL_FLOOR, link, station, floor);
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
    }

    private Part surface(RoadIndex.LinkGeometry link, int station, double offset) {
        if (marked(link, station, offset)) {
            return Part.MARKING;
        }
        return inBand(offset, link.spec().pavedHalfWidth()) ? Part.SURFACE : Part.EDGE;
    }

    /** Station index == arc length => dash rhythm holds through curves. */
    private static boolean marked(RoadIndex.LinkGeometry link, int station, double offset) {
        RoadSpec spec = link.spec();
        return spec.paintedAt(station) && inBand(offset, spec.markingWidth() / 2.0);
    }

    /** Leaves never decay: worldgen placed them without updates, so their distance is stale forever. */
    private void sweepVegetation(RoadIndex.LinkGeometry link, int station, int x, int z) {
        RoadSpec spec = link.spec();
        int deck = link.deck(station);
        int from = link.mode(station) == RoadProfile.TUNNEL ? deck + spec.tunnelHeight() + 2 : deck + 1;
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
            int connections) {
        if (y <= canvas.minY() || y >= canvas.maxY() || y < floor
                || (canvas.cell(x, y, z) & Canvas.BEDROCK) != 0) {
            return;
        }
        canvas.place(x, y, z, part, link, station, connections);
    }
}
