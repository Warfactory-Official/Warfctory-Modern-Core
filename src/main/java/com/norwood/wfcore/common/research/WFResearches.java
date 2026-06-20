package com.norwood.wfcore.common.research;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.api.research.Research;

/** Example research tree. Registered on common setup; extendable from KubeJS via {@code WFCoreResearchEvents}. */
public final class WFResearches {

    private WFResearches() {}

    public static void register() {
        Research.builder("basic_electronics")
                .name("wfcore.research.basic_electronics.name")
                .icon(new ItemStack(Items.REDSTONE))
                .pos(0, 0)
                .runs(4).cwuPerRun(64).eut(32).ticksPerRun(40)
                .itemPerRun(new ItemStack(Items.REDSTONE, 2))
                .unlocks(new ItemStack(Items.REPEATER))
                .blueprint()
                .register();

        Research.builder("advanced_circuits")
                .name("wfcore.research.advanced_circuits.name")
                .icon(new ItemStack(Items.COMPARATOR))
                .pos(1, 0)
                .requires("basic_electronics")
                .runs(8).cwuPerRun(128).eut(64).ticksPerRun(60)
                .itemPerRun(new ItemStack(Items.COPPER_INGOT))
                .unlocks(new ItemStack(Items.COMPARATOR))
                .blueprint()
                .register();

        Research.builder("radar_systems")
                .name("wfcore.research.radar_systems.name")
                .icon(new ItemStack(Items.LIGHTNING_ROD))
                .pos(2, -1)
                .requires("advanced_circuits")
                .runs(12).cwuPerRun(256).eut(128).ticksPerRun(80)
                .itemPerRun(new ItemStack(Items.IRON_INGOT, 4))
                .unlocks(new ItemStack(Items.LIGHTNING_ROD))
                .blueprint()
                .register();

        Research.builder("vehicle_engineering")
                .name("wfcore.research.vehicle_engineering.name")
                .icon(new ItemStack(Items.MINECART))
                .pos(2, 1)
                .requires("advanced_circuits")
                .runs(16).cwuPerRun(384).eut(256).ticksPerRun(100)
                .itemPerRun(new ItemStack(Items.IRON_INGOT, 8))
                .unlocks(new ItemStack(Items.MINECART))
                .blueprint()
                .register();
    }
}
