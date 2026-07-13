package com.norwood.wfcore.integration.kubejs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.compute.CPURegistry;
import com.norwood.wfcore.common.compute.RAMRegistry;
import com.norwood.wfcore.common.compute.WFComputeConfig;
import com.norwood.wfcore.common.compute.WFComputeScripts;
import com.norwood.wfcore.common.fluid.CoolantRegistry;

import javax.annotation.Nullable;

/**
 * KubeJS binding exposed as {@code WFCompute} in <b>startup scripts</b>. It makes the computation mainframe's
 * registries (which items count as CPUs / RAM / coolant, and their stats) and every simulation tunable
 * (temperatures, thermal masses, cooling coefficients, ambient temps, …) fully configurable from a pack.
 *
 * <p>
 * Operations are recorded and replayed <em>after</em> WFCore's built-in defaults are registered (KubeJS startup
 * runs before common setup), so a pack can add, override <em>or</em> remove any built-in reliably.
 *
 * <pre>{@code
 * // startup_scripts/compute.js
 * WFCompute
 *     // --- CPUs: item id -> (efficiency 0..1, maxPower EU/t, idle EU/t) ---
 *     .cpu('gtceu:integrated_circuit_lv').efficiency(0.7).maxPower(120).minPower(8).register()
 *     .cpu('gtceu:integrated_circuit_hv').efficiency(0.55).maxPower(1920).minPower(64).register()
 *     // --- RAM: item id -> CWU/t throughput cap it contributes ---
 *     .ram('gtceu:random_access_memory_wafer', 256)          // shorthand
 *     .ram('gtceu:nand_memory_chip').throughput(1024).register()
 *     // --- Coolant fluids: id -> heat capacity, optional hot output fluid ---
 *     .coolant('minecraft:water').heatCapacity(1.0).register()
 *     .coolant('gtceu:nitrogen').heatCapacity(10.0).hotVariant('gtceu:hot_nitrogen').register()
 *     // --- remove a built-in ---
 *     .removeCpu('gtceu:integrated_circuit_mv')
 *
 * // --- global tunables (all chainable) ---
 * WFCompute.config()
 *     .maxTemperature(120)            // explode threshold (°C)
 *     .baseFrameMass(600)             // thermal inertia of the frame
 *     .hatchThermalMass(50)           // added inertia per cpu/ram/cooler hatch
 *     .passiveCoolingBase(0.06)       // per-tier passive cooling coefficient
 *     .activeCoolingScale(0.12)       // liquid-cooler strength
 *     .liquidCoolantPerTick(100)      // mB a liquid cooler drains per tick
 *     .heatRatio(0.04)                // waste-EU -> heat conversion
 *     .ambientNether(75)
 * }</pre>
 *
 * <p>
 * Items and fluids are given by id (namespace optional, defaults to {@code minecraft}); they are resolved at
 * apply time, so referencing another mod's content is fine even from startup. Unknown ids are skipped with a
 * warning in the log rather than crashing.
 */
public class WFComputeBindings {

    private static final WFComputeConfigBinding CONFIG = new WFComputeConfigBinding();


    /** Begin defining a CPU item; finish with {@code .register()}. */
    public CpuBuilder cpu(String itemId) {
        return new CpuBuilder(this, itemId);
    }

    public WFComputeBindings removeCpu(String itemId) {
        WFComputeScripts.enqueue(() -> {
            Item item = resolveItem(itemId);
            if (item != null) CPURegistry.unregister(item);
        });
        return this;
    }


    /** Begin defining a RAM item; finish with {@code .register()}. */
    public RamBuilder ram(String itemId) {
        return new RamBuilder(this, itemId);
    }

    /** Shorthand: register a RAM item with the given CWU/t throughput in one call. */
    public WFComputeBindings ram(String itemId, int throughput) {
        WFComputeScripts.enqueue(() -> {
            Item item = resolveItem(itemId);
            if (item != null) RAMRegistry.register(item, throughput);
        });
        return this;
    }

    public WFComputeBindings removeRam(String itemId) {
        WFComputeScripts.enqueue(() -> {
            Item item = resolveItem(itemId);
            if (item != null) RAMRegistry.unregister(item);
        });
        return this;
    }


    /** Begin defining a coolant fluid; finish with {@code .register()}. */
    public CoolantBuilder coolant(String fluidId) {
        return new CoolantBuilder(this, fluidId);
    }

    public WFComputeBindings removeCoolant(String fluidId) {
        WFComputeScripts.enqueue(() -> {
            Fluid fluid = resolveFluid(fluidId);
            if (fluid != null) CoolantRegistry.unregister(fluid);
        });
        return this;
    }


