package com.norwood.wfcore.common.worldgen.road;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;

/** Voxel world for road tests: heightfield terrain + sea, sparse overrides, part ownership per block. */
final class RoadWorld implements RoadProfile.Terrain, RoadRasterizer.Canvas {

    static final int MIN_Y = -64;
    static final int MAX_Y = 320;

    static final byte AIR_ID = 0;
    static final byte STONE = 1;
    static final byte WATER = 2;
    static final byte LEAVES = 3;
    static final byte BEDROCK_ID = 4;
    static final byte STALK = 5;
    static final byte PART_BASE = 16;

    final IntBinaryOperator ground;
    final int sea;
    private final Long2ByteOpenHashMap cells = new Long2ByteOpenHashMap();
    private final Long2ObjectOpenHashMap<RoadIndex.LinkGeometry> owner = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap highest = new Long2IntOpenHashMap();
    private final LongOpenHashSet canopy = new LongOpenHashSet();
    private final LongOpenHashSet stalks = new LongOpenHashSet();
    final Long2IntOpenHashMap railings = new Long2IntOpenHashMap();

    RoadWorld(IntBinaryOperator ground, int sea) {
        this.ground = ground;
        this.sea = sea;
        cells.defaultReturnValue((byte) -1);
        highest.defaultReturnValue(Integer.MIN_VALUE);
    }

