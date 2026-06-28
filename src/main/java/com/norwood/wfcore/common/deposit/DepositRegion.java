package com.norwood.wfcore.common.deposit;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

/**
 * A quota of {@code count} deposits of {@code type} spread over a rectangular block region. The region's chunks
 * are partitioned into {@code count} cells; each cell deterministically places exactly one deposit (seeded by the
 * world seed so it's stable and never double-placed), so the region reliably reaches its quota as the area
 * generates. {@code count} is effectively capped at the number of chunks in the region. Registered via
 * {@code WFDeposits.region(...)}.
 */
public final class DepositRegion {

    private final ResourceLocation type;
    private final ResourceLocation dimension;
    private final int x1;
    private final int z1;
    private final int x2;
    private final int z2;
    private final int count;
    private final int minSize;
    private final int maxSize;
    private final int yield;
    private final long salt;

    private DepositRegion(Builder b) {
        this.type = b.type;
        this.dimension = b.dimension != null ? b.dimension : new ResourceLocation("minecraft", "overworld");
        this.x1 = Math.min(b.x1, b.x2);
        this.z1 = Math.min(b.z1, b.z2);
        this.x2 = Math.max(b.x1, b.x2);
        this.z2 = Math.max(b.z1, b.z2);
        this.count = Math.max(1, b.count);
        this.minSize = b.minSize;
        this.maxSize = b.maxSize;
        this.yield = b.yield;
        this.salt = ((long) this.x1 * 73856093L) ^ ((long) this.z1 * 19349663L) ^ ((long) this.x2 * 83492791L) ^
                ((long) this.z2 * 49979687L) ^ ((long) this.count * 1610612741L) ^ type.toString().hashCode();
    }

    public ResourceLocation type() {
        return type;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public int minSize() {
        return minSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public int yield() {
        return yield;
    }

    /**
     * If the given chunk is the one chosen to host a deposit for one of this region's cells, return the cluster
     * origin (block x/z) within it; otherwise {@code null}.
     */
    @Nullable
    public BlockPos chosenOrigin(int chunkX, int chunkZ, long worldSeed) {
        int rcx1 = x1 >> 4;
        int rcz1 = z1 >> 4;
        int rcx2 = x2 >> 4;
        int rcz2 = z2 >> 4;
        int wChunks = rcx2 - rcx1 + 1;
        int hChunks = rcz2 - rcz1 + 1;
        int lcx = chunkX - rcx1;
        int lcz = chunkZ - rcz1;
        if (lcx < 0 || lcz < 0 || lcx >= wChunks || lcz >= hChunks) {
            return null;
        }

        int cols = (int) Math.round(Math.sqrt((double) count * wChunks / hChunks));
        cols = Mth.clamp(cols, 1, wChunks);
        int rows = Math.min(hChunks, (count + cols - 1) / cols);
        int activeCells = Math.min(count, cols * rows);

        int col = (int) ((long) lcx * cols / wChunks);
        int row = (int) ((long) lcz * rows / hChunks);
        int cellIndex = row * cols + col;
        if (cellIndex >= activeCells) {
            return null;
        }

        int cellCx1 = rcx1 + (int) ((long) col * wChunks / cols);
        int cellCx2 = rcx1 + (int) ((long) (col + 1) * wChunks / cols) - 1;
        int cellCz1 = rcz1 + (int) ((long) row * hChunks / rows);
        int cellCz2 = rcz1 + (int) ((long) (row + 1) * hChunks / rows) - 1;

        long h = mix(worldSeed ^ salt ^ ((long) cellIndex * 0x9E3779B97F4A7C15L));
        int chosenCx = cellCx1 + (int) Math.floorMod(h, cellCx2 - cellCx1 + 1);
        int chosenCz = cellCz1 + (int) Math.floorMod(h >>> 21, cellCz2 - cellCz1 + 1);
        if (chosenCx != chunkX || chosenCz != chunkZ) {
            return null;
        }

        int bx = (chunkX << 4) + (int) Math.floorMod(h >>> 40, 16);
        int bz = (chunkZ << 4) + (int) Math.floorMod(mix(h) >>> 40, 16);
        bx = Mth.clamp(bx, x1, x2);
        bz = Mth.clamp(bz, z1, z2);
        return new BlockPos(bx, 0, bz);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static Builder builder(ResourceLocation type) {
        return new Builder(type);
    }

    public static final class Builder {

        private final ResourceLocation type;
        private ResourceLocation dimension;
        private int x1;
        private int z1;
        private int x2;
        private int z2;
        private int count = 1;
        private int minSize = -1;
        private int maxSize = -1;
        private int yield = -1;

        private Builder(ResourceLocation type) {
            this.type = type;
        }

        public Builder dimension(String dimension) {
            ResourceLocation rl = ResourceLocation.tryParse(dimension);
            if (rl != null) {
                this.dimension = rl;
            }
            return this;
        }

        public Builder from(int x, int z) {
            this.x1 = x;
            this.z1 = z;
            return this;
        }

        public Builder to(int x, int z) {
            this.x2 = x;
            this.z2 = z;
            return this;
        }

        public Builder count(int count) {
            this.count = Math.max(1, count);
            return this;
        }

        public Builder size(int size) {
            return size(size, size);
        }

        public Builder size(int min, int max) {
            this.minSize = Math.max(2, Math.min(DepositNode.MAX_EXPLICIT_SIZE, Math.min(min, max)));
            this.maxSize = Math.max(2, Math.min(DepositNode.MAX_EXPLICIT_SIZE, Math.max(min, max)));
            return this;
        }

        public Builder yield(int yield) {
            this.yield = Math.max(1, yield);
            return this;
        }

        public DepositRegion build() {
            return new DepositRegion(this);
        }

        public DepositRegion register() {
            DepositRegion region = build();
            WFDeposits.registerRegion(region);
            return region;
        }
    }
}
