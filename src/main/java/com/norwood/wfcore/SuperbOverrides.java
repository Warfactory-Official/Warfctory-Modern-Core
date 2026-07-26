package com.norwood.wfcore;

import net.minecraft.world.level.material.Fluid;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class SuperbOverrides {

    private SuperbOverrides() {}

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
     */
    public record OverrideData(int maxFuel, Map<Fluid, Float> fluidConsumptionMap, Integer storageSize,
                               int storageColumns) {

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
    }
}
