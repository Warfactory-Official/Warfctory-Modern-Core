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
 * Owns one {@link RoadIndex} per level and decides when it gets built.
 *
 * <p>Building means probing this world's terrain along every road, which is thousands of noise columns and takes
 * real time, so it starts on a background thread the moment a level loads rather than on whichever chunk happens
 * to hit a road first. A generator thread that arrives before the build finishes blocks on it. That is deliberate:
 * generating the chunk without its road would leave a permanent gap in the network, and there is no second chance
 * to fix it once the chunk is saved.
 */
public final class RoadIndexManager {

    public static final RoadIndexManager INSTANCE = new RoadIndexManager();

    /**
     * A thread of its own rather than {@link net.minecraft.Util#backgroundExecutor()}. The generation threads
     * that wait on this build run on that same pool, so scheduling the build there means the work everyone is
     * blocked on is queued behind the blocked work — survivable only because ForkJoinPool compensates by
     * spawning threads, which is not a mechanism to rely on.
     */
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
            indexes.remove(level.dimension());
        }
    }

    private CompletableFuture<RoadIndex> indexFor(ServerLevel level) {
        return indexes.computeIfAbsent(level.dimension(),
                key -> CompletableFuture.supplyAsync(() -> build(level), BUILDER));
    }

    /** Cache location, alongside the world's other saved data and named for the dimension it belongs to. */
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
                WFCore.LOGGER.info("Roads in {}: {} link(s), {} station(s), solved in {} ms",
                        level.dimension().location(), index.linkCount(), index.stationCount(),
                        (System.nanoTime() - started) / 1_000_000L);
            }
            return index;
        } catch (Exception e) {
            // A thrown index would be re-thrown into every chunk that touches a road; an empty one just means no
            // roads, which is recoverable by fixing the pack and reloading.
            WFCore.LOGGER.error("Failed to build the road index for {}", level.dimension().location(), e);
            return RoadIndex.EMPTY;
        }
    }
}
