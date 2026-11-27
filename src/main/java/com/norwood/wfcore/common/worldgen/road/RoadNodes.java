package com.norwood.wfcore.common.worldgen.road;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Plan-view meeting points of pieces, before any height: network junctions of 3+ ends (flat pad), T attachments
 * and at-grade crossings (lesser road conforms to the major surface). Each is a set of arms leaving a centre,
 * with a fillet in every corner narrower than {@link #MAX_FILLET_ANGLE}.
 *
 * <p>Angles are {@code atan2(dz, dx)}: increasing = clockwise on a map with z down.
 */
final class RoadNodes {

    static final double MAX_FILLET_ANGLE = Math.toRadians(150.0);
    static final double MIN_FILLET_ANGLE = Math.toRadians(12.0);
    /** Fillet tangent length cap => radius shrinks in acute corners. */
    static final double MAX_TANGENT = 12.0;
    static final double MIN_RADIUS = 1.5;
    static final int MAX_THROAT = 64;
    private static final int DIRECTION_SPAN = 10;

    static final class Arm {

        final RoadIndex.LinkGeometry link;
        final int index;
        final int station;
        /** +1: leaves the node along increasing stations. */
        final int dir;
        /** Keeps its markings through the pad; no stop line. */
        boolean major;
        final double angle;
        /** Stations from {@link #station} to the pad edge. */
        int throat;

        Arm(RoadIndex.LinkGeometry link, int index, int station, int dir, boolean major) {
            this.link = link;
            this.index = index;
            this.station = station;
            this.dir = dir;
            this.major = major;
            int far = Math.clamp(station + (long) dir * DIRECTION_SPAN, 0, link.stationCount() - 1);
            this.angle = Math.atan2(link.z(far) - link.z(station), link.x(far) - link.x(station));
        }

        /** Stations past the node, -1 off this arm. */
        int along(int at) {
            int t = (at - station) * dir;
            return t >= 0 && t <= throat ? t : -1;
        }
    }

    /** Wedge from arm {@code a} to the next arm {@code b} by angle. */
    record Corner(int a, int b, double radius, double cos, double sinSquared, double limit, double shoulder) {}

    static final class Node {

        final double x;
        final double z;
        final Arm[] arms;
        final Corner[] corners;
        /** One height across the pad. */
        final boolean flat;
        /** Link whose resolution decides {@link #active}; -1 = always. */
        final int owner;
        final double radius;
        volatile boolean active;

        Node(double x, double z, Arm[] arms, boolean flat, int owner) {
            this.x = x;
            this.z = z;
            this.flat = flat;
            this.owner = owner;
            this.active = owner < 0;
            Arrays.sort(arms, Comparator.comparingDouble(arm -> arm.angle));
            this.arms = arms;
            int n = arms.length;
            if (flat) {
                through(arms);
            }
            List<Corner> found = new ArrayList<>(n);
            double reach = 0.0;
            for (Arm arm : arms) {
                for (Arm other : arms) {
                    if (other.link != arm.link) {
                        arm.throat = Math.max(arm.throat, (int) Math.ceil(other.link.spec().outerHalfWidth()) + 1);
                    }
                }
            }
            for (int k = 0; k < n && n > 1; k++) {
                Arm a = arms[k];
                Arm b = arms[(k + 1) % n];
                if (a.link == b.link) {
                    continue;
                }
                double theta = b.angle - a.angle;
                if (theta <= 0.0) {
                    theta += Math.PI * 2.0;
                }
                if (theta < MIN_FILLET_ANGLE || theta > MAX_FILLET_ANGLE) {
                    continue;
                }
                RoadSpec sa = a.link.spec();
                RoadSpec sb = b.link.spec();
                double ha = sa.outerHalfWidth();
                double hb = sb.outerHalfWidth();
                double sin = Math.sin(theta);
                double cos = Math.cos(theta);
                double tan = Math.tan(theta / 2.0);
                double radius = Math.min(Math.min(sa.cornerRadius(), sb.cornerRadius()), MAX_TANGENT * tan);
                radius = Math.min(radius, Math.min(sa.clearedHalfWidth() - ha, sb.clearedHalfWidth() - hb) - 0.5);
                double tangent = 0.0;
                if (radius >= MIN_RADIUS) {
                    tangent = radius / tan;
                    found.add(new Corner(k, (k + 1) % n, radius, cos, sin * sin,
                            radius / Math.sin(theta / 2.0) + 2.0, Math.max(sa.shoulder(), sb.shoulder())));
                    reach = Math.max(reach, radius);
                }
                a.throat = Math.max(a.throat, (int) Math.ceil((hb + ha * cos) / sin + tangent) + 1);
                b.throat = Math.max(b.throat, (int) Math.ceil((ha + hb * cos) / sin + tangent) + 1);
            }
            double radius = 0.0;
            for (Arm arm : arms) {
                int available = arm.dir > 0 ? arm.link.stationCount() - 1 - arm.station : arm.station;
                boolean through = false;
                for (Arm other : arms) {
                    through |= other != arm && other.link == arm.link;
                }
                // An end arm keeps the far half of its link for the node at the other end.
                arm.throat = Math.clamp(arm.throat, 0, Math.min(MAX_THROAT, through ? available : available / 2));
                radius = Math.max(radius, arm.throat + arm.link.spec().outerHalfWidth() + reach + 2.0);
            }
            this.corners = found.toArray(Corner[]::new);
            this.radius = radius;
        }

        /** The arm of {@code link} leaving along {@code dir}. */
        Arm end(RoadIndex.LinkGeometry link, int dir) {
            for (Arm arm : arms) {
                if (arm.link == link && arm.dir == dir) {
                    return arm;
                }
            }
            throw new IllegalArgumentException("link has no arm at this node");
        }

        /** The straightest pair of the most important arms runs through: no stop lines, markings kept. */
        private static void through(Arm[] arms) {
            Arm first = null;
            Arm second = null;
            double best = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < arms.length; i++) {
                for (int j = i + 1; j < arms.length; j++) {
                    double between = Math.abs(arms[j].angle - arms[i].angle);
                    between = Math.min(between, Math.PI * 2.0 - between);
                    if (between < MAX_FILLET_ANGLE) {
                        continue;
                    }
                    int a = arms[i].link.spec().priority();
                    int b = arms[j].link.spec().priority();
                    double score = Math.min(a, b) * 1.0e6 + Math.max(a, b) * 1.0e3 + between;
                    if (score > best) {
                        best = score;
                        first = arms[i];
                        second = arms[j];
                    }
                }
            }
            if (first != null) {
                first.major = true;
                second.major = true;
            }
        }
    }

    final List<Node> nodes = new ArrayList<>();
    /** Per piece: node at station 0 / at the last station, null when none. */
    final Node[] start;
    final Node[] end;
    /** Per piece, parallel to its crossing triples. */
    final Node[][] crossing;
    private final Long2ObjectMap<Node[]> byChunk = new Long2ObjectOpenHashMap<>();

    RoadNodes(List<RoadTopology.Piece> pieces, List<RoadIndex.LinkGeometry> links,
            Long2ObjectMap<IntArrayList> junctionEnds) {
        int n = pieces.size();
        start = new Node[n];
        end = new Node[n];
        crossing = new Node[n][];
        for (Long2ObjectMap.Entry<IntArrayList> entry : junctionEnds.long2ObjectEntrySet()) {
            IntArrayList ends = entry.getValue();
            if (ends.size() < 3) {
                continue;
            }
            Arm[] arms = new Arm[ends.size()];
            double x = 0.0;
            double z = 0.0;
            for (int k = 0; k < arms.length; k++) {
                int index = ends.getInt(k) >> 1;
                boolean atEnd = (ends.getInt(k) & 1) != 0;
                RoadIndex.LinkGeometry link = links.get(index);
                int station = atEnd ? link.stationCount() - 1 : 0;
                arms[k] = new Arm(link, index, station, atEnd ? -1 : 1, false);
                x += link.x(station) / arms.length;
                z += link.z(station) / arms.length;
            }
            Node node = add(new Node(x, z, arms, true, -1));
            for (Arm arm : node.arms) {
                if (arm.dir > 0) {
                    start[arm.index] = node;
                } else {
                    end[arm.index] = node;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            RoadTopology.Piece piece = pieces.get(i);
            RoadIndex.LinkGeometry link = links.get(i);
            if (piece.startAttach >= 0) {
                start[i] = add(through(link, i, 0, false, links.get(piece.startAttach), piece.startAttach,
                        piece.startAttachAt, -1));
            }
            if (piece.endAttach >= 0) {
                end[i] = add(through(link, i, link.stationCount() - 1, false, links.get(piece.endAttach),
                        piece.endAttach, piece.endAttachAt, -1));
            }
            int count = piece.crossings.size() / 3;
            crossing[i] = new Node[count];
            for (int c = 0; c < count; c++) {
                int partner = piece.crossings.getInt(c * 3 + 1);
                int partnerStation = piece.crossings.getInt(c * 3 + 2);
                crossing[i][c] = add(through(link, i, piece.crossings.getInt(c * 3), true, links.get(partner),
                        partner, partnerStation, i));
            }
        }
        Long2ObjectMap<List<Node>> building = new Long2ObjectOpenHashMap<>();
        for (Node node : nodes) {
            int minX = (int) Math.floor(node.x - node.radius) >> 4;
            int maxX = (int) Math.floor(node.x + node.radius) >> 4;
            int minZ = (int) Math.floor(node.z - node.radius) >> 4;
            int maxZ = (int) Math.floor(node.z + node.radius) >> 4;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    building.computeIfAbsent(RoadIndex.chunkKey(cx, cz), key -> new ArrayList<>(2)).add(node);
                }
            }
        }
        for (Long2ObjectMap.Entry<List<Node>> entry : building.long2ObjectEntrySet()) {
            byChunk.put(entry.getLongKey(), entry.getValue().toArray(Node[]::new));
        }
    }

    private Node add(Node node) {
        nodes.add(node);
        return node;
    }

    /** {@code lesser} meets (one arm) or crosses (two arms) {@code major} at {@code majorStation}. */
    private static Node through(RoadIndex.LinkGeometry lesser, int lesserIndex, int station, boolean crosses,
            RoadIndex.LinkGeometry major, int majorIndex, int majorStation, int owner) {
        List<Arm> arms = new ArrayList<>(4);
        if (station < lesser.stationCount() - 1) {
            arms.add(new Arm(lesser, lesserIndex, station, 1, false));
        }
        if (station > 0 && (crosses || arms.isEmpty())) {
            arms.add(new Arm(lesser, lesserIndex, station, -1, false));
        }
        if (majorStation < major.stationCount() - 1) {
            arms.add(new Arm(major, majorIndex, majorStation, 1, true));
        }
        if (majorStation > 0) {
            arms.add(new Arm(major, majorIndex, majorStation, -1, true));
        }
        return new Node(major.x(majorStation), major.z(majorStation), arms.toArray(Arm[]::new), false, owner);
    }

    Node[] near(int chunkX, int chunkZ) {
        return byChunk.get(RoadIndex.chunkKey(chunkX, chunkZ));
    }
}
