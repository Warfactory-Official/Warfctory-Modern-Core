package com.norwood.wfcore.integration.warforge;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.flansmod.warforge.api.WarforgeAPI;
import com.flansmod.warforge.api.vein.VeinSample;
import com.flansmod.warforge.api.vein.VeinView;
import com.flansmod.warforge.api.vein.VeinWeightProvider;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** {@link ResourceSystem#WARFORGE_VEIN} rules -> WarForge's vein roll. Behind {@link WarforgeIntegration#isLoaded()}. */
public final class WarforgeVeinAllocation {

    private WarforgeVeinAllocation() {}

    public static void install() {
        WarforgeAPI.setVeinWeightProvider(new VeinWeightProvider() {

            @Override
            public ToDoubleFunction<VeinView> multipliers(ResourceKey<Level> dim, int chunkX, int chunkZ) {
                return WarforgeVeinAllocation.multipliers(dim, chunkX, chunkZ);
            }

            @Override
            public Assignment assigned(ResourceKey<Level> dim, int chunkX, int chunkZ) {
                AllocationIndex index = index(dim);
                List<AllocationIndex.Pick> picks = index == null ? null
                        : index.picks(ResourceSystem.WARFORGE_VEIN, chunkX, chunkZ);
                if (picks == null) {
                    return null;
                }
                AllocationIndex.Pick pick = picks.getFirst();
                return new Assignment(pick.target().isEmpty() ? null : WarforgeAPI.findVein(pick.target()),
                        pick.richness());
            }

            @Override
            public double richness(ResourceKey<Level> dim, int chunkX, int chunkZ, VeinView vein) {
                AllocationIndex index = index(dim);
                return index == null ? 1.0 : index.richness(ResourceSystem.WARFORGE_VEIN, vein.key(),
                        (chunkX << 4) + 8.0, (chunkZ << 4) + 8.0);
            }
        });
    }

    @Nullable
    private static AllocationIndex index(ResourceKey<Level> dim) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.getLevel(dim);
        return level == null ? null : AllocationIndexManager.get(level);
    }

    public static boolean knows(String translationKey) {
        VeinView vein = WarforgeAPI.findVein(translationKey);
        return vein != null && vein.key().equals(translationKey);
    }

    public static String rolled(ServerLevel level, int chunkX, int chunkZ) {
        if (WarforgeAPI.veins().isEmpty()) {
            return "unloaded";
        }
        VeinSample sample = WarforgeAPI.veinAt(level, chunkX, chunkZ);
        return sample == null ? "none" : sample.vein().key() + " " + sample.quality();
    }

    /** {@code [{id: translation key, dimensions: {dim: weight 0..1}}]} for the resource catalog. */
    public static JsonArray catalog() {
        JsonArray out = new JsonArray();
        WarforgeAPI.veins().stream()
                .sorted(Comparator.comparing(VeinView::key))
                .forEach(vein -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", vein.key());
                    JsonObject dims = new JsonObject();
                    vein.dimWeights().entrySet().stream()
                            .sorted(Comparator.comparing(e -> e.getKey().location().toString()))
                            .forEach(e -> dims.addProperty(e.getKey().location().toString(), e.getValue()));
                    o.add("dimensions", dims);
                    out.add(o);
                });
        return out;
    }

    @Nullable
    private static ToDoubleFunction<VeinView> multipliers(ResourceKey<Level> dim, int chunkX, int chunkZ) {
        AllocationIndex index = index(dim);
        if (index == null || index.weightRules(ResourceSystem.WARFORGE_VEIN).isEmpty()) {
            return null;
        }
        double x = (chunkX << 4) + 8.0;
        double z = (chunkZ << 4) + 8.0;
        Map<String, Double> local = new HashMap<>();
        boolean stock = true;
        for (VeinView vein : WarforgeAPI.veins()) {
            double m = index.multiplier(ResourceSystem.WARFORGE_VEIN, vein.key(), x, z);
            // richness only reaches the quality through the weighted roll
            stock &= m == 1.0 && index.richness(ResourceSystem.WARFORGE_VEIN, vein.key(), x, z) == 1.0;
            local.put(vein.key(), m);
        }
        return stock ? null : vein -> local.getOrDefault(vein.key(), 1.0);
    }
}
