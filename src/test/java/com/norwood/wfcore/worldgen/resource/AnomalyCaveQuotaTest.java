package com.norwood.wfcore.worldgen.resource;

import com.norwood.wfcore.common.worldgen.resource.AnomalyCaveQuota;
import com.norwood.wfcore.common.worldgen.resource.ResourceAllocation.AnomalyCaveSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyCaveQuotaTest {
    private static AnomalyCaveSpec spec(int count, int artefacts) {
        return new AnomalyCaveSpec(-25000, -25000, 25000, 25000, count, artefacts, List.of(0.4, 0.3, 0.2, 0.1),
                List.of(1.0, 2.0, 3.0, 4.0), 0.0, Optional.empty());
    }

    private static final AnomalyCaveQuota.Land EAST_HALF = (x, z) -> x > 0;

    @Test
    void exactFieldAndArtefactTotalsOnLandSpaced() {
        for (long seed : new long[] {1L, 20261014L, -5L}) {
            for (int[] cfg : new int[][] {{40, 400}, {7, 100}, {64, 63}, {1, 5}}) {
                var slots = AnomalyCaveQuota.allocate(spec(cfg[0], cfg[1]), seed, EAST_HALF);
                assertEquals(cfg[0], slots.size());
                assertEquals(cfg[1], slots.stream().mapToInt(AnomalyCaveQuota.Slot::artefacts).sum());
                double spacing = 0.5 * Math.sqrt(50000.0 * 50000.0 / cfg[0]);
                for (var a : slots) {
                    assertTrue(a.chunkX() * 16 + 8 > 0);
                    assertTrue(a.tier() >= 1 && a.tier() <= 4);
                    for (var b : slots) {
                        if (a != b) {
                            assertTrue(Math.hypot((a.chunkX() - b.chunkX()) * 16.0, (a.chunkZ() - b.chunkZ()) * 16.0)
                                    >= spacing - 1e-6);
                        }
                    }
                }
            }
        }
    }

    @Test
    void deterministicPerSeedAndDifferentAcrossSeeds() {
        var a = AnomalyCaveQuota.allocate(spec(40, 400), 7L, EAST_HALF);
        assertEquals(a, AnomalyCaveQuota.allocate(spec(40, 400), 7L, EAST_HALF));
        assertTrue(!a.equals(AnomalyCaveQuota.allocate(spec(40, 400), 8L, EAST_HALF)));
    }

    @Test
    void higherTiersGetMore() {
        var slots = AnomalyCaveQuota.allocate(spec(60, 600), 3L, (x, z) -> true);
        for (var a : slots) {
            for (var b : slots) {
                if (a.tier() < b.tier()) {
                    assertTrue(a.artefacts() <= b.artefacts() + 1, a + " vs " + b);
                }
            }
        }
    }

    @Test
    void impossibleFailsFast() {
        assertThrows(IllegalStateException.class, () -> AnomalyCaveQuota.allocate(spec(40, 400), 1L, (x, z) -> false));
    }
}
