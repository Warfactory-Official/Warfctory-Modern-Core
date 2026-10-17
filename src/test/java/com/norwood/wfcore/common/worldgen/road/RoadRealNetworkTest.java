package com.norwood.wfcore.common.worldgen.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code road/main_network.json}: worldgen-workspace router output (default world.json, 92 links), typed like the
 * bundled road_type files. Terrain is synthetic noise.
 */
class RoadRealNetworkTest {

    private static List<RoadIndex.Source> sources;

    @BeforeAll
    static void load() throws Exception {
        JsonObject root;
        try (var in = RoadRealNetworkTest.class.getResourceAsStream("/road/main_network.json")) {
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        sources = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("links")) {
            JsonObject link = element.getAsJsonObject();
            JsonArray flat = link.getAsJsonArray("points");
            int[] points = new int[flat.size()];
            for (int i = 0; i < points.length; i++) {
                points[i] = flat.get(i).getAsInt();
            }
            String kind = link.get("kind").getAsString();
            int from = link.has("from") ? link.get("from").getAsInt() : -1;
            int to = link.has("to") ? link.get("to").getAsInt() : -1;
            RoadNetwork.Link parsed = RoadWorld.link(kind, from, to, points);
            int length = RoadCurve.stations(parsed)[0].length;
            RoadSpec spec = !kind.equals("paved") ? RoadWorld.track()
                    : length >= 6000 ? RoadWorld.highway() : RoadWorld.road();
            sources.add(RoadIndex.Source.of(0, parsed, spec, 0));
        }
    }

    private static RoadWorld world() {
        return new RoadWorld((x, z) -> (int) Math.round(70 + 30 * RoadFuzzTest.noise(5, x / 900.0, z / 900.0)
                + 10 * RoadFuzzTest.noise(6, x / 150.0, z / 150.0)), 62);
    }

    @Test
    void topologyJoinsEveryTrackAndLeavesNoSideBySideRoads() {
        long started = System.nanoTime();
        List<RoadIndex.Source> ranked = new ArrayList<>(sources);
        ranked.sort((a, b) -> {
            int byClass = Integer.compare(b.spec().priority(), a.spec().priority());
            return byClass != 0 ? byClass : Integer.compare(b.xs().length, a.xs().length);
        });
        List<RoadTopology.Piece> pieces = RoadTopology.build(ranked);
        long micros = (System.nanoTime() - started) / 1000;

        int attached = 0;
        int crossings = 0;
        int junctionEnds = 0;
        long stations = 0;
        for (RoadTopology.Piece piece : pieces) {
            stations += piece.stationCount();
            attached += (piece.startAttach >= 0 ? 1 : 0) + (piece.endAttach >= 0 ? 1 : 0);
            junctionEnds += (piece.startJunction >= 0 ? 1 : 0) + (piece.endJunction >= 0 ? 1 : 0);
            crossings += piece.crossings.size() / 3;
        }
        for (RoadIndex.Source source : sources) {
            if (source.spec().priority() != RoadWorld.track().priority()) {
                continue;
            }
            double best = Double.MAX_VALUE;
            for (RoadTopology.Piece piece : pieces) {
                if (piece.origin != source) {
                    best = Math.min(best, distance(piece, source.xs()[0], source.zs()[0]));
                }
            }
            assertTrue(best < 1.0, "track starting at " + source.xs()[0] + "," + source.zs()[0] + " is " + best
                    + " from every other road");
        }
        int worst = sideBySide(pieces);
        List<RoadTopology.Piece> raw = new ArrayList<>();
        for (RoadIndex.Source source : ranked) {
            raw.add(new RoadTopology.Piece(source, source.xs(), source.zs()));
        }
        System.out.printf("real network: %d links -> %d pieces, %,d stations, %d junction ends, %d attachments,"
                + " %d crossings, longest side-by-side run %d (untrimmed %d), topology %d us%n", sources.size(),
                pieces.size(), stations, junctionEnds, attached, crossings, worst, sideBySide(raw), micros);
        double touch = 2 * RoadWorld.highway().outerHalfWidth() + RoadTopology.SHADOW_GAP;
        assertTrue(worst <= (RoadTopology.LONG_END + 2) * touch, "side-by-side run " + worst);
        assertTrue(micros < 2_000_000, "topology took " + micros + " us");
    }

