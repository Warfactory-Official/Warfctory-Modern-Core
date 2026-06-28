package com.norwood.wfcore.common.deposit;

import net.minecraft.resources.ResourceLocation;

/**
 * A forced deposit at exact world coordinates: when the chunk containing {@code (x, z)} generates, a cluster of
 * {@code type} is stamped there on the bedrock floor (the Y in the request is nominal — deposits always sit on
 * bedrock). Use for hand-placed landmark patches. Registered via {@code WFDeposits.node(...)}.
 */
public final class DepositNode {

    /** Explicit cluster size for nodes/regions may exceed the random-scatter 6 cap, up to this. */
    public static final int MAX_EXPLICIT_SIZE = 16;

    private final ResourceLocation type;
    private final ResourceLocation dimension;
    private final int x;
    private final int z;
    private final int size;
    private final int yield;

    private DepositNode(Builder b) {
        this.type = b.type;
        this.dimension = b.dimension != null ? b.dimension : new ResourceLocation("minecraft", "overworld");
        this.x = b.x;
        this.z = b.z;
        this.size = b.size;
        this.yield = b.yield;
    }

    public ResourceLocation type() {
        return type;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    /** Explicit size (clamped), or {@code -1} to roll from the deposit type's cluster range. */
    public int size() {
        return size;
    }

    /** Fixed per-block yield, or {@code -1} to roll from the deposit type's yield range. */
    public int yield() {
        return yield;
    }

    public boolean inChunk(int chunkX, int chunkZ) {
        return (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }

    public static Builder builder(ResourceLocation type, int x, int z) {
        return new Builder(type, x, z);
    }

    public static final class Builder {

        private final ResourceLocation type;
        private ResourceLocation dimension;
        private final int x;
        private final int z;
        private int size = -1;
        private int yield = -1;

        private Builder(ResourceLocation type, int x, int z) {
            this.type = type;
            this.x = x;
            this.z = z;
        }

        public Builder dimension(String dimension) {
            ResourceLocation rl = ResourceLocation.tryParse(dimension);
            if (rl != null) {
                this.dimension = rl;
            }
            return this;
        }

        public Builder size(int size) {
            this.size = Math.max(2, Math.min(MAX_EXPLICIT_SIZE, size));
            return this;
        }

        public Builder yield(int yield) {
            this.yield = Math.max(1, yield);
            return this;
        }

        public DepositNode build() {
            return new DepositNode(this);
        }

        public DepositNode register() {
            DepositNode node = build();
            WFDeposits.registerNode(node);
            return node;
        }
    }
}
