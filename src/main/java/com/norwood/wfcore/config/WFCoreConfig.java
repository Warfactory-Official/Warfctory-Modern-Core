package com.norwood.wfcore.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import com.norwood.wfcore.SuperbOverrides;
import com.norwood.wfcore.WFCore;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WFCoreConfig {

    private static final String FILE_NAME = "wfcore.yaml";
    private static final int DEFAULT_ENERGY_TO_FLUID_RATIO = 10;
    private static final int DEFAULT_REFUEL_INTERVAL_TICKS = 20;
    private static final boolean DEFAULT_CLEAR_STRUCTURE_LOOT = true;
    private static final String DEFAULT_CONFIG = """
            # WFCore configuration
            # vehicles:
            #   - id: "superbwarfare:example_vehicle"
            #     # --- fluid fuel (optional) ---
            #     maxFuel: 4000
            #     fluids:
            #       minecraft:lava: 1.0
            #       minecraft:water: 0.5
            #     # --- storage (optional) ---
            #     # storageSize switches this vehicle to WFCore's resizable ModularUI storage.
            #     # storageColumns is the grid width (defaults to 9); rows wrap and scroll past 6 rows.
            #     storageSize: 40
            #     storageColumns: 10
            energyToFluidRatio: 10
            refuelIntervalTicks: 20
            # Empty every chest and fishing loot table by default; repopulate or whitelist via KubeJS (WFLoot).
            clearStructureLoot: true
            vehicles: []
            """;

    private static volatile int energyToFluidRatio = DEFAULT_ENERGY_TO_FLUID_RATIO;
    private static volatile int refuelIntervalTicks = DEFAULT_REFUEL_INTERVAL_TICKS;
    private static volatile boolean clearStructureLoot = DEFAULT_CLEAR_STRUCTURE_LOOT;

    private WFCoreConfig() {}

    public static int getEnergyToFluidRatio() {
        return energyToFluidRatio;
    }

    public static int getRefuelIntervalTicks() {
        return refuelIntervalTicks;
    }

    /** When true, WFCore empties every chest/fishing loot table on load unless KubeJS overrides or keeps it. */
    public static boolean isClearStructureLoot() {
        return clearStructureLoot;
    }

    public static void load() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path configFile = configDir.resolve(FILE_NAME);

        try {
            Files.createDirectories(configDir);
            if (Files.notExists(configFile)) {
                Files.writeString(configFile, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            }

            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                parse(reader);
            }
        } catch (IOException | RuntimeException e) {
            WFCore.LOGGER.error("Failed to load WFCore YAML config from {}", configFile, e);
            energyToFluidRatio = DEFAULT_ENERGY_TO_FLUID_RATIO;
            refuelIntervalTicks = DEFAULT_REFUEL_INTERVAL_TICKS;
            clearStructureLoot = DEFAULT_CLEAR_STRUCTURE_LOOT;
            SuperbOverrides.setOverrideDataMap(Map.of());
        }
    }

    @SuppressWarnings("unchecked")
    private static void parse(Reader reader) {
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(reader);
        if (!(loaded instanceof Map<?, ?> root)) {
            WFCore.LOGGER.warn("WFCore config was empty or not a map; using defaults");
            energyToFluidRatio = DEFAULT_ENERGY_TO_FLUID_RATIO;
            refuelIntervalTicks = DEFAULT_REFUEL_INTERVAL_TICKS;
            clearStructureLoot = DEFAULT_CLEAR_STRUCTURE_LOOT;
            SuperbOverrides.setOverrideDataMap(Map.of());
            return;
        }

        energyToFluidRatio = positiveInt(root.get("energyToFluidRatio"), DEFAULT_ENERGY_TO_FLUID_RATIO);
        refuelIntervalTicks = positiveInt(root.get("refuelIntervalTicks"), DEFAULT_REFUEL_INTERVAL_TICKS);
        clearStructureLoot = boolValue(root.get("clearStructureLoot"), DEFAULT_CLEAR_STRUCTURE_LOOT);
        SuperbOverrides.setOverrideDataMap(parseVehicleOverrides(root.get("vehicles")));
        WFCore.LOGGER.info("Loaded WFCore YAML config: {} vehicle overrides, energy ratio {}, refuel interval {} ticks",
                SuperbOverrides.overrideDataMap.size(), energyToFluidRatio, refuelIntervalTicks);
    }

    private static Map<String, SuperbOverrides.OverrideData> parseVehicleOverrides(Object rawVehicles) {
        Map<String, SuperbOverrides.OverrideData> overrides = new LinkedHashMap<>();

        if (rawVehicles instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> vehicleMap)) {
                    continue;
                }
                String id = stringValue(vehicleMap.get("id"));
                if (id == null || id.isBlank()) {
                    continue;
                }
                SuperbOverrides.OverrideData data = buildOverride(id, vehicleMap);
                if (data != null) {
                    overrides.put(id, data);
                }
            }
            return overrides;
        }

        if (rawVehicles instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String id = stringValue(entry.getKey());
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (!(entry.getValue() instanceof Map<?, ?> vehicleMap)) {
                    continue;
                }
                SuperbOverrides.OverrideData data = buildOverride(id, vehicleMap);
                if (data != null) {
                    overrides.put(id, data);
                }
            }
        }

        return overrides;
    }

    /**
     * Build an override from a single vehicle entry. An entry is kept when it customizes fuel (defines fluids)
     * <em>or</em> storage (defines a positive {@code storageSize}); entries that do neither are skipped.
     */
    private static SuperbOverrides.OverrideData buildOverride(String id, Map<?, ?> vehicleMap) {
        int maxFuel = positiveInt(vehicleMap.get("maxFuel"), 4000);
        Map<Fluid, Float> fluidMap = parseFluidMap(vehicleMap.get("fluids"));
        if (fluidMap.isEmpty()) {
            fluidMap = parseFluidMap(vehicleMap.get("fluidConsumption"));
        }

        Integer storageSize = positiveIntOrNull(vehicleMap.get("storageSize"));
        int storageColumns = positiveInt(vehicleMap.get("storageColumns"), 9);

        if (fluidMap.isEmpty() && storageSize == null) {
            WFCore.LOGGER.warn("Skipping WFCore vehicle override {} because it defines neither fluids nor storageSize",
                    id);
            return null;
        }

        return new SuperbOverrides.OverrideData(maxFuel, fluidMap, storageSize, storageColumns);
    }

    private static Map<Fluid, Float> parseFluidMap(Object rawFluids) {
        Map<Fluid, Float> fluidMap = new LinkedHashMap<>();

        if (!(rawFluids instanceof Map<?, ?> map)) {
            return fluidMap;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = stringValue(entry.getKey());
            Float ratio = floatValue(entry.getValue());
            if (key == null || ratio == null || ratio <= 0.0f) {
                continue;
            }

            ResourceLocation fluidId = ResourceLocation.tryParse(key);
            if (fluidId == null) {
                WFCore.LOGGER.warn("Ignoring invalid fluid id '{}' in WFCore config", key);
                continue;
            }

            Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid == null) {
                WFCore.LOGGER.warn("Ignoring unknown fluid '{}' in WFCore config", key);
                continue;
            }

            fluidMap.put(fluid, ratio);
        }

        return fluidMap;
    }

    private static int positiveInt(Object value, int fallback) {
        Integer parsed = integerValue(value);
        return parsed == null || parsed <= 0 ? fallback : parsed;
    }

    private static Integer positiveIntOrNull(Object value) {
        Integer parsed = integerValue(value);
        return parsed == null || parsed <= 0 ? null : parsed;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean boolValue(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        String text = stringValue(value);
        return text == null || text.isBlank() ? fallback : Boolean.parseBoolean(text);
    }

    private static Float floatValue(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
