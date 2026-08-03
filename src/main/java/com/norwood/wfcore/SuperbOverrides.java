package com.norwood.wfcore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class SuperbOverrides {

    private SuperbOverrides() {}

  
    public static final TagKey<Item> VEHICLE_STORAGE_BLACKLIST =
            TagKey.create(Registries.ITEM, new ResourceLocation("wfcore", "vehicle_storage_blacklist"));

    // Vehicle id -> data.
    public static volatile Map<String, OverrideData> overrideDataMap = Map.of();

    // Vehicle ids that plough through and break cacti/logs/leaves as they drive.
    public static volatile Set<String> foliageBreakers = Set.of();

    public static void setOverrideDataMap(Map<String, OverrideData> overrides) {
        overrideDataMap = Map.copyOf(overrides);
    }

    public static OverrideData getOverride(String vehicleId) {
        return overrideDataMap.get(vehicleId);
    }

    public static void setFoliageBreakers(Collection<? extends String> ids) {
        foliageBreakers = Set.copyOf(ids);
    }

    public static boolean breaksFoliage(String vehicleId) {
        return vehicleId != null && foliageBreakers.contains(vehicleId);
    }


    private static final Map<String, OverrideData> REGISTERED_OVERRIDES = new java.util.LinkedHashMap<>();
    private static final Set<String> REGISTERED_FOLIAGE = new java.util.LinkedHashSet<>();

    /** Register (or replace) a vehicle fuel/storage override from the {@code WFVehicles} KubeJS API. */
    public static synchronized void registerOverride(String vehicleId, OverrideData data) {
        REGISTERED_OVERRIDES.put(vehicleId, data);
        overrideDataMap = Map.copyOf(REGISTERED_OVERRIDES);
    }

    /** Register a foliage-breaking vehicle id from the {@code WFVehicles} KubeJS API. */
    public static synchronized void registerFoliageBreaker(String vehicleId) {
        REGISTERED_FOLIAGE.add(vehicleId);
        foliageBreakers = Set.copyOf(REGISTERED_FOLIAGE);
    }

    /**
     * @param storageSize    desired vehicle storage slot count, or {@code null} when this override does not
     *                       customize storage (the vehicle keeps Superb Warfare's native size/menu).
     * @param storageColumns preferred grid column count for the WFCore ModularUI (<= 0 means "use default").
     * @param storageFilter  optional item tag the storage slots are restricted to; {@code null} means no
     *                       restriction (any item allowed, like Superb Warfare's stock storage). A
     *                       {@link TagKey} is safe to build at registration time even before the referenced
     *                       tag is populated — it is resolved lazily by {@code stack.is(tag)} at use-time.
     *
     * <p>
     * {@code fluidConsumptionMap} is keyed by fluid <em>registry id</em> (e.g. {@code gtceu:diesel}), NOT a
     * resolved {@link net.minecraft.world.level.material.Fluid}. Overrides are registered from a KubeJS
     * <em>startup</em> script, which runs during mod loading before other mods' fluids are registered, so
     * resolving ids to {@code Fluid} objects at registration time yields null and silently drops every fuel.
     * Callers resolve {@code stack.getFluid()} back to its id via {@code ForgeRegistries.FLUIDS.getKey(...)}.
     */
    public record OverrideData(int maxFuel, Map<ResourceLocation, Float> fluidConsumptionMap, Integer storageSize,
                               int storageColumns, @Nullable TagKey<Item> storageFilter) {

        public OverrideData {
            fluidConsumptionMap = Map.copyOf(fluidConsumptionMap);
        }

        public boolean hasFuelOverride() {
            return !fluidConsumptionMap.isEmpty();
        }

        public boolean hasStorageOverride() {
            return storageSize != null && storageSize > 0;
        }

        public int columnsOrDefault() {
            return storageColumns > 0 ? storageColumns : 9;
        }

        /** True when this override restricts what may be placed in the vehicle's storage to {@link #storageFilter}. */
        public boolean hasStorageFilter() {
            return storageFilter != null;
        }

        /**
         * Whether {@code stack} may be placed in the vehicle's storage. Always {@code true} when no filter is set,
         * and empty stacks always pass so items can be cleared/removed regardless of the filter.
         */
        public boolean allowsInStorage(ItemStack stack) {
            return storageFilter == null || stack.isEmpty() || stack.is(storageFilter);
        }
    }
}
