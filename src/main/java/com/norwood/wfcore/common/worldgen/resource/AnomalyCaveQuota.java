package com.norwood.wfcore.common.worldgen.resource;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed allotment of WFLib cave fields: a pure function of seed and spec, so every chunk order and every restart
 * sees the same fields and the same artefact total. Fields by dart-throwing hashed chunks over the rectangle: land
 * only, spaced; the artefact total split by largest remainder over tier shares. Go preview: none (gap).
 */
public final class AnomalyCaveQuota {

    /** Field start chunk, tier 1-4, its artefacts. */
    public record Slot(int index, int chunkX, int chunkZ, int tier, int artefacts, long seed) {}

    @FunctionalInterface
    public interface Land {

        boolean at(double x, double z);
    }

    private static final int TRIES_PER_FIELD = 4000;

    private AnomalyCaveQuota() {}

    public static List<Slot> allocate(ResourceAllocation.AnomalyCaveSpec spec, long worldSeed, Land land) {
        int x1 = Math.min(spec.minX(), spec.maxX());
        int x2 = Math.max(spec.minX(), spec.maxX());
        int z1 = Math.min(spec.minZ(), spec.maxZ());
        int z2 = Math.max(spec.minZ(), spec.maxZ());
        int cx1 = x1 >> 4;
        int cz1 = z1 >> 4;
        int w = (x2 >> 4) - cx1 + 1;
        int h = (z2 >> 4) - cz1 + 1;
        double spacing = spec.spacing() > 0 ? spec.spacing()
                : 0.5 * Math.sqrt((double) (x2 - x1) * (z2 - z1) / Math.max(1, spec.count()));
        long salt = ((long) x1 * 73856093L) ^ ((long) z1 * 19349663L) ^ ((long) x2 * 83492791L)
                ^ ((long) z2 * 49979687L) ^ ((long) spec.count() * 1610612741L);
        long base = mix(worldSeed ^ salt ^ 0x41C64E6DL);

        List<int[]> chunks = new ArrayList<>(spec.count());
        long tries = (long) spec.count() * TRIES_PER_FIELD;
        for (long k = 0; chunks.size() < spec.count(); k++) {
            if (k >= tries) {
                throw new IllegalStateException("anomaly_caves: only " + chunks.size() + " of " + spec.count()
                        + " fields fit (spacing " + spacing + ", land)");
            }
            long hash = mix(base + k * 0x9E3779B97F4A7C15L);
            int cx = cx1 + (int) Math.floorMod(hash, (long) w);
            int cz = cz1 + (int) Math.floorMod(hash >>> 24, (long) h);
            double bx = cx * 16 + 8.0;
            double bz = cz * 16 + 8.0;
            boolean clear = true;
            for (int[] c : chunks) {
                double dx = c[0] * 16 + 8.0 - bx;
                double dz = c[1] * 16 + 8.0 - bz;
                if (dx * dx + dz * dz < spacing * spacing) {
                    clear = false;
                    break;
                }
            }
            if (clear && land.at(bx, bz)) {
                chunks.add(new int[] {cx, cz});
            }
        }

        int[] tier = new int[spec.count()];
        double tierTotal = spec.tiers().stream().mapToDouble(Double::doubleValue).sum();
        double shareTotal = 0.0;
        for (int i = 0; i < tier.length; i++) {
            double u = Mth.clamp(unit(mix(base ^ (i + 1) * 0xC2B2AE3D27D4EB4FL)), 0.0, 1.0 - 1e-12) * tierTotal;
            int t = 0;
            double acc = spec.tiers().get(0);
            while (u >= acc && t < 3) {
                acc += spec.tiers().get(++t);
            }
            tier[i] = t + 1;
            shareTotal += spec.tierShare().get(t);
        }
        int[] artefacts = new int[tier.length];
        double[] frac = new double[tier.length];
        int given = 0;
        for (int i = 0; i < tier.length; i++) {
            double exact = spec.artefacts() * spec.tierShare().get(tier[i] - 1) / shareTotal;
            artefacts[i] = (int) Math.floor(exact);
            frac[i] = exact - artefacts[i];
            given += artefacts[i];
        }
        for (int left = spec.artefacts() - given; left > 0; left--) {
            int best = 0;
            for (int i = 1; i < frac.length; i++) {
                if (frac[i] > frac[best]) {
                    best = i;
                }
            }
            artefacts[best]++;
            frac[best] = -1.0;
        }
        List<Slot> out = new ArrayList<>(tier.length);
        for (int i = 0; i < tier.length; i++) {
            out.add(new Slot(i, chunks.get(i)[0], chunks.get(i)[1], tier[i], artefacts[i],
                    mix(base ^ (i + 1) * 0x94D049BB133111EBL)));
        }
        return out;
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