    private static double distance(RoadTopology.Piece piece, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int s = 0; s < piece.stationCount(); s++) {
            best = Math.min(best, Math.hypot(piece.xs[s] - x, piece.zs[s] - z));
        }
        return best;
    }

    /** Longest run of one piece's stations within touch of one earlier piece. */
    private static int sideBySide(List<RoadTopology.Piece> pieces) {
        it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<IntArrayList> grid =
                new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        int worst = 0;
        for (int i = 0; i < pieces.size(); i++) {
            RoadTopology.Piece piece = pieces.get(i);
            it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap runs = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
            for (int s = 0; s < piece.stationCount(); s++) {
                it.unimi.dsi.fastutil.ints.IntOpenHashSet near = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
                int cx = Math.floorDiv((int) Math.floor(piece.xs[s]), 16);
                int cz = Math.floorDiv((int) Math.floor(piece.zs[s]), 16);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        IntArrayList cell = grid.get(RoadWorld.column(cx + dx, cz + dz));
                        if (cell == null) {
                            continue;
                        }
                        for (int e = 0; e < cell.size(); e += 2) {
                            RoadTopology.Piece other = pieces.get(cell.getInt(e));
                            double touch = piece.origin.spec().outerHalfWidth()
                                    + other.origin.spec().outerHalfWidth() + RoadTopology.SHADOW_GAP;
                            double ox = other.xs[cell.getInt(e + 1)] - piece.xs[s];
                            double oz = other.zs[cell.getInt(e + 1)] - piece.zs[s];
                            if (ox * ox + oz * oz < touch * touch) {
                                near.add(cell.getInt(e));
                            }
                        }
                    }
                }
                it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap next = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
                for (int k : near) {
                    int run = runs.get(k) + 1;
                    next.put(k, run);
                    worst = Math.max(worst, run);
                }
                runs = next;
            }
            for (int s = 0; s < piece.stationCount(); s++) {
                grid.computeIfAbsent(RoadWorld.column(Math.floorDiv((int) Math.floor(piece.xs[s]), 16),
                        Math.floorDiv((int) Math.floor(piece.zs[s]), 16)), key -> new IntArrayList()).add(i);
                grid.get(RoadWorld.column(Math.floorDiv((int) Math.floor(piece.xs[s]), 16),
                        Math.floorDiv((int) Math.floor(piece.zs[s]), 16))).add(s);
            }
        }
        return worst;
    }

    @Test
    void junctionsAttachmentsAndCrossingsRasteriseCleanly() {
        RoadWorld world = world();
        long started = System.nanoTime();
        RoadIndex index = RoadIndex.build(world, sources, List.of(), 0L, null);
        long built = System.nanoTime();
        for (int i = 0; i < index.linkCount(); i++) {
            index.link(i);
        }
        long solved = System.nanoTime();

        List<long[]> chunks = new ArrayList<>();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet seen = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        IntArrayList hot = new IntArrayList();
        for (int i = 0; i < index.linkCount(); i++) {
            RoadIndex.LinkGeometry link = index.link(i);
            RoadTopology.Piece piece = index.piece(i);
            hot.add(i);
            hot.add(0);
            hot.add(i);
            hot.add(link.stationCount() - 1);
            for (int c = 0; c < piece.crossings.size(); c += 3) {
                hot.add(i);
                hot.add(piece.crossings.getInt(c));
            }
        }
        for (int h = 0; h < hot.size(); h += 2) {
            RoadIndex.LinkGeometry link = index.link(hot.getInt(h));
            int s = hot.getInt(h + 1);
            int cx = (int) Math.floor(link.x(s)) >> 4;
            int cz = (int) Math.floor(link.z(s)) >> 4;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (seen.add(RoadWorld.column(cx + dx, cz + dz))) {
                        chunks.add(new long[] {cx + dx, cz + dz});
                    }
                }
            }
        }
        long rasterStart = System.nanoTime();
        world.rasterize(index, chunks);
        long rasterNanos = System.nanoTime() - rasterStart;

        int blocked = 0;
        int missing = 0;
        int checked = 0;
        for (int i = 0; i < index.linkCount(); i++) {
            RoadIndex.LinkGeometry link = index.link(i);
            for (int s = 0; s < link.stationCount(); s++) {
                int x = (int) Math.floor(link.x(s));
                int z = (int) Math.floor(link.z(s));
                if (!seen.contains(RoadWorld.column(x >> 4, z >> 4))
                        || !seen.contains(RoadWorld.column((x >> 4) + 1, z >> 4))
                        || !seen.contains(RoadWorld.column((x >> 4) - 1, z >> 4))) {
                    continue;
                }
                checked++;
                int deck = link.deck(s);
                int found = Integer.MIN_VALUE;
                for (int y = deck - RoadRasterizer.SAME_SURFACE; y <= deck + RoadRasterizer.SAME_SURFACE; y++) {
                    RoadRasterizer.Part part = world.part(x, y, z);
                    if (part != null && RoadWorld.isDeck(part)) {
                        found = y;
                        break;
                    }
                }
                if (found == Integer.MIN_VALUE) {
                    missing++;
                    continue;
                }
                RoadRasterizer.Part part = world.part(x, found, z);
                if (part != RoadRasterizer.Part.TUNNEL_FLOOR && part != RoadRasterizer.Part.ACCENT) {
                    for (int h = 1; h <= 3; h++) {
                        if (!world.air(x, found + h, z)) {
                            blocked++;
                            break;
                        }
                    }
                }
            }
        }
        System.out.printf("real network: build %d ms, solve all %d ms, raster %d chunks %.1f us/chunk,"
                + " %d centreline columns checked%n", (built - started) / 1_000_000, (solved - built) / 1_000_000,
                chunks.size(), rasterNanos / 1000.0 / chunks.size(), checked);
        int tracks = 0;
        for (int i = 0; i < index.linkCount(); i++) {
            RoadTopology.Piece piece = index.piece(i);
            if (piece.origin.spec().priority() != RoadWorld.track().priority()
                    || piece.xs[0] != piece.origin.xs()[0] || piece.zs[0] != piece.origin.zs()[0]) {
                continue;
            }
            int deck = index.link(i).deck(0);
            int closest = Integer.MAX_VALUE;
            for (int k = 0; k < index.linkCount(); k++) {
                if (k == i) {
                    continue;
                }
                RoadIndex.LinkGeometry other = index.link(k);
                int s = RoadIntersectionTest.nearest(other, piece.xs[0], piece.zs[0]);
                if (Math.hypot(other.x(s) - piece.xs[0], other.z(s) - piece.zs[0]) < 1.5) {
                    closest = Math.min(closest, Math.abs(other.deck(s) - deck));
                }
            }
            assertEquals(0, closest, "track " + i + " start height off its road");
            tracks++;
        }
        assertTrue(tracks > 0);
        assertEquals(0, missing, "centreline columns without a deck");
        assertEquals(0, blocked, "centreline columns with blocked headroom");
    }
}
