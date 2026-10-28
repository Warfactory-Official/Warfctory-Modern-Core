package com.norwood.wfcore.integration.wflib;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;
import com.wf.wflib.anomaly.site.AnomalySites;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * {@link ResourceSystem#ANOMALY_SITE} rules -> WFLib's anomaly site density (WFWorldGen's {@code anomalies} section):
 * each tier's sites as much likelier at a place as its rule says, in the overworld; stock (1) without a server.
 */
public final class AnomalySiteDensity {

    private AnomalySiteDensity() {}

    public static void register() {
        AnomalySites.density = (tier, x, z) -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            return server == null ? 1.0 : AllocationIndexManager.get(server.overworld())
                    .multiplier(ResourceSystem.ANOMALY_SITE, "wflib:anomaly_site_tier_" + tier, x + 0.5, z + 0.5);
        };
    }
}
