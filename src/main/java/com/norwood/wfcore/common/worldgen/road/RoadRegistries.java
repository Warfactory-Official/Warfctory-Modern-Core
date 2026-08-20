package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import com.norwood.wfcore.WFCore;

/**
 * The two datapack registries the road system reads.
 *
 * <p>Both are server-only — no network codec — because nothing about a road is needed on the client: the blocks
 * are ordinary blocks by the time they reach anyone. Being datapack registries rather than a reload listener is
 * what makes them safe to read from a chunk generation thread: they are frozen with the level's registry access
 * and cannot be swapped underneath a generating chunk.
 *
 * <p>NeoForge prefixes non-vanilla registries with their namespace, so the on-disk directories are
 * {@code data/<pack>/wfcore/road_type/} and {@code data/<pack>/wfcore/road_network/}.
 */
public final class RoadRegistries {

    public static final ResourceKey<Registry<RoadType>> ROAD_TYPE =
            ResourceKey.createRegistryKey(WFCore.id("road_type"));
    public static final ResourceKey<Registry<RoadNetwork>> ROAD_NETWORK =
            ResourceKey.createRegistryKey(WFCore.id("road_network"));

    private RoadRegistries() {}

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(ROAD_TYPE, RoadType.CODEC);
        event.dataPackRegistry(ROAD_NETWORK, RoadNetwork.CODEC);
    }
}
