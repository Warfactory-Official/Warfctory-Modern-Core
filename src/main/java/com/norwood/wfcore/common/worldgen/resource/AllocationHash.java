package com.norwood.wfcore.common.worldgen.resource;

import net.minecraft.world.level.levelgen.RandomSupport;

import java.nio.charset.StandardCharsets;

/** Per-cell draws owned by wfcore, mirrored bit for bit by {@code worldgen-workspace} allocation.go. */
public final class AllocationHash {

    private AllocationHash() {}

    public static long hash(long seed, long salt, int a, int b) {
        return RandomSupport.mixStafford13(RandomSupport.mixStafford13(RandomSupport.mixStafford13(seed ^ salt) + a) + b);
    }

    /** FNV-1a 64 over UTF-8. */
    public static long salt(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xFF;
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** [0, 1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    public static double dist(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Worley cell over a {@code size}-block grid: {nearest cell's uniform value, (d2 - d1) / 2}. */
    public static double[] cell(long seed, long salt, double size, double jitter, double x, double z) {
        int ix = (int) Math.floor(x / size);
        int iz = (int) Math.floor(z / size);
        double d1 = Double.POSITIVE_INFINITY;
        double d2 = Double.POSITIVE_INFINITY;
        double v = 0.0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = hash(seed, salt, ix + dx, iz + dz);
                double px = (ix + dx + 0.5 + jitter * (unit(RandomSupport.mixStafford13(h ^ 1)) - 0.5)) * size;
                double pz = (iz + dz + 0.5 + jitter * (unit(RandomSupport.mixStafford13(h ^ 2)) - 0.5)) * size;
                double d = dist(x - px, z - pz);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                    v = unit(h);
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
        return new double[] { v, (d2 - d1) * 0.5 };
    }
}
