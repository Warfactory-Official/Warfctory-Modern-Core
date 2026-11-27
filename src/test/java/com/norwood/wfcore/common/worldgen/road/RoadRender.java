package com.norwood.wfcore.common.worldgen.road;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/** {@link RoadWorld} -> PNG under {@code build/road-renders/}: plan (top block per column) or elevation slice. */
final class RoadRender {

    private static final int[] PART_COLOURS = new int[RoadRasterizer.Part.values().length];

    static {
        colour(RoadRasterizer.Part.SURFACE, 0x5A5F66);
        colour(RoadRasterizer.Part.EDGE, 0xB9BCC0);
        colour(RoadRasterizer.Part.MARKING, 0xFFFFFF);
        colour(RoadRasterizer.Part.BASE, 0x6E6E6E);
        colour(RoadRasterizer.Part.EMBANKMENT, 0x8A6A45);
        colour(RoadRasterizer.Part.BRIDGE_DECK, 0x4A5560);
        colour(RoadRasterizer.Part.STRUCTURE, 0x9A8F80);
        colour(RoadRasterizer.Part.ACCENT, 0xD8D2C4);
        colour(RoadRasterizer.Part.RAILING, 0xE0A030);
        colour(RoadRasterizer.Part.TRUSS, 0x303848);
        colour(RoadRasterizer.Part.WALL, 0x7A5C8A);
        colour(RoadRasterizer.Part.TUNNEL_FLOOR, 0x3A3A44);
        colour(RoadRasterizer.Part.CEILING, 0x504060);
        colour(RoadRasterizer.Part.LIGHT, 0xFFF080);
        colour(RoadRasterizer.Part.GUARD_RAIL, 0xFF5020);
        colour(RoadRasterizer.Part.LAMP_POST, 0x20C0FF);
        colour(RoadRasterizer.Part.LAMP_LIGHT, 0xFFFF00);
        colour(RoadRasterizer.Part.SIGN, 0xFF00FF);
    }

    private RoadRender() {}

    private static void colour(RoadRasterizer.Part part, int rgb) {
        PART_COLOURS[part.ordinal()] = rgb;
    }

    private static int colour(RoadWorld world, int x, int y, int z, int reference) {
        byte cell = world.get(x, y, z);
        int rgb;
        if (cell >= RoadWorld.PART_BASE) {
            rgb = PART_COLOURS[cell - RoadWorld.PART_BASE];
        } else {
            rgb = switch (cell) {
                case RoadWorld.STONE -> 0x4E7F3A;
                case RoadWorld.TURF -> 0x86C050;
                case RoadWorld.WATER -> 0x3060C0;
                case RoadWorld.LEAVES -> 0x1F5F1F;
                case RoadWorld.STALK -> 0x80A020;
                case RoadWorld.BEDROCK_ID -> 0x101010;
                default -> 0xC8E0F0;
            };
        }
        double shade = Math.clamp(1.0 + (y - reference) * 0.035, 0.45, 1.5);
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * shade);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * shade);
        int b = (int) Math.min(255, (rgb & 255) * shade);
        return (r << 16) | (g << 8) | b;
    }

    /** Top block per column, shaded by height against {@code reference}. */
    static void plan(RoadWorld world, String name, int minX, int minZ, int maxX, int maxZ, int reference,
            int scale) {
        BufferedImage image = new BufferedImage((maxX - minX + 1) * scale, (maxZ - minZ + 1) * scale,
                BufferedImage.TYPE_INT_RGB);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int y = world.top(x, z);
                fill(image, (x - minX) * scale, (z - minZ) * scale, scale, colour(world, x, y, z, reference));
            }
        }
        write(image, name);
    }

    /** Blocks along the polyline of stations {@code [from, to]} of a link, offset sideways, y up. */
    static void elevation(RoadWorld world, String name, RoadIndex.LinkGeometry link, int from, int to,
            double offset, int minY, int maxY, int scale) {
        BufferedImage image = new BufferedImage((to - from + 1) * scale, (maxY - minY + 1) * scale,
                BufferedImage.TYPE_INT_RGB);
        for (int s = from; s <= to; s++) {
            int lo = Math.max(0, s - 1);
            int hi = Math.min(link.stationCount() - 1, s + 1);
            double dx = link.x(hi) - link.x(lo);
            double dz = link.z(hi) - link.z(lo);
            double length = Math.max(1e-9, Math.sqrt(dx * dx + dz * dz));
            int x = (int) Math.floor(link.x(s) - dz / length * offset);
            int z = (int) Math.floor(link.z(s) + dx / length * offset);
            for (int y = minY; y <= maxY; y++) {
                fill(image, (s - from) * scale, (maxY - y) * scale, scale, colour(world, x, y, z, y));
            }
        }
        write(image, name);
    }

    /** Cross-section through station {@code station}, {@code half} blocks each side. */
    static void section(RoadWorld world, String name, RoadIndex.LinkGeometry link, int station, int half,
            int minY, int maxY, int scale) {
        BufferedImage image = new BufferedImage((half * 2 + 1) * scale, (maxY - minY + 1) * scale,
                BufferedImage.TYPE_INT_RGB);
        int lo = Math.max(0, station - 1);
        int hi = Math.min(link.stationCount() - 1, station + 1);
        double dx = link.x(hi) - link.x(lo);
        double dz = link.z(hi) - link.z(lo);
        double length = Math.max(1e-9, Math.sqrt(dx * dx + dz * dz));
        for (int o = -half; o <= half; o++) {
            int x = (int) Math.floor(link.x(station) - dz / length * o);
            int z = (int) Math.floor(link.z(station) + dx / length * o);
            for (int y = minY; y <= maxY; y++) {
                fill(image, (o + half) * scale, (maxY - y) * scale, scale, colour(world, x, y, z, y));
            }
        }
        write(image, name);
    }

    private static void fill(BufferedImage image, int px, int py, int scale, int rgb) {
        for (int a = 0; a < scale; a++) {
            for (int b = 0; b < scale; b++) {
                image.setRGB(px + a, py + b, rgb);
            }
        }
    }

    private static void write(BufferedImage image, String name) {
        File file = new File("build/road-renders/" + name + ".png");
        file.getParentFile().mkdirs();
        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
