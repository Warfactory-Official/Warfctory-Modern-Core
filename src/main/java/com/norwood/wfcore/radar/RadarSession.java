package com.norwood.wfcore.radar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

import com.norwood.wfcore.radar.data.CalibratorData;
import com.norwood.wfcore.radar.data.RadarRegistryData;
import org.jetbrains.annotations.Nullable;

import java.util.Random;


public final class RadarSession {

    private static final int DIST_STEP = 100;
    private static final double JITTER_FRACTION = 0.4;

    private RadarSession() {}

    @Nullable
    public static long[] generate(ServerLevel level, BlockPos radar, int n) {
        if (n <= 0) {
            return null;
        }
        WorldBorder border = level.getWorldBorder();
        Random rng = new Random();
        double base = rng.nextDouble() * Math.PI * 2;
        double sector = (Math.PI * 2) / n;
        int min = RadarConfig.getCalibratorMinDistance();
        int max = RadarConfig.getCalibratorMaxDistance();
        double margin = RadarConfig.getCalibratorBorderMargin();

        long[] targets = new long[n];
        for (int i = 0; i < n; i++) {
            double angle = base + i * sector + (rng.nextDouble() - 0.5) * sector * JITTER_FRACTION;
            double startDist = min + rng.nextDouble() * (max - min);
            boolean placed = false;
            for (double d = startDist; d >= min; d -= DIST_STEP) {
                double tx = radar.getX() + Math.cos(angle) * d;
                double tz = radar.getZ() + Math.sin(angle) * d;
                if (insideBorder(border, tx, tz, margin)) {
                    targets[i] = RadarRegistryData.pack((int) Math.round(tx), (int) Math.round(tz));
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                return null;
            }
        }
        return targets;
    }

    public static int placedMask(ServerLevel level, long[] targets, int tol) {
        CalibratorData data = CalibratorData.get(level);
        int mask = 0;
        for (int i = 0; i < targets.length; i++) {
            if (data.hasWithin(unpackX(targets[i]), unpackZ(targets[i]), tol)) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackZ(long packed) {
        return (int) packed;
    }

    private static boolean insideBorder(WorldBorder border, double x, double z, double margin) {
        return x >= border.getMinX() + margin && x <= border.getMaxX() - margin
                && z >= border.getMinZ() + margin && z <= border.getMaxZ() - margin;
    }
}
