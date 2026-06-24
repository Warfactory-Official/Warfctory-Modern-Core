package com.norwood.wfcore.common.research;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchCategory;

/**
 * Example research trees across five themed tabs. Most nodes are left to {@code ResearchLayout} (no
 * {@code .pos()}), showing automatic depth/row placement; vehicles and logistics pin every node by hand, and
 * aerospace mixes the two (one node pinned among auto-placed siblings). Registered on common setup; extendable
 * from KubeJS via {@code WFResearch}.
 */
public final class WFResearches {

    private WFResearches() {}

    public static void register() {
        registerCategories();

        // ---------------------------------------------------------------- Electronics: AUTO (branch + merge x2)
        autoNode("basic_electronics", "electronics", Items.REDSTONE, 4, 64, 32, 40,
                new ItemStack(Items.REDSTONE, 2), new ItemStack(Items.REPEATER));
        autoNode("logic_gates", "electronics", Items.LEVER, 5, 96, 48, 50,
                new ItemStack(Items.REDSTONE, 4), new ItemStack(Items.REDSTONE_TORCH), "basic_electronics");
        autoNode("power_systems", "electronics", Items.REDSTONE_BLOCK, 5, 96, 48, 50,
                new ItemStack(Items.COPPER_INGOT, 4), new ItemStack(Items.REDSTONE_BLOCK), "basic_electronics");
        autoNode("advanced_circuits", "electronics", Items.COMPARATOR, 8, 160, 64, 60,
                new ItemStack(Items.COPPER_INGOT, 6), new ItemStack(Items.COMPARATOR), "logic_gates");
        autoNode("capacitor_banks", "electronics", Items.DAYLIGHT_DETECTOR, 8, 160, 64, 60,
                new ItemStack(Items.GOLD_INGOT, 4), new ItemStack(Items.DAYLIGHT_DETECTOR), "power_systems");
        autoNode("integrated_processors", "electronics", Items.OBSERVER, 10, 256, 96, 70,
                new ItemStack(Items.GOLD_INGOT, 8), new ItemStack(Items.OBSERVER), "advanced_circuits",
                "capacitor_banks");
        autoNode("radar_systems", "electronics", Items.LIGHTNING_ROD, 12, 320, 128, 80,
                new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.LIGHTNING_ROD), "integrated_processors");
        autoNode("sensor_arrays", "electronics", Items.SCULK_SENSOR, 12, 320, 128, 80,
                new ItemStack(Items.AMETHYST_SHARD, 4), new ItemStack(Items.SCULK_SENSOR), "integrated_processors");
        autoNode("quantum_computing", "electronics", Items.AMETHYST_SHARD, 16, 512, 256, 100,
                new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.AMETHYST_BLOCK), "radar_systems",
                "sensor_arrays");

        // ---------------------------------------------------------------- Vehicles: MANUAL (hand-placed, 5 rows)
        node("vehicle_chassis", "vehicles", Items.IRON_BARS, 0, 0, 5, 80, 48, 45,
                new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.IRON_BARS));
        node("light_frame", "vehicles", Items.IRON_INGOT, 1, -1, 6, 120, 64, 55,
                new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.MINECART), "vehicle_chassis");
        node("heavy_frame", "vehicles", Items.IRON_BLOCK, 1, 1, 6, 120, 64, 55,
                new ItemStack(Items.IRON_BLOCK, 2), new ItemStack(Items.IRON_BLOCK), "vehicle_chassis");
        node("scout_car", "vehicles", Items.MINECART, 2, -1, 9, 200, 96, 65,
                new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.MINECART), "light_frame");
        node("armored_carrier", "vehicles", Items.HOPPER_MINECART, 2, 1, 9, 200, 96, 65,
                new ItemStack(Items.IRON_BLOCK, 3), new ItemStack(Items.HOPPER_MINECART), "heavy_frame");
        node("recon_drones", "vehicles", Items.PHANTOM_MEMBRANE, 3, -2, 10, 260, 110, 70,
                new ItemStack(Items.PHANTOM_MEMBRANE, 4), new ItemStack(Items.PHANTOM_MEMBRANE), "scout_car");
        node("vehicle_engineering", "vehicles", Items.CHEST_MINECART, 3, 0, 12, 300, 128, 80,
                new ItemStack(Items.IRON_INGOT, 12), new ItemStack(Items.CHEST_MINECART), "scout_car",
                "armored_carrier");
        node("main_battle_tank", "vehicles", Items.TNT_MINECART, 3, 2, 14, 360, 160, 85,
                new ItemStack(Items.IRON_BLOCK, 4), new ItemStack(Items.TNT_MINECART), "armored_carrier");
        node("mechanized_warfare", "vehicles", Items.NETHERITE_INGOT, 4, 0, 18, 560, 256, 110,
                new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.NETHERITE_BLOCK), "vehicle_engineering",
                "main_battle_tank");

        // ---------------------------------------------------------------- Weapons: AUTO (two parallel chains merge)
        autoNode("ballistics", "weapons", Items.GUNPOWDER, 5, 80, 40, 45,
                new ItemStack(Items.GUNPOWDER, 4), new ItemStack(Items.GUNPOWDER));
        autoNode("small_arms", "weapons", Items.BOW, 6, 120, 56, 55,
                new ItemStack(Items.STICK, 6), new ItemStack(Items.BOW), "ballistics");
        autoNode("explosives", "weapons", Items.TNT, 6, 120, 56, 55,
                new ItemStack(Items.GUNPOWDER, 6), new ItemStack(Items.TNT), "ballistics");
        autoNode("automatic_weapons", "weapons", Items.CROSSBOW, 9, 200, 96, 65,
                new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.CROSSBOW), "small_arms");
        autoNode("artillery", "weapons", Items.FIRE_CHARGE, 9, 200, 96, 65,
                new ItemStack(Items.GUNPOWDER, 8), new ItemStack(Items.FIRE_CHARGE), "explosives");
        autoNode("marksman_systems", "weapons", Items.SPECTRAL_ARROW, 11, 280, 120, 75,
                new ItemStack(Items.ARROW, 16), new ItemStack(Items.SPECTRAL_ARROW), "automatic_weapons");
        autoNode("guided_munitions", "weapons", Items.FIREWORK_ROCKET, 11, 280, 120, 75,
                new ItemStack(Items.GUNPOWDER, 12), new ItemStack(Items.FIREWORK_ROCKET), "artillery");
        autoNode("advanced_weaponry", "weapons", Items.TRIDENT, 16, 480, 220, 100,
                new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.TRIDENT), "marksman_systems",
                "guided_munitions");

        // ---------------------------------------------------------------- Aerospace: MIXED (auto + one pinned node)
        autoNode("aerodynamics", "aerospace", Items.FEATHER, 5, 90, 48, 50,
                new ItemStack(Items.FEATHER, 8), new ItemStack(Items.FEATHER));
        autoNode("jet_propulsion", "aerospace", Items.BLAZE_POWDER, 7, 140, 72, 60,
                new ItemStack(Items.BLAZE_POWDER, 4), new ItemStack(Items.BLAZE_POWDER), "aerodynamics");
        autoNode("rocketry", "aerospace", Items.FIREWORK_ROCKET, 7, 140, 72, 60,
                new ItemStack(Items.GUNPOWDER, 6), new ItemStack(Items.FIREWORK_STAR), "aerodynamics");
        autoNode("aircraft_design", "aerospace", Items.ELYTRA, 10, 240, 110, 70,
                new ItemStack(Items.PHANTOM_MEMBRANE, 6), new ItemStack(Items.ELYTRA), "jet_propulsion");
        autoNode("missile_systems", "aerospace", Items.END_CRYSTAL, 10, 240, 110, 70,
                new ItemStack(Items.BLAZE_ROD, 4), new ItemStack(Items.END_CRYSTAL), "rocketry");
        autoNode("air_superiority", "aerospace", Items.PHANTOM_MEMBRANE, 14, 380, 180, 90,
                new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.ELYTRA), "aircraft_design", "missile_systems");
        // Pinned by hand at column 5, row 1 — auto-placed siblings lay out around this fixed cell.
        node("orbital_systems", "aerospace", Items.BEACON, 5, 1, 20, 640, 300, 120,
                new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.BEACON), "air_superiority");

        // ---------------------------------------------------------------- Logistics: MANUAL, TWO parallel trees
        // Two unconnected roots (rows 0 and 3) share the page but never link, so they render as separate trees.
        node("supply_lines", "logistics", Items.CHEST, 0, 0, 5, 90, 48, 50,
                new ItemStack(Items.CHEST, 2), new ItemStack(Items.CHEST_MINECART));
        node("field_depots", "logistics", Items.BARREL, 1, 0, 8, 160, 72, 65,
                new ItemStack(Items.IRON_INGOT, 6), new ItemStack(Items.BARREL), "supply_lines");
        node("signal_corps", "logistics", Items.REDSTONE_LAMP, 0, 3, 5, 90, 48, 50,
                new ItemStack(Items.REDSTONE, 6), new ItemStack(Items.REDSTONE_LAMP));
        node("encrypted_comms", "logistics", Items.COMPASS, 1, 3, 8, 160, 72, 65,
                new ItemStack(Items.AMETHYST_SHARD, 4), new ItemStack(Items.COMPASS), "signal_corps");
    }

    /**
     * Registers the example categories (tabs) with their theming. Each sets a connector colour; a couple also
     * tint the page background. Done from Java here; a pack can do the same from KubeJS via
     * {@code WFResearch.category(id)...register()}, or just reference a new category id on a research (a default
     * tab is created on demand).
     */
    private static void registerCategories() {
        ResearchCategory.builder("electronics").icon(new ItemStack(Items.REPEATER))
                .connectorColor(0xFF2F9BD8).register();
        ResearchCategory.builder("vehicles").icon(new ItemStack(Items.MINECART))
                .connectorColor(0xFFB0A018).backgroundColor(0xFF161210).register();
        ResearchCategory.builder("weapons").icon(new ItemStack(Items.CROSSBOW))
                .connectorColor(0xFFD05030).register();
        ResearchCategory.builder("aerospace").icon(new ItemStack(Items.ELYTRA))
                .connectorColor(0xFF50C0E0).backgroundColor(0xFF0E1014).register();
        ResearchCategory.builder("logistics").icon(new ItemStack(Items.CHEST))
                .connectorColor(0xFF60C060).backgroundColor(0xFF0F140F).register();
    }

    /** Shared builder for the example nodes; name/description default to {@code wfcore.research.<id>.name|.desc}. */
    private static Research.Builder build(String id, String category, Item icon, int runs, long cwuPerRun, long eut,
                                          int ticksPerRun, ItemStack inputPerRun, ItemStack unlock,
                                          String... requires) {
        return Research.builder(id)
                .category(category)
                .icon(new ItemStack(icon))
                .requires(requires)
                .runs(runs).cwuPerRun(cwuPerRun).eut(eut).ticksPerRun(ticksPerRun)
                .itemPerRun(inputPerRun)
                .unlocks(unlock)
                .blueprint();
    }

    /** Auto-placed node: position is left to {@code ResearchLayout} (depth-based column, packed rows). */
    private static void autoNode(String id, String category, Item icon, int runs, long cwuPerRun, long eut,
                                 int ticksPerRun, ItemStack inputPerRun, ItemStack unlock, String... requires) {
        build(id, category, icon, runs, cwuPerRun, eut, ticksPerRun, inputPerRun, unlock, requires).register();
    }

    /** Hand-placed node: pinned to a fixed {@code (gridX, gridY)} cell, overriding auto-layout. */
    private static void node(String id, String category, Item icon, int gridX, int gridY, int runs, long cwuPerRun,
                             long eut, int ticksPerRun, ItemStack inputPerRun, ItemStack unlock, String... requires) {
        build(id, category, icon, runs, cwuPerRun, eut, ticksPerRun, inputPerRun, unlock, requires)
                .pos(gridX, gridY).register();
    }
}
