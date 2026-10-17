package com.norwood.wfcore.common.worldgen.resource;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import com.norwood.wfcore.WFCore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One {@link AllocationIndex} per level. Built on load; compile failure => load fails (wrong world otherwise). */
public final class AllocationIndexManager {

    public static final AllocationIndexManager INSTANCE = new AllocationIndexManager();

    private final Map<ResourceKey<Level>, AllocationIndex> indexes = new ConcurrentHashMap<>();

    private AllocationIndexManager() {}

    public static AllocationIndex get(ServerLevel level) {
        AllocationIndex index = INSTANCE.indexes.get(level.dimension());
        return index != null ? index : INSTANCE.indexes.computeIfAbsent(level.dimension(), k -> build(level));
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            get(level);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            indexes.remove(level.dimension());
        }
    }

    private static AllocationIndex build(ServerLevel level) {
        long started = System.nanoTime();
        AllocationIndex index = AllocationIndex.build(level);
        if (!index.isEmpty()) {
            WFCore.LOGGER.info("Resource allocation in {}: {} file(s), {} deposit node(s), {} deposit region(s), "
                    + "compiled in {} ms", level.dimension().location(), index.sources(),
                    index.depositNodes().size(), index.depositRegions().size(),
                    (System.nanoTime() - started) / 1_000_000L);
        }
        return index;
    }
}