    /** Accessor for the global compute tunables (see {@link WFComputeConfigBinding}). */
    public WFComputeConfigBinding config() {
        return CONFIG;
    }


    @Nullable
    static Item resolveItem(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            WFCore.LOGGER.warn("[WFCompute] invalid item id '{}'", id);
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            WFCore.LOGGER.warn("[WFCompute] unknown item '{}' - is the providing mod loaded?", id);
            return null;
        }
        return item;
    }

    @Nullable
    static Fluid resolveFluid(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            WFCore.LOGGER.warn("[WFCompute] invalid fluid id '{}'", id);
            return null;
        }
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(rl);
        if (fluid == null) {
            WFCore.LOGGER.warn("[WFCompute] unknown fluid '{}' - is the providing mod loaded?", id);
            return null;
        }
        return fluid;
    }


    /** Fluent builder for a CPU entry: an item plus its (efficiency, maxPower, minPower). */
    public static final class CpuBuilder {

        private final WFComputeBindings owner;
        private final String itemId;
        private double efficiency = 0.5;
        private long maxPower = 0;
        private long minPower = 0;

        private CpuBuilder(WFComputeBindings owner, String itemId) {
            this.owner = owner;
            this.itemId = itemId;
        }

        /** Thermal efficiency, 0..1. Lower = more waste heat. */
        public CpuBuilder efficiency(double efficiency) {
            this.efficiency = efficiency;
            return this;
        }

        /** Peak power draw in EU/t at full load. */
        public CpuBuilder maxPower(long maxPower) {
            this.maxPower = maxPower;
            return this;
        }

        /** Idle / baseline power draw in EU/t. */
        public CpuBuilder minPower(long minPower) {
            this.minPower = minPower;
            return this;
        }

        /** Register the CPU. Returns the {@code WFCompute} binding so calls can keep chaining. */
        public WFComputeBindings register() {
            double eff = Math.min(1.0, Math.max(0.0, efficiency));
            long max = maxPower;
            long min = minPower;
            WFComputeScripts.enqueue(() -> {
                Item item = resolveItem(itemId);
                if (item == null) return;
                if (max <= 0) {
                    WFCore.LOGGER.warn("[WFCompute] CPU '{}' has maxPower <= 0; skipping", itemId);
                    return;
                }
                CPURegistry.register(item, new CPURegistry.CPUEntry(eff, max, Math.max(0, Math.min(min, max))));
            });
            return owner;
        }
    }

    /** Fluent builder for a RAM entry: an item plus its CWU/t throughput. */
    public static final class RamBuilder {

        private final WFComputeBindings owner;
        private final String itemId;
        private int throughput = 0;

        private RamBuilder(WFComputeBindings owner, String itemId) {
            this.owner = owner;
            this.itemId = itemId;
        }

        /** Max CWU this RAM lets the mainframe route per tick. */
        public RamBuilder throughput(int throughput) {
            this.throughput = throughput;
            return this;
        }

        public WFComputeBindings register() {
            int tp = throughput;
            WFComputeScripts.enqueue(() -> {
                Item item = resolveItem(itemId);
                if (item == null) return;
                if (tp <= 0) {
                    WFCore.LOGGER.warn("[WFCompute] RAM '{}' has throughput <= 0; skipping", itemId);
                    return;
                }
                RAMRegistry.register(item, tp);
            });
            return owner;
        }
    }

    /** Fluent builder for a coolant fluid: heat capacity plus an optional hot output variant. */
    public static final class CoolantBuilder {

        private final WFComputeBindings owner;
        private final String fluidId;
        private double heatCapacity = 1.0;
        @Nullable
        private String hotVariantId;

        private CoolantBuilder(WFComputeBindings owner, String fluidId) {
            this.owner = owner;
            this.fluidId = fluidId;
        }

        /** EU carried per mB — higher = stronger active cooling. */
        public CoolantBuilder heatCapacity(double heatCapacity) {
            this.heatCapacity = heatCapacity;
            return this;
        }

        /** Optional fluid produced when this coolant is spent (e.g. hot coolant). Omit to consume it outright. */
        public CoolantBuilder hotVariant(String fluidId) {
            this.hotVariantId = fluidId;
            return this;
        }

        public WFComputeBindings register() {
            double cap = heatCapacity;
            String hotId = hotVariantId;
            WFComputeScripts.enqueue(() -> {
                Fluid cold = resolveFluid(fluidId);
                if (cold == null) return;
                Fluid hot = hotId == null ? null : resolveFluid(hotId);
                CoolantRegistry.register(cold, hot, cap);
            });
            return owner;
        }
    }
}