    static RoadWorld flat(int y) {
        return new RoadWorld((x, z) -> y, MIN_Y);
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y - MIN_Y & 0xFFF);
    }

    static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Leaves block above the terrain, for sweep checks. */
    void leaves(int x, int y, int z) {
        canopy.add(key(x, y, z));
        highest.put(column(x, z), Math.max(highest.get(column(x, z)), y));
    }

    /** Bamboo-like column on the ground. */
    void stalk(int x, int z, int height) {
        int g = ground.applyAsInt(x, z);
        for (int y = g + 1; y <= g + height; y++) {
            stalks.add(key(x, y, z));
        }
        highest.put(column(x, z), Math.max(highest.get(column(x, z)), g + height));
    }

    byte base(int x, int y, int z) {
        if (y <= MIN_Y) {
            return BEDROCK_ID;
        }
        int g = ground.applyAsInt(x, z);
        if (y <= g) {
            return STONE;
        }
        if (y <= sea) {
            return WATER;
        }
        long k = key(x, y, z);
        return canopy.contains(k) ? LEAVES : stalks.contains(k) ? STALK : AIR_ID;
    }

    byte get(int x, int y, int z) {
        byte v = cells.get(key(x, y, z));
        return v >= 0 ? v : base(x, y, z);
    }

    RoadRasterizer.Part part(int x, int y, int z) {
        byte v = get(x, y, z);
        return v >= PART_BASE ? RoadRasterizer.Part.values()[v - PART_BASE] : null;
    }

    RoadIndex.LinkGeometry owner(int x, int y, int z) {
        return owner.get(key(x, y, z));
    }

    boolean air(int x, int y, int z) {
        return get(x, y, z) == AIR_ID;
    }

    // --- Terrain ---

    @Override
    public long sample(int x, int z) {
        int g = ground.applyAsInt(x, z);
        int surface = g < sea ? sea : g;
        return ((long) g << 32) | (surface & 0xFFFFFFFFL);
    }

    // --- Canvas ---

    @Override
    public int minY() {
        return MIN_Y;
    }

    @Override
    public int maxY() {
        return MAX_Y;
    }

    @Override
    public int top(int x, int z) {
        int start = Math.min(MAX_Y - 1, Math.max(Math.max(ground.applyAsInt(x, z), sea), highest.get(column(x, z))));
        for (int y = start; y > MIN_Y; y--) {
            if (get(x, y, z) != AIR_ID) {
                return y;
            }
        }
        return MIN_Y;
    }

    @Override
    public int cell(int x, int y, int z) {
        return switch (get(x, y, z)) {
            case AIR_ID -> AIR | HOLLOW;
            case WATER -> HOLLOW;
            case BEDROCK_ID -> BEDROCK;
            default -> 0;
        };
    }

    @Override
    public boolean vegetation(int x, int y, int z) {
        return get(x, y, z) == LEAVES;
    }

    @Override
    public void clear(int x, int y, int z) {
        long k = key(x, y, z);
        cells.put(k, AIR_ID);
        owner.remove(k);
    }

    @Override
    public void place(int x, int y, int z, RoadRasterizer.Part part, RoadIndex.LinkGeometry link, int station,
            int connections) {
        long k = key(x, y, z);
        cells.put(k, (byte) (PART_BASE + part.ordinal()));
        owner.put(k, link);
        if (part == RoadRasterizer.Part.RAILING) {
            railings.put(k, connections);
        } else {
            railings.remove(k);
        }
        highest.put(column(x, z), Math.max(highest.get(column(x, z)), y));
    }

    /** Snapshot of every override, for determinism comparisons. */
    Long2ByteOpenHashMap snapshot() {
        return new Long2ByteOpenHashMap(cells);
    }

    // --- Building ---

    static boolean isDeck(RoadRasterizer.Part part) {
        return part == RoadRasterizer.Part.SURFACE || part == RoadRasterizer.Part.EDGE
                || part == RoadRasterizer.Part.MARKING || part == RoadRasterizer.Part.BRIDGE_DECK
                || part == RoadRasterizer.Part.ACCENT || part == RoadRasterizer.Part.TUNNEL_FLOOR;
    }

    /** Deck heights in the column, ascending; a deck block with a non-deck block or air above. */
    IntArrayList decks(int x, int z, int from, int to) {
        IntArrayList out = new IntArrayList(2);
        for (int y = from; y <= to; y++) {
            RoadRasterizer.Part p = part(x, y, z);
            if (p != null && isDeck(p) && !isDeck(part(x, y + 1, z))) {
                out.add(y);
            }
        }
        return out;
    }

    /** Rasterises every chunk touching the index's stations (+ margin), in the given chunk order. */
    void rasterize(RoadIndex index, List<long[]> chunkOrder) {
        IntArrayList scratch = new IntArrayList();
        for (long[] chunk : chunkOrder) {
            new RoadRasterizer(index, this).chunk((int) chunk[0] * 16, (int) chunk[1] * 16, scratch);
        }
    }

    static List<long[]> chunks(RoadIndex index, int margin) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < index.linkCount(); i++) {
            RoadIndex.LinkGeometry link = index.link(i);
            for (int s = 0; s < link.stationCount(); s++) {
                minX = Math.min(minX, (int) Math.floor(link.x(s)));
                minZ = Math.min(minZ, (int) Math.floor(link.z(s)));
                maxX = Math.max(maxX, (int) Math.floor(link.x(s)));
                maxZ = Math.max(maxZ, (int) Math.floor(link.z(s)));
            }
        }
        List<long[]> out = new ArrayList<>();
        for (int cx = (minX - margin) >> 4; cx <= (maxX + margin) >> 4; cx++) {
            for (int cz = (minZ - margin) >> 4; cz <= (maxZ + margin) >> 4; cz++) {
                out.add(new long[] {cx, cz});
            }
        }
        return out;
    }

    void rasterize(RoadIndex index) {
        rasterize(index, chunks(index, 32));
    }

    // --- Fixtures ---

    static RoadNetwork.Link link(String kind, int from, int to, int... xz) {
        List<Integer> points = new ArrayList<>(xz.length);
        for (int v : xz) {
            points.add(v);
        }
        return new RoadNetwork.Link(kind, from >= 0 ? OptionalInt.of(from) : OptionalInt.empty(),
                to >= 0 ? OptionalInt.of(to) : OptionalInt.empty(), false, false, points);
    }

    static RoadIndex index(RoadProfile.Terrain terrain, RoadIndex.Source... sources) {
        return RoadIndex.build(terrain, List.of(sources), List.of(), 1L, null);
    }

    static RoadIndex.Source source(RoadSpec spec, RoadNetwork.Link link) {
        return RoadIndex.Source.of(0, link, spec, 0);
    }

    static Spec spec() {
        return new Spec();
    }

    /** RoadSpec builder; defaults = wfcore road.json. */
    static final class Spec {

        int priority = 20;
        int width = 7;
        int shoulder = 1;
        int clearance = 5;
        int vegetationMargin = 6;
        int markingWidth = 0;
        int dash = 4;
        int gap = 4;
        int baseDepth = 3;
        int margin = 5;
        double batter = 1.0;
        double grade = 0.07;
        int smoothing = 6;
        int sampleSpacing = 16;
        double fillCost = 2.0;
        int minDeckY = 90;
        int waterClearance = 4;
        int seaCrossing = 48;
        boolean railing = true;
        int railingHeight = 1;
        int pierSpacing = 12;
        int pierRadius = 2;
        int bridgeMinHeight = 4;
        boolean light = true;
        int lightSpacing = 8;
        int tunnelHeight = 6;
        int minCover = 8;

        Spec priority(int v) {
            priority = v;
            return this;
        }

        Spec width(int v) {
            width = v;
            return this;
        }

        Spec shoulder(int v) {
            shoulder = v;
            return this;
        }

        Spec clearance(int v) {
            clearance = v;
            return this;
        }

        Spec markings(int w, int d, int g) {
            markingWidth = w;
            dash = d;
            gap = g;
            return this;
        }

        Spec grade(double v) {
            grade = v;
            return this;
        }

        Spec sampleSpacing(int v) {
            sampleSpacing = v;
            return this;
        }

        Spec margin(int v) {
            margin = v;
            return this;
        }

        Spec vegetation(int v) {
            vegetationMargin = v;
            return this;
        }

        Spec tunnel(int height, int cover) {
            tunnelHeight = height;
            minCover = cover;
            return this;
        }

        Spec piers(int spacing, int radius) {
            pierSpacing = spacing;
            pierRadius = radius;
            return this;
        }

        RoadSpec build() {
            return new RoadSpec(priority, width, shoulder, clearance, vegetationMargin, markingWidth, dash, gap,
                    baseDepth, margin, batter, new RoadType.Grade(grade, smoothing, sampleSpacing, fillCost),
                    new RoadType.Water(minDeckY, waterClearance, seaCrossing), railing, railingHeight, pierSpacing,
                    pierRadius, bridgeMinHeight, light, lightSpacing, tunnelHeight, minCover);
        }
    }

    static RoadSpec road() {
        return spec().build();
    }

    static RoadSpec highway() {
        return spec().priority(30).width(11).shoulder(2).clearance(6).markings(1, 6, 6).grade(0.05).margin(6)
                .tunnel(7, 10).piers(16, 3).build();
    }

    static RoadSpec track() {
        return spec().priority(10).width(5).shoulder(1).clearance(4).grade(0.11).margin(3).tunnel(5, 8)
                .piers(8, 1).build();
    }
}
