package com.norwood.wfcore.common.world;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import com.norwood.wfcore.WFCore;

/**
 * Per-level {@link SavedData} reference cache. {@code DimensionDataStorage} is not thread-safe: region workers read the
 * cached reference; storage is touched on the server thread only, at {@code LevelEvent.Load} (caller primes via
 * {@link #get}). Uncached lookup off the server thread => throws.
 */
@EventBusSubscriber(modid = WFCore.MOD_ID)
public final class LevelSavedCache<T extends SavedData> {

    private static final List<LevelSavedCache<?>> ALL = new CopyOnWriteArrayList<>();

    private final String name;
    private final Function<ServerLevel, SavedData.Factory<T>> factory;
    private final boolean overworld;
    private final ConcurrentHashMap<ServerLevel, T> byLevel = new ConcurrentHashMap<>();

    private LevelSavedCache(String name, Function<ServerLevel, SavedData.Factory<T>> factory, boolean overworld) {
        this.name = name;
        this.factory = factory;
        this.overworld = overworld;
        ALL.add(this);
    }

    public static <T extends SavedData> LevelSavedCache<T> perLevel(String name, SavedData.Factory<T> factory) {
        return new LevelSavedCache<>(name, l -> factory, false);
    }

    public static <T extends SavedData> LevelSavedCache<T> perLevel(String name,
                                                                    Function<ServerLevel, SavedData.Factory<T>> factory) {
        return new LevelSavedCache<>(name, factory, false);
    }

    /** One instance, stored on the overworld; {@code get(anyLevel)} resolves it. */
    public static <T extends SavedData> LevelSavedCache<T> overworld(String name, SavedData.Factory<T> factory) {
        return new LevelSavedCache<>(name, l -> factory, true);
    }

    public T get(ServerLevel level) {
        ServerLevel key = this.overworld ? level.getServer().overworld() : level;
        T cached = this.byLevel.get(key);
        return cached != null ? cached : this.resolve(key);
    }

    private T resolve(ServerLevel key) {
        if (!key.getServer().isSameThread()) {
            throw new IllegalStateException("[wfcore] LevelSavedCache " + this.name + " not primed for "
                    + key.dimension().location() + "; storage lookup off the server thread");
        }
        return this.byLevel.computeIfAbsent(key, l -> l.getDataStorage().computeIfAbsent(this.factory.apply(l), this.name));
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (LevelSavedCache<?> c : ALL) {
                c.byLevel.remove(level);
            }
        }
    }

    @SubscribeEvent
    public static void onLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            prime(level);
        }
    }

    private static void prime(ServerLevel level) {
        com.norwood.wfcore.radar.data.RadarRegistryData.get(level);
        com.norwood.wfcore.radar.data.CalibratorData.get(level);
        com.norwood.wfcore.common.pipenet.ac.net.LevelACPipeNet.getOrCreate(level);
        if (level.dimension() == Level.OVERWORLD) {
            com.norwood.wfcore.radar.data.RadarScanData.get(level);
        }
    }
}
