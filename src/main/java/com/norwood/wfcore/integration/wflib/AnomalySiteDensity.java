package com.norwood.wfcore.integration.wflib;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.AnomalyCaveQuota;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;
import com.wf.wflib.anomaly.site.AnomalyCaves;
import com.wf.wflib.anomaly.site.AnomalySites;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * WFWorldGen's {@code anomalies} section into WFLib, from the overworld's {@link AllocationIndex}:
 * {@link ResourceSystem#ANOMALY_SITE} rules -> site density (each tier's sites as much likelier at a place as its rule
 * says; stock (1) without a server); {@code anomaly_caves} -> {@link AnomalyCaves#allocation} (no server => none).
 */
public final class AnomalySiteDensity {

    private AnomalySiteDensity() {}

    @Nullable
    private static AllocationIndex index() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : AllocationIndexManager.get(server.overworld());
    }

    private static AnomalyCaves.Slot slot(AnomalyCaveQuota.Slot s, int count, long worldSeed) {
        return new AnomalyCaves.Slot(s.index(), count, s.chunkX(), s.chunkZ(), s.tier(), s.artefacts(), s.seed(),
                worldSeed);
    }

    public static void register() {
        AnomalySites.density = (tier, x, z) -> {
            AllocationIndex index = index();
            return index == null ? 1.0 : index.multiplier(ResourceSystem.ANOMALY_SITE,
                    "wflib:anomaly_site_tier_" + tier, x + 0.5, z + 0.5);
        };
        AnomalyCaves.allocation = new AnomalyCaves.Allocation() {
            @Override
            public AnomalyCaves.Slot at(int chunkX, int chunkZ) {
                AllocationIndex index = index();
                AnomalyCaveQuota.Slot s = index == null ? null : index.caveSlotAt(chunkX, chunkZ);
                return s == null ? null : slot(s, index.caveSlots().size(), ServerLifecycleHooks.getCurrentServer()
                        .overworld().getSeed());
            }

            @Override
            public List<AnomalyCaves.Slot> slots() {
                AllocationIndex index = index();
                if (index == null) {
                    return List.of();
                }
                long seed = ServerLifecycleHooks.getCurrentServer().overworld().getSeed();
                return index.caveSlots().stream().map(s -> slot(s, index.caveSlots().size(), seed)).toList();
            }

            @Override
            public boolean any() {
                AllocationIndex index = index();
                return index != null && !index.caveSlots().isEmpty();
            }
        };
    }
}
