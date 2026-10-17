package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import com.norwood.wfcore.WFCore;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * One {@link RoadIndex} per level, built off-thread at level load (geometry + cache read; terrain probes are lazy,
 * per link). Generator threads reaching a road before the build ends block on it: skipping => permanent road gap.
 */
public final class RoadIndexManager {

    public static final RoadIndexManager INSTANCE = new RoadIndexManager();

    // Not Util.backgroundExecutor(): waiters run on that pool.
    private static final Executor BUILDER = command -> {
        Thread thread = new Thread(command, "wfcore-road-index");
        thread.setDaemon(true);
        thread.start();
    };

    private final Map<ResourceKey<Level>, CompletableFuture<RoadIndex>> indexes = new ConcurrentHashMap<>();

    private RoadIndexManager() {}

    /** The index for this level, waiting for the background build if it is still running. */
    public static RoadIndex get(ServerLevel level) {
        return INSTANCE.indexFor(level).join();
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            indexFor(level);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CompletableFuture<RoadIndex> index = indexes.remove(level.dimension());
            if (index != null && index.isDone() && !index.isCompletedExceptionally()) {
                index.join().flush();
            }
        }
    }

    private CompletableFuture<RoadIndex> indexFor(ServerLevel level) {
        return indexes.computeIfAbsent(level.dimension(),
                key -> CompletableFuture.supplyAsync(() -> build(level), BUILDER));
    }

    private static Path cacheFile(ServerLevel level) {
        String dimension = level.dimension().location().toString().replace(':', '_').replace('/', '_');
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve("wfcore_road_terrain_" + dimension + ".bin");
    }

    private static RoadIndex build(ServerLevel level) {
        Optional<Registry<RoadNetwork>> networks = level.registryAccess().registry(RoadRegistries.ROAD_NETWORK);
        Optional<Registry<RoadType>> types = level.registryAccess().registry(RoadRegistries.ROAD_TYPE);
        if (networks.isEmpty() || types.isEmpty() || networks.get().size() == 0) {
            return RoadIndex.EMPTY;
        }
        try {
            long started = System.nanoTime();
            RoadIndex index = RoadIndex.build(level.dimension(),
                    level.getChunkSource().getGenerator(),
                    level.getChunkSource().randomState(),
                    level,
                    networks.get(),
                    types.get(),
                    level.getSeed(),
                    cacheFile(level));
            if (!index.isEmpty()) {
                WFCore.LOGGER.info("Roads in {}: {} link(s), {} station(s), indexed in {} ms; terrain probed lazily",
                        level.dimension().location(), index.linkCount(), index.stationCount(),
                        (System.nanoTime() - started) / 1_000_000L);
            }
            return index;
        } catch (Exception e) {
            WFCore.LOGGER.error("Failed to build the road index for {}", level.dimension().location(), e);
            return RoadIndex.EMPTY;
        }
    }
}
