package com.norwood.wfcore;

import net.minecraft.world.level.material.Fluid;

import java.util.Collections;
import java.util.Map;

public final class SuperbFuelOverride {

    private SuperbFuelOverride() {}

    // Vehicle id -> data.
    public static volatile Map<String, OverrideData> overrideDataMap = Map.of();

    public static void setOverrideDataMap(Map<String, OverrideData> overrides) {
        overrideDataMap = Collections.unmodifiableMap(Map.copyOf(overrides));
    }

    public static OverrideData getOverride(String vehicleId) {
        return overrideDataMap.get(vehicleId);
    }

    public record OverrideData(int maxFuel, Map<Fluid, Float> fluidConsumptionMap) {

        public OverrideData {
            fluidConsumptionMap = Map.copyOf(fluidConsumptionMap);
        }
    }
}
