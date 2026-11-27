package com.norwood.wfcore.common.worldgen.road;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** Renders of the meeting / structure cases to {@code build/road-renders/}; assertions live in the other suites. */
class RoadGalleryTest {

    private static final RoadSpec ROAD = RoadWorld.spec().markings(1, 5, 5).furniture(0, 4, 3, true).build();
    private static final RoadSpec HIGHWAY = RoadWorld.spec().priority(30).width(11).shoulder(2).clearance(6)
            .markings(1, 6, 6).grade(0.07).margin(14).tunnel(7, 10).tunnelMinLength(48).piers(16, 3)
            .bridge(7, 6, 96).gradeSeparated().corner(6).furniture(48, 5, 3, true).build();
    private static final RoadSpec TRACK = RoadWorld.track();

    private static RoadWorld rolling() {
        return new RoadWorld((x, z) -> (int) Math.round(72 + 7 * RoadFuzzTest.noise(3, x / 120.0, z / 120.0)
                + 2 * RoadFuzzTest.noise(4, x / 35.0, z / 35.0)), 60);
    }

    private static RoadIndex.Source link(RoadSpec spec, String kind, int from, int to, int... xz) {
        return RoadWorld.source(spec, RoadWorld.link(kind, from, to, xz));
    }

    private static RoadIndex build(RoadWorld world, List<RoadIndex.Source> sources) {
        RoadIndex index = RoadIndex.build(world, sources, List.of(), 1L, null);
        world.rasterize(index);
        return index;
    }

    private static void star(String name, RoadSpec[] specs, double[] degrees) {
        RoadWorld world = rolling();
        List<RoadIndex.Source> sources = new ArrayList<>();
        for (int a = 0; a < specs.length; a++) {
            double angle = Math.toRadians(degrees[a]);
            sources.add(link(specs[a], "paved", 0, a + 1, 0, 0, (int) Math.round(Math.cos(angle) * 260),
                    (int) Math.round(Math.sin(angle) * 260)));
        }
        build(world, sources);
        RoadRender.plan(world, name, -56, -56, 56, 56, 72, 8);
    }

    @Test
    void junctions() {
        star("junction3", new RoadSpec[] {ROAD, ROAD, ROAD}, new double[] {5, 125, 250});
        star("junction4", new RoadSpec[] {ROAD, ROAD, ROAD, ROAD}, new double[] {10, 95, 190, 275});
        star("junction5", new RoadSpec[] {ROAD, ROAD, ROAD, ROAD, ROAD}, new double[] {0, 60, 140, 215, 290});
        star("junction_mixed", new RoadSpec[] {HIGHWAY, HIGHWAY, ROAD, TRACK}, new double[] {8, 188, 80, 300});
        star("junction_acute", new RoadSpec[] {ROAD, ROAD, ROAD}, new double[] {0, 35, 200});
    }

    @Test
    void attachmentsAndCrossings() {
        RoadWorld world = rolling();
        build(world, List.of(link(ROAD, "paved", 0, 1, -200, 0, 200, 30),
                link(TRACK, "track", -1, -1, 20, 16, 60, 150),
                link(ROAD, "paved", 2, 3, -120, -150, -60, 160)));
        RoadRender.plan(world, "tee_and_cross", -150, -60, 80, 70, 72, 6);

        world = rolling();
        RoadIndex index = build(world, List.of(link(HIGHWAY, "paved", 0, 1, -300, 0, 300, 40),
                link(ROAD, "paved", 2, 3, -40, -260, 30, 260),
                link(TRACK, "track", -1, -1, 150, 30, 190, 200)));
        RoadRender.plan(world, "highway_cross", -90, -80, 240, 90, 72, 5);
        RoadIndex.LinkGeometry road = index.link(1);
        RoadRender.elevation(world, "highway_cross_side", road, 140, 400, 0.0, 50, 100, 5);
    }

    @Test
    void structures() {
        RoadWorld world = new RoadWorld((x, z) -> {
            double valley = 30.0 * Math.exp(-Math.pow((x - 300) / 70.0, 2));
            double hill = 45.0 * Math.exp(-Math.pow((x - 700) / 60.0, 2));
            return (int) Math.round(75 - valley + hill + 0.06 * z);
        }, 58);
        RoadIndex index = build(world, List.of(link(ROAD, "paved", 0, 1, 0, 0, 1000, 0)));
        RoadIndex.LinkGeometry link = index.link(0);
        RoadRender.elevation(world, "bridge_side", link, 180, 430, 3.5, 35, 95, 5);
        RoadRender.elevation(world, "bridge_centre", link, 180, 430, 0.0, 35, 95, 5);
        RoadRender.elevation(world, "tunnel_centre", link, 560, 840, 0.0, 60, 130, 5);
        RoadRender.plan(world, "tunnel_portal", 590, -30, 680, 30, 80, 8);
        RoadRender.section(world, "section_cut", link, firstTunnel(link) - 8, 24, 60, 110, 8);
        RoadRender.plan(world, "tunnel_portal_end", firstTunnel(link) - 30, -30, firstTunnel(link) + 20, 30, 80, 8);
        RoadRender.section(world, "section_portal", link, firstTunnel(link), 24, 60, 110, 8);
        RoadRender.section(world, "section_wing", link, firstTunnel(link) - 2, 24, 60, 110, 8);
        RoadRender.section(world, "section_fill", link, 215, 24, 40, 90, 8);
        RoadRender.plan(world, "bridge_plan", 180, -30, 430, 30, 70, 5);

        RoadWorld sea = new RoadWorld((x, z) -> x > 150 && x < 550 ? 40 : 66, 62);
        RoadIndex seaIndex = build(sea, List.of(link(HIGHWAY, "paved", 0, 1, 0, 0, 700, 0)));
        RoadRender.elevation(sea, "truss_side", seaIndex.link(0), 60, 360, 6.5, 35, 110, 4);

        RoadWorld slope = new RoadWorld((x, z) -> (int) Math.round(80 + 0.35 * z), 40);
        RoadIndex slopeIndex = build(slope, List.of(link(ROAD, "paved", 0, 1, 0, 0, 100, 0, 180, 60, 300, 60)));
        RoadRender.section(slope, "section_hillside", slopeIndex.link(0), 50, 26, 60, 100, 8);
        RoadRender.plan(slope, "hillside_plan", 0, -40, 300, 100, 80, 4);
    }

    private static int firstTunnel(RoadIndex.LinkGeometry link) {
        for (int s = 0; s < link.stationCount(); s++) {
            if (link.mode(s) == RoadProfile.TUNNEL) {
                return s;
            }
        }
        return 0;
    }
}
