package com.norwood.wfcore.radar.math;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import org.apache.commons.math3.ml.clustering.Clusterable;

/**
 * Simple integer (x, z) tuple. Implements {@link Clusterable} for use with DBSCAN.
 */
public class IntCoord2 implements Clusterable {

    private final int x, z;

    public IntCoord2(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public IntCoord2(BlockPos pos) {
        this.x = pos.getX();
        this.z = pos.getZ();
    }

    public IntCoord2(long packed) {
        this.x = (int) (packed >> 32);
        this.z = (int) packed;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    @Override
    public double[] getPoint() {
        return new double[] { x, z };
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ")";
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cx", x);
        tag.putInt("cz", z);
        return tag;
    }

    public static IntCoord2 fromNBT(CompoundTag tag) {
        return new IntCoord2(tag.getInt("cx"), tag.getInt("cz"));
    }
}
