package com.norwood.wfcore.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

import com.norwood.wfcore.SuperbOverrides;
import com.norwood.wfcore.WFCore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WFCoreConfig {

    // -------------------------------------------------------------------------
    // Default constants (kept so callers that reference DEFAULT_* still compile,
    // and so the volatile fields have sane pre-load values).
    // -------------------------------------------------------------------------
    private static final int DEFAULT_ENERGY_TO_FLUID_RATIO = 10;
    private static final int DEFAULT_REFUEL_INTERVAL_TICKS = 20;
    private static final boolean DEFAULT_CLEAR_STRUCTURE_LOOT = true;
    private static final boolean DEFAULT_DISABLE_NETHER = true;
    private static final int DEFAULT_DEPOSIT_YIELD_MIN = 2000;
    private static final int DEFAULT_DEPOSIT_YIELD_MAX = 8000;
    private static final boolean DEFAULT_DEPOSIT_WORLDGEN_ENABLED = true;
    private static final boolean DEFAULT_DEPOSIT_SCATTER = true;
    private static final int DEFAULT_DEPOSIT_WORLDGEN_RARITY = 24;
    private static final boolean DEFAULT_DEPOSIT_LOG_PLACEMENTS = false;
    private static final boolean DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED = false;
    private static final boolean DEFAULT_BALLISTICS_ENABLED = true;
    private static final boolean DEFAULT_BALLISTICS_DEBUG_LOGGING = false;

    // -------------------------------------------------------------------------
    // Volatile cache fields — pre-initialised to defaults so getters are safe
    // even before the config file is loaded.
    // -------------------------------------------------------------------------
    private static volatile int energyToFluidRatio = DEFAULT_ENERGY_TO_FLUID_RATIO;
    private static volatile int refuelIntervalTicks = DEFAULT_REFUEL_INTERVAL_TICKS;
    private static volatile boolean clearStructureLoot = DEFAULT_CLEAR_STRUCTURE_LOOT;
    private static volatile boolean disableNether = DEFAULT_DISABLE_NETHER;
    private static volatile int depositYieldMin = DEFAULT_DEPOSIT_YIELD_MIN;
    private static volatile int depositYieldMax = DEFAULT_DEPOSIT_YIELD_MAX;
    private static volatile boolean depositWorldgenEnabled = DEFAULT_DEPOSIT_WORLDGEN_ENABLED;
    private static volatile boolean depositScatter = DEFAULT_DEPOSIT_SCATTER;
    private static volatile int depositWorldgenRarity = DEFAULT_DEPOSIT_WORLDGEN_RARITY;
    private static volatile boolean depositLogPlacements = DEFAULT_DEPOSIT_LOG_PLACEMENTS;
    private static volatile boolean modelTransformDebugEnabled = DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED;
    private static volatile boolean ballisticsEnabled = DEFAULT_BALLISTICS_ENABLED;
    private static volatile boolean ballisticsDebugLogging = DEFAULT_BALLISTICS_DEBUG_LOGGING;

    // -------------------------------------------------------------------------
    // ForgeConfigSpec handles
    // -------------------------------------------------------------------------
    private static final ForgeConfigSpec.IntValue ENERGY_TO_FLUID_RATIO;
    private static final ForgeConfigSpec.IntValue REFUEL_INTERVAL_TICKS;
    private static final ForgeConfigSpec.BooleanValue CLEAR_STRUCTURE_LOOT;
    private static final ForgeConfigSpec.BooleanValue DISABLE_NETHER;
    private static final ForgeConfigSpec.BooleanValue MODEL_TRANSFORM_DEBUG_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BALLISTICS_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BALLISTICS_DEBUG_LOGGING;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> VEHICLES;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> FOLIAGE_BREAKERS;
    private static final ForgeConfigSpec.IntValue DEPOSIT_YIELD_MIN;
    private static final ForgeConfigSpec.IntValue DEPOSIT_YIELD_MAX;
    private static final ForgeConfigSpec.BooleanValue DEPOSIT_WORLDGEN_ENABLED;
    private static final ForgeConfigSpec.BooleanValue DEPOSIT_SCATTER;
    private static final ForgeConfigSpec.IntValue DEPOSIT_WORLDGEN_RARITY;
    private static final ForgeConfigSpec.BooleanValue DEPOSIT_LOG_PLACEMENTS;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        ENERGY_TO_FLUID_RATIO = builder
                .comment("Forge energy produced per millibucket of fluid fuel consumed.")
                .defineInRange("energyToFluidRatio", DEFAULT_ENERGY_TO_FLUID_RATIO, 1, Integer.MAX_VALUE);

        REFUEL_INTERVAL_TICKS = builder
                .comment("How often (in ticks) a fuelled vehicle tops up from its fluid tank.")
                .defineInRange("refuelIntervalTicks", DEFAULT_REFUEL_INTERVAL_TICKS, 1, Integer.MAX_VALUE);

        CLEAR_STRUCTURE_LOOT = builder
                .comment(
                        "Empty every chest and fishing loot table by default; repopulate or whitelist via KubeJS (WFLoot).")
                .define("clearStructureLoot", DEFAULT_CLEAR_STRUCTURE_LOOT);

        DISABLE_NETHER = builder
                .comment(
                        "Make the Nether inaccessible: nether portals never form and any travel to the_nether (portals, commands) is blocked.")
                .define("disableNether", DEFAULT_DISABLE_NETHER);

        MODEL_TRANSFORM_DEBUG_ENABLED = builder
                .comment(
                        "Dev tool: numpad-driven live editor for animated-machine model offsets (see IAnimatedMachine). Off by default so the numpad bindings and HUD stay inert for normal players.")
                .define("modelTransformDebugEnabled", DEFAULT_MODEL_TRANSFORM_DEBUG_ENABLED);

        VEHICLES = builder
                .comment(
                        "Per-vehicle fuel/storage overrides. One quoted entry per line, format:",
                        "  \"vehicleId; maxFuel=4000; fluids=fluidId=ratio,fluidId=ratio; storageSize=40; storageColumns=10\"",
                        "Every field after the id is optional. An entry only takes effect if it sets fluids or storageSize.",
                        "storageSize switches the vehicle to WFCore's resizable ModularUI storage; storageColumns is the grid width (default 9).",
                        "Example: \"superbwarfare:example_vehicle; maxFuel=4000; fluids=minecraft:lava=1.0,minecraft:water=0.5; storageSize=40; storageColumns=10\"")
                .defineList("vehicles", List.of(), o -> o instanceof String);

        FOLIAGE_BREAKERS = builder
                .comment(
                        "Vehicle ids that plough through and break cacti, wood logs and leaves as they drive.",
                        "Example: [\"superbwarfare:lav_150\", \"superbwarfare:truck\"]")
                .defineList("foliageBreakers", List.of(), o -> o instanceof String);

        builder.comment(
                "Drillable bedrock deposits. defaultYield is the per-block yield range used when a deposit type (built-in or KubeJS) does not specify its own.")
                .push("deposits");

        DEPOSIT_YIELD_MIN = builder
                .defineInRange("defaultYieldMin", DEFAULT_DEPOSIT_YIELD_MIN, 1, Integer.MAX_VALUE);

        DEPOSIT_YIELD_MAX = builder
                .defineInRange("defaultYieldMax", DEFAULT_DEPOSIT_YIELD_MAX, 1, Integer.MAX_VALUE);

        builder.comment(
                "Ambient weighted scatter across the world. Turn scatter off to rely only on KubeJS nodes/regions. rarity is \"1 in N chunks\".")
                .push("worldgen");

        DEPOSIT_WORLDGEN_ENABLED = builder
                .define("enabled", DEFAULT_DEPOSIT_WORLDGEN_ENABLED);

        DEPOSIT_SCATTER = builder
                .define("scatter", DEFAULT_DEPOSIT_SCATTER);

        DEPOSIT_WORLDGEN_RARITY = builder
                .defineInRange("rarity", DEFAULT_DEPOSIT_WORLDGEN_RARITY, 1, Integer.MAX_VALUE);

        DEPOSIT_LOG_PLACEMENTS = builder
                .comment("Debug: log every deposit cluster placed by worldgen (type, size, position). Testing aid.")
                .define("logPlacements", DEFAULT_DEPOSIT_LOG_PLACEMENTS);

        builder.pop(2);

        builder.comment("Off-thread long-range ballistics for TACZ bullets and Superb Warfare projectiles.")
                .push("ballistics");

        BALLISTICS_ENABLED = builder
                .comment(
                        "Master switch. When off, projectiles are never handed to the off-thread ballistics engine and behave exactly as their own mod ships them.")
                .define("enabled", DEFAULT_BALLISTICS_ENABLED);

        BALLISTICS_DEBUG_LOGGING = builder
                .comment(
                        "Log each virtual shell's lifecycle to the server log: leaving loaded chunks, impact position (and whether that chunk was loaded), deferred detonations, and expiries.")
                .define("debugLogging", DEFAULT_BALLISTICS_DEBUG_LOGGING);

        builder.pop();

        SPEC = builder.build();
    }

    private WFCoreConfig() {}

    // -------------------------------------------------------------------------
    // Public API — unchanged signatures
    // -------------------------------------------------------------------------

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

    /** When true, the Nether is disabled: portals never form and travel to {@code minecraft:the_nether} is blocked. */
    public static boolean isNetherDisabled() {
        return disableNether;
    }

    /** Default per-block deposit yield range, used when a deposit type does not set its own. */
    public static int getDefaultYieldMin() {
        return depositYieldMin;
    }

    public static int getDefaultYieldMax() {
        return depositYieldMax;
    }

    public static boolean isDepositWorldgenEnabled() {
        return depositWorldgenEnabled;
    }

    /** When true, deposits also scatter randomly across the world (in addition to KubeJS nodes/regions). */
    public static boolean isDepositScatterEnabled() {
        return depositScatter;
    }

    /** Deposit worldgen rarity, as "1 in N chunks". */
    public static int getDepositWorldgenRarity() {
        return depositWorldgenRarity;
    }

    /** Debug: when true, worldgen logs every deposit cluster it places (type, size, position). */
    public static boolean isDepositLogPlacements() {
        return depositLogPlacements;
    }

    /** Dev tool gate: the numpad model-transform debugger (see IAnimatedMachine) only arms when this is true. */
    public static boolean isModelTransformDebugEnabled() {
        return modelTransformDebugEnabled;
    }

    /** Master switch for the off-thread long-range ballistics engine (TACZ bullets + SBW projectiles). */
    public static boolean isBallisticsEnabled() {
        return ballisticsEnabled;
    }

    /** When true, the ballistics engine logs each virtual shell's lifecycle (demote / impact / defer / expiry). */
    public static boolean isBallisticsDebugLogging() {
        return ballisticsDebugLogging;
    }

    // -------------------------------------------------------------------------
    // Bake — called by WFCore on ModConfigEvent
    // -------------------------------------------------------------------------

    public static void bake() {
        energyToFluidRatio = ENERGY_TO_FLUID_RATIO.get();
        refuelIntervalTicks = REFUEL_INTERVAL_TICKS.get();
        clearStructureLoot = CLEAR_STRUCTURE_LOOT.get();
        disableNether = DISABLE_NETHER.get();
        modelTransformDebugEnabled = MODEL_TRANSFORM_DEBUG_ENABLED.get();
        ballisticsEnabled = BALLISTICS_ENABLED.get();
        ballisticsDebugLogging = BALLISTICS_DEBUG_LOGGING.get();
        depositYieldMin = DEPOSIT_YIELD_MIN.get();
        depositYieldMax = Math.max(DEPOSIT_YIELD_MAX.get(), depositYieldMin);
        depositWorldgenEnabled = DEPOSIT_WORLDGEN_ENABLED.get();
        depositScatter = DEPOSIT_SCATTER.get();
        depositWorldgenRarity = DEPOSIT_WORLDGEN_RARITY.get();
        depositLogPlacements = DEPOSIT_LOG_PLACEMENTS.get();
        SuperbOverrides.setOverrideDataMap(parseVehicleOverrides(VEHICLES.get()));
        SuperbOverrides.setFoliageBreakers(FOLIAGE_BREAKERS.get());
        WFCore.LOGGER.info(
                "Loaded WFCore TOML config: {} vehicle overrides, energy ratio {}, refuel interval {} ticks",
                SuperbOverrides.overrideDataMap.size(), energyToFluidRatio, refuelIntervalTicks);
    }

    // -------------------------------------------------------------------------
    // Vehicle override parsing — string-line based (replaces snakeyaml parsing)
    // -------------------------------------------------------------------------

    private static Map<String, SuperbOverrides.OverrideData> parseVehicleOverrides(List<? extends String> lines) {
        Map<String, SuperbOverrides.OverrideData> overrides = new LinkedHashMap<>();

        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String id = null;
            int maxFuel = 4000;
            Integer storageSize = null;
            int storageColumns = 9;
            Map<Fluid, Float> fluidMap = new LinkedHashMap<>();

            String[] tokens = line.split(";");
            for (String token : tokens) {
                String t = token.trim();
                if (t.isEmpty()) continue;

                int eqIdx = t.indexOf('=');
                if (eqIdx < 0) {
                    // Bare token — treat as vehicle id
                    if (id == null) {
                        id = t;
                    }
                    continue;
                }

                String key = t.substring(0, eqIdx).trim();
                String value = t.substring(eqIdx + 1).trim();

                switch (key) {
                    case "id" -> id = value;
                    case "maxFuel" -> {
                        Integer v = parseIntOr(value, null);
                        if (v != null && v > 0) maxFuel = v;
                    }
                    case "storageSize" -> storageSize = parsePositiveIntOrNull(value);
                    case "storageColumns" -> {
                        Integer v = parseIntOr(value, null);
                        if (v != null && v > 0) storageColumns = v;
                    }
                    case "fluids", "fluidConsumption" -> {
                        if (fluidMap.isEmpty()) {
                            fluidMap = parseFluidSubMap(value);
                        }
                    }
                    default -> { /* ignore unknown keys */ }
                }
            }

            if (id == null || id.isBlank()) continue;

            if (fluidMap.isEmpty() && storageSize == null) {
                WFCore.LOGGER.warn(
                        "Skipping WFCore vehicle override {} because it defines neither fluids nor storageSize", id);
                continue;
            }

            overrides.put(id, new SuperbOverrides.OverrideData(maxFuel, fluidMap, storageSize, storageColumns));
        }

        return overrides;
    }

    /**
     * Parse a fluid sub-map from a value string like {@code minecraft:lava=1.0,minecraft:water=0.5}.
     * Fluid ids contain ':' but never '=', so splitting on the first '=' is safe.
     */
    private static Map<Fluid, Float> parseFluidSubMap(String value) {
        Map<Fluid, Float> fluidMap = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return fluidMap;

        String[] entries = value.split(",");
        for (String entry : entries) {
            String e = entry.trim();
            if (e.isEmpty()) continue;

            int eqIdx = e.indexOf('=');
            if (eqIdx < 0) continue;

            String fluidId = e.substring(0, eqIdx).trim();
            String ratioText = e.substring(eqIdx + 1).trim();

            if (fluidId.isBlank()) continue;

            Float ratio = parseFloatOrNull(ratioText);
            if (ratio == null || ratio <= 0.0f) continue;

            ResourceLocation rl = ResourceLocation.tryParse(fluidId);
            if (rl == null) {
                WFCore.LOGGER.warn("Ignoring invalid fluid id '{}' in WFCore config", fluidId);
                continue;
            }

            Fluid fluid = ForgeRegistries.FLUIDS.getValue(rl);
            if (fluid == null) {
                WFCore.LOGGER.warn("Ignoring unknown fluid '{}' in WFCore config", fluidId);
                continue;
            }

            fluidMap.put(fluid, ratio);
        }

        return fluidMap;
    }

    // -------------------------------------------------------------------------
    // Small parsing helpers (String-typed, replacing the old Object-typed ones)
    // -------------------------------------------------------------------------

    /** Returns {@code fallback} (which may be null) if {@code text} is blank or unparseable. */
    private static Integer parseIntOr(String text, Integer fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Integer parsePositiveIntOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            int v = Integer.parseInt(text.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Float parseFloatOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
