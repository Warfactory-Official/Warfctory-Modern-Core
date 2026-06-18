package com.norwood.wfcore.radar.math;

import net.minecraft.nbt.CompoundTag;

/**
 * Axis-aligned (x, z) bounding box for a detected cluster.
 */
public class BoundingBox {

    private final IntCoord2 min, max;

    public BoundingBox(IntCoord2 min, IntCoord2 max) {
        this.min = min;
        this.max = max;
    }

    public IntCoord2 getMin() {
        return min;
    }

    public IntCoord2 getMax() {
        return max;
    }

    @Override
    public String toString() {
        return "{" + min + ", " + max + "}";
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("min", min.toNBT());
        tag.put("max", max.toNBT());
        return tag;
    }

    public static BoundingBox fromNBT(CompoundTag tag) {
        return new BoundingBox(IntCoord2.fromNBT(tag.getCompound("min")), IntCoord2.fromNBT(tag.getCompound("max")));
    }
}
