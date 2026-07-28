package com.norwood.wfcore.api.research;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A node in the research tree, defined through {@link #builder(String)} (Java or KubeJS).
 *
 * <p>
 * Runs Factorio-style: a research is completed by repeating a uniform "run" {@link #getRunsRequired()}
 * times. Each run consumes {@link #getItemsPerRun()} + {@link #getCwuPerRun()} compute over at least
 * {@link #getTicksPerRun()} ticks at a constant {@link #getEut()} power draw, and advances completion by one
 * run (so progress % = completedRuns / runsRequired).
 */
public final class Research {

    private final String id;
    private final String nameKey;
    private final String descKey;
    private final ItemStack icon;
    private final boolean producesBlueprint;
    private final String category;
    private final int gridX;
    private final int gridY;
    private final boolean manualPos;
    private final int nodeColor;
    private final List<String> prerequisites;
    /** Each inner list is an "any-of" group: at least one id in the group must be complete to unlock this node. */
    private final List<List<String>> anyOfGroups;

    private final int runsRequired;
    private final List<ItemStack> itemsPerRun;
    private final List<FluidStack> fluidsPerRun;
    private final List<ItemStack> unlockedItems;
    private final long cwuPerRun;
    private final long eut;
    private final int ticksPerRun;

    private Research(Builder b) {
        this.id = b.id;
        this.nameKey = b.nameKey != null ? b.nameKey : "wfcore.research." + b.id + ".name";
        this.descKey = b.descKey != null ? b.descKey : "wfcore.research." + b.id + ".desc";
        this.icon = b.icon == null ? ItemStack.EMPTY : b.icon;
        this.producesBlueprint = b.producesBlueprint;
        this.category = b.category == null ? "wfcore" : b.category;
        this.gridX = b.gridX;
        this.gridY = b.gridY;
        this.manualPos = b.manualPos;
        this.nodeColor = b.nodeColor;
        this.prerequisites = Collections.unmodifiableList(new ArrayList<>(b.prerequisites));
        this.anyOfGroups = b.anyOfGroups.stream()
                .map(g -> Collections.unmodifiableList(new ArrayList<>(g)))
                .collect(Collectors.toUnmodifiableList());
        this.runsRequired = Math.max(1, b.runsRequired);
        this.itemsPerRun = Collections.unmodifiableList(new ArrayList<>(b.itemsPerRun));
        this.fluidsPerRun = Collections.unmodifiableList(new ArrayList<>(b.fluidsPerRun));
        this.unlockedItems = Collections.unmodifiableList(new ArrayList<>(b.unlockedItems));
        this.cwuPerRun = Math.max(0, b.cwuPerRun);
        this.eut = Math.max(0, b.eut);
        this.ticksPerRun = Math.max(1, b.ticksPerRun);
    }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDescKey() {
        return descKey;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public boolean hasBlueprint() {
        return producesBlueprint;
    }

    public String getCategory() {
        return category;
    }

    public int getGridX() {
        return gridX;
    }

    public int getGridY() {
        return gridY;
    }

    /** True if a position was set explicitly via {@link Builder#pos(int, int)} (vs. left to auto-layout). */
    public boolean hasManualPos() {
        return manualPos;
    }

    /** The node tile's tint (ARGB) when available/ready, or {@code 0} to use the default theme colour. */
    public int getNodeColor() {
        return nodeColor;
    }

    public List<String> getPrerequisites() {
        return prerequisites;
    }

    /** Groups where at least one member must be complete before this node unlocks (in addition to all prerequisites). */
    public List<List<String>> getAnyOfGroups() {
        return anyOfGroups;
    }

    public int getRunsRequired() {
        return runsRequired;
    }

    public List<ItemStack> getItemsPerRun() {
        return itemsPerRun;
    }

    /** Fluids consumed from an Import Fluid Hatch each run (empty if the research has no fluid cost). */
    public List<FluidStack> getFluidsPerRun() {
        return fluidsPerRun;
    }

    public List<ItemStack> getUnlockedItems() {
        return unlockedItems;
    }

    public long getCwuPerRun() {
        return cwuPerRun;
    }

    /** Total energy across the whole research (eut * ticksPerRun * runsRequired). */
    public long getTotalEU() {
        return eut * (long) ticksPerRun * runsRequired;
    }

    public long getEut() {
        return eut;
    }

    public int getTicksPerRun() {
        return ticksPerRun;
    }

    /** Total compute budget across the whole research. */
    public long getTotalCWU() {
        return cwuPerRun * runsRequired;
    }

    /** Minimum total ticks across the whole research. */
    public int getTotalDuration() {
        return ticksPerRun * runsRequired;
    }

    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String id;
        private String nameKey;
        private String descKey;
        private ItemStack icon;
        private boolean producesBlueprint;
        private String category;
        private int gridX;
        private int gridY;
        private boolean manualPos;
        private int nodeColor;
        private final List<String> prerequisites = new ArrayList<>();
        private final List<List<String>> anyOfGroups = new ArrayList<>();
        private int runsRequired = 1;
        private List<ItemStack> itemsPerRun = new ArrayList<>();
        private final List<FluidStack> fluidsPerRun = new ArrayList<>();
        private final List<ItemStack> unlockedItems = new ArrayList<>();
        private long cwuPerRun;
        private long eut = 32;
        private int ticksPerRun = 20;

        private Builder(@NotNull String id) {
            this.id = id;
        }

        public Builder name(String langKey) {
            this.nameKey = langKey;
            return this;
        }

        public Builder description(String langKey) {
            this.descKey = langKey;
            return this;
        }

        public Builder icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        public Builder blueprint() {
            this.producesBlueprint = true;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        /** Pins this research to a fixed grid cell, overriding auto-layout. Omit to let layout place it. */
        public Builder pos(int gridX, int gridY) {
            this.gridX = gridX;
            this.gridY = gridY;
            this.manualPos = true;
            return this;
        }

        /**
         * Tints the node tile (ARGB) when the research is available; locked/queued/done keep status colours.
         * Accepts {@code long} so KubeJS full-alpha literals like 0xFF2F6BD8 work.
         */
        public Builder nodeColor(long argb) {
            this.nodeColor = (int) argb;
            return this;
        }

        public Builder requires(String... researchIds) {
            Collections.addAll(this.prerequisites, researchIds);
            return this;
        }

        /**
         * Adds an "any-of" group: at least one of the given research IDs must be complete before this node
         * unlocks (checked on top of all {@link #requires} prerequisites). Call multiple times to add multiple
         * independent groups — each group must independently satisfy the "≥1 complete" condition.
         */
        public Builder anyOf(String... researchIds) {
            if (researchIds.length > 0) {
                List<String> group = new ArrayList<>(researchIds.length);
                Collections.addAll(group, researchIds);
                this.anyOfGroups.add(group);
            }
            return this;
        }

        public Builder runs(int runsRequired) {
            this.runsRequired = runsRequired;
            return this;
        }

        public Builder itemsPerRun(@Nullable List<ItemStack> itemsPerRun) {
            this.itemsPerRun = itemsPerRun == null ? new ArrayList<>() : new ArrayList<>(itemsPerRun);
            return this;
        }

        public Builder itemPerRun(ItemStack item) {
            this.itemsPerRun.add(item);
            return this;
        }

        public Builder fluidsPerRun(@Nullable List<FluidStack> fluidsPerRun) {
            this.fluidsPerRun.clear();
            if (fluidsPerRun != null) this.fluidsPerRun.addAll(fluidsPerRun);
            return this;
        }

        /** Adds a fluid consumed each run (Java / Forge {@link FluidStack} form). */
        public Builder fluidPerRun(FluidStack fluid) {
            if (fluid != null && !fluid.isEmpty()) this.fluidsPerRun.add(fluid);
            return this;
        }

        /**
         * Adds a fluid consumed each run by registry id + amount in mB. KubeJS-friendly overload (avoids passing a
         * Forge {@code FluidStack}, which {@code Fluid.of(...)} in scripts does not produce): e.g.
         * {@code .fluidPerRun('minecraft:water', 1000)}.
         */
        public Builder fluidPerRun(String fluidId, int amount) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluidId));
            if (fluid != null && fluid != Fluids.EMPTY && amount > 0) {
                this.fluidsPerRun.add(new FluidStack(fluid, amount));
            }
            return this;
        }

        /** Items this research unlocks (shown in the side panel as outputs). */
        public Builder unlocks(ItemStack... items) {
            Collections.addAll(this.unlockedItems, items);
            return this;
        }

        public Builder unlock(ItemStack item) {
            this.unlockedItems.add(item);
            return this;
        }

        public Builder cwuPerRun(long cwuPerRun) {
            this.cwuPerRun = cwuPerRun;
            return this;
        }

        public Builder eut(long eut) {
            this.eut = eut;
            return this;
        }

        public Builder ticksPerRun(int ticksPerRun) {
            this.ticksPerRun = ticksPerRun;
            return this;
        }

        public Research build() {
            return new Research(this);
        }

        /** Builds and adds to the {@link ResearchRegistry}, returning the built research. */
        public Research register() {
            Research r = build();
            ResearchRegistry.register(r);
            return r;
        }
    }
}
