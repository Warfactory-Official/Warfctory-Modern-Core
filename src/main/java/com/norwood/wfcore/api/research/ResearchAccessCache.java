package com.norwood.wfcore.api.research;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ResearchAccessCache {

    public record ChunkKey(ResourceKey<Level> dimension, long chunkPos) {}

    private static final class Entry {

        final Map<String, Boolean> results = new ConcurrentHashMap<>();
        volatile Set<ChunkKey> watched = Set.of();
    }

    private static final Map<UUID, Entry> BY_FACTION = new ConcurrentHashMap<>();

    private static final Map<ChunkKey, UUID> WATCHED = new ConcurrentHashMap<>();

    private ResearchAccessCache() {}

    public static Boolean peek(UUID faction, String researchId) {
        Entry entry = BY_FACTION.get(faction);
        return entry == null ? null : entry.results.get(researchId);
    }

    public static void record(UUID faction, String researchId, boolean value,
                              Supplier<Set<ChunkKey>> claimedChunks) {
        Entry entry = BY_FACTION.computeIfAbsent(faction, id -> {
            Entry created = new Entry();
            Set<ChunkKey> chunks = claimedChunks.get();
            created.watched = chunks;
            for (ChunkKey key : chunks) {
                WATCHED.put(key, id);
            }
            return created;
        });
        entry.results.put(researchId, value);
    }

    public static void invalidateChunk(ResourceKey<Level> dimension, long chunkPos) {
        if (WATCHED.isEmpty()) {
            return;
        }
        UUID faction = WATCHED.get(new ChunkKey(dimension, chunkPos));
        if (faction != null) {
            invalidateFaction(faction);
        }
    }

    private static void invalidateFaction(UUID faction) {
        Entry entry = BY_FACTION.remove(faction);
        if (entry != null) {
            for (ChunkKey key : entry.watched) {
                WATCHED.remove(key, faction);
            }
        }
    }

    public static void clear() {
        BY_FACTION.clear();
        WATCHED.clear();
    }
}
