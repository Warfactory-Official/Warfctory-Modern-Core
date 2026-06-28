package com.norwood.wfcore.common.deposit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.norwood.wfcore.config.WFCoreConfig;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A drillable deposit kind. Held in the runtime {@link WFDeposits} registry rather than a Forge registry so
 * KubeJS startup scripts can add their own ore types. Carries everything worldgen, rendering and the drilling
 * recipe condition need: a custom block texture, a configurable yield range, a worldgen weight, the dimensions
 * it generates in, and the allowed cluster size. The actual drilled output/EU/duration lives on a separate
 * {@code wfcore:drilling} recipe keyed by this type's id (see DepositRecipeCondition).
 */
public final class DepositType {

    private final ResourceLocation id;
    private final String nameKey;
    private final ResourceLocation texture;
    private final int yieldMin;
    private final int yieldMax;
    private final int weight;
    private final Set<ResourceLocation> dimensions;
    private final int clusterMin;
    private final int clusterMax;

    private DepositType(Builder b) {
        this.id = b.id;
        this.nameKey = b.nameKey != null ? b.nameKey : "wfcore.deposit." + b.id.getNamespace() + "." + b.id.getPath();
        this.texture = b.texture != null ? b.texture :
                new ResourceLocation(id.getNamespace(), "block/deposit/" + id.getPath());
        int defMin = WFCoreConfig.getDefaultYieldMin();
        int defMax = WFCoreConfig.getDefaultYieldMax();
        this.yieldMin = b.yieldMin >= 0 ? b.yieldMin : defMin;
        this.yieldMax = b.yieldMax >= 0 ? b.yieldMax : defMax;
        this.weight = Math.max(1, b.weight);
        this.dimensions = b.dimensions.isEmpty() ? Set.of(new ResourceLocation("minecraft", "overworld")) :
                Set.copyOf(b.dimensions);
        this.clusterMin = Mth.clamp(b.clusterMin, 2, 6);
        this.clusterMax = Mth.clamp(Math.max(b.clusterMax, this.clusterMin), this.clusterMin, 6);
    }

    public ResourceLocation id() {
        return id;
    }

    public String nameKey() {
        return nameKey;
    }

    /** Conventional texture id ({@code ns:block/...}); the BER expands it to {@code ns:textures/<path>.png}. */
    public ResourceLocation texture() {
        return texture;
    }

    public int weight() {
        return weight;
    }

    public Set<ResourceLocation> dimensions() {
        return dimensions;
    }

    public boolean generatesIn(ResourceLocation dimension) {
        return dimensions.contains(dimension);
    }

    public int clusterMin() {
        return clusterMin;
    }

    public int clusterMax() {
        return clusterMax;
    }

    /** A random starting yield within {@code [yieldMin, yieldMax]} (inclusive). */
    public int rollYield(RandomSource random) {
        if (yieldMax <= yieldMin) {
            return Math.max(1, yieldMin);
        }
        return yieldMin + random.nextInt(yieldMax - yieldMin + 1);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final ResourceLocation id;
        private String nameKey;
        private ResourceLocation texture;
        private int yieldMin = -1;
        private int yieldMax = -1;
        private int weight = 10;
        private final Set<ResourceLocation> dimensions = new LinkedHashSet<>();
        private int clusterMin = 2;
        private int clusterMax = 6;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder name(String langKey) {
            this.nameKey = langKey;
            return this;
        }

        public Builder texture(ResourceLocation texture) {
            this.texture = texture;
            return this;
        }

        public Builder texture(String texture) {
            return texture(new ResourceLocation(texture));
        }

        public Builder yield(int min, int max) {
            this.yieldMin = Math.max(0, Math.min(min, max));
            this.yieldMax = Math.max(0, Math.max(min, max));
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder dimension(String... dimensions) {
            for (String d : dimensions) {
                ResourceLocation rl = ResourceLocation.tryParse(d);
                if (rl != null) {
                    this.dimensions.add(rl);
                }
            }
            return this;
        }

        public Builder clusterSize(int min, int max) {
            this.clusterMin = min;
            this.clusterMax = max;
            return this;
        }

        public DepositType build() {
            return new DepositType(this);
        }

        /** Builds and registers the type into {@link WFDeposits}; returns it for further use. */
        public DepositType register() {
            return WFDeposits.register(build());
        }
    }
}
