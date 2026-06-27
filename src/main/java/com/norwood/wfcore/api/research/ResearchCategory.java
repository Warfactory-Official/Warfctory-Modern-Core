package com.norwood.wfcore.api.research;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * A research category — one tab in the research GUI, holding its own tree (or several parallel trees). Carries
 * the tab's label/icon and the theming for its page: an optional tiled background texture or solid background
 * colour, and the colour of the prerequisite connector lines. Defined from Java or KubeJS via
 * {@link #builder(String)}; researches join a category through {@link Research.Builder#category(String)}.
 */
public final class ResearchCategory {

    public static final int DEFAULT_CONNECTOR_COLOR = 0xFF8A8A8A;

    private final String id;
    private final String nameKey;
    private final ItemStack icon;
    @Nullable
    private final ResourceLocation backgroundTexture;
    private final int backgroundColor;
    private final int connectorColor;

    private ResearchCategory(Builder b) {
        this.id = b.id;
        this.nameKey = b.nameKey != null ? b.nameKey : "wfcore.research.category." + b.id;
        this.icon = b.icon == null ? ItemStack.EMPTY : b.icon;
        this.backgroundTexture = b.backgroundTexture;
        this.backgroundColor = b.backgroundColor;
        this.connectorColor = b.connectorColor;
    }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        return nameKey;
    }

    public ItemStack getIcon() {
        return icon;
    }

    /** Tiled canvas background texture, or {@code null} to fall back to {@link #getBackgroundColor()}. */
    @Nullable
    public ResourceLocation getBackgroundTexture() {
        return backgroundTexture;
    }

    /** Solid canvas background colour (ARGB), or {@code 0} for the default stone texture. */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /** Colour (ARGB) of the prerequisite connector lines on this tab. */
    public int getConnectorColor() {
        return connectorColor;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** A default category for an id that a research references but that was never explicitly registered. */
    public static ResearchCategory createDefault(String id) {
        return new Builder(id).build();
    }

    public static final class Builder {

        private final String id;
        private String nameKey;
        private ItemStack icon;
        @Nullable
        private ResourceLocation backgroundTexture;
        private int backgroundColor;
        private int connectorColor = DEFAULT_CONNECTOR_COLOR;

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String langKey) {
            this.nameKey = langKey;
            return this;
        }

        public Builder icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        public Builder background(ResourceLocation texture) {
            this.backgroundTexture = texture;
            return this;
        }

        public Builder background(String texture) {
            this.backgroundTexture = new ResourceLocation(texture);
            return this;
        }

        /** Solid canvas background (ARGB). Accepts {@code long} so KubeJS full-alpha literals like 0xFF101814 work. */
        public Builder backgroundColor(long argb) {
            this.backgroundColor = (int) argb;
            return this;
        }

        /** Connector line colour (ARGB). Accepts {@code long} so KubeJS full-alpha literals like 0xFF60C060 work. */
        public Builder connectorColor(long argb) {
            this.connectorColor = (int) argb;
            return this;
        }

        public ResearchCategory build() {
            return new ResearchCategory(this);
        }

        /** Builds and adds to the {@link ResearchCategoryRegistry}, returning the built category. */
        public ResearchCategory register() {
            ResearchCategory category = build();
            ResearchCategoryRegistry.register(category);
            return category;
        }
    }
}
