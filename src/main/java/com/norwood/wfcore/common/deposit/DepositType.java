package com.norwood.wfcore.common.deposit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.common.data.GTMaterials;
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
    private final String prospectorMaterial;
    private final ResourceLocation overlayTexture;
    private final int overlayColor;

    private DepositType(Builder b) {
        this.id = b.id;
        this.nameKey = b.nameKey != null ? b.nameKey : "wfcore.deposit." + b.id.getNamespace() + "." + b.id.getPath();
        this.texture = b.texture != null ? b.texture :
                new ResourceLocation(id.getNamespace(), "block/deposit/" + id.getPath());
        this.prospectorMaterial = b.prospectorMaterial;
        this.overlayTexture = b.overlayTexture;
        this.overlayColor = b.overlayColor;
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

    /**
     * Optional ore-overlay texture id ({@code ns:block/...}). When set, the BER draws the deposit as a bedrock
     * cube with this (transparent) overlay layered on top and tinted — mirroring how GregTech ore blocks
     * composite a material overlay over stone — instead of the flat single {@link #texture()}. {@code null}
     * keeps the single-texture look.
     */
    @javax.annotation.Nullable
    public ResourceLocation overlayTexture() {
        return overlayTexture;
    }

    /**
     * Tint applied to {@link #overlayTexture()} as {@code 0xRRGGBB}, or {@code -1} to derive it from the
     * {@link #prospectorMaterial()}'s GregTech colour (white if there is none).
     */
    public int overlayColor() {
        return overlayColor;
    }

    /**
     * The RGB tint (0xRRGGBB) for this deposit's ore overlay AND the drill's working particles: the explicit
     * {@link #overlayColor} if set, else the {@link #prospectorMaterial}'s GregTech colour, else white. Shared by
     * {@code DepositBlockEntityRenderer} and {@code DrillParticleHandler} so both read the same ore colour.
     */
    public int effectiveColor() {
        if (overlayColor >= 0) {
            return overlayColor & 0xFFFFFF;
        }
        if (prospectorMaterial != null) {
            Material material = GTMaterials.get(prospectorMaterial);
            if (material != null && !material.isNull()) {
                return material.getMaterialRGB() & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
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

    /**
     * GregTech material name (the path of its id, e.g. {@code pitchblende}) used to label this deposit on the
     * GregTech Ore Prospector, or {@code null} to show as the plain deposit block. Read by
     * {@link WFDepositProspector} from the prospector-scan mixin.
     */
    @javax.annotation.Nullable
    public String prospectorMaterial() {
        return prospectorMaterial;
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
        private String prospectorMaterial;
        private ResourceLocation overlayTexture;
        private int overlayColor = -1;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        public Builder name(String langKey) {
            this.nameKey = langKey;
            return this;
        }


        public Builder texture(String texture) {
            this.texture = new ResourceLocation(texture);
            return this;
        }


        public Builder overlay(String overlayTexture) {
            this.overlayTexture = new ResourceLocation(overlayTexture);
            return this;
        }

        public Builder overlayColor(int rgb) {
            this.overlayColor = rgb;
            return this;
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


        public Builder prospectorMaterial(String material) {
            if (material == null || material.isEmpty()) {
                this.prospectorMaterial = null;
                return this;
            }
            int colon = material.indexOf(':');
            this.prospectorMaterial = (colon >= 0 ? material.substring(colon + 1) : material)
                    .toLowerCase(java.util.Locale.ROOT);
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
