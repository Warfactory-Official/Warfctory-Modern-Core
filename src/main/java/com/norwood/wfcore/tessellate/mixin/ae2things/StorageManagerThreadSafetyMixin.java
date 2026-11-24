package com.norwood.wfcore.tessellate.mixin.ae2things;

import com.norwood.wfcore.tessellate.ae2things.DiskStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** {@code patches/ae2things/0001}; {@code synchronized}/{@code volatile}/{@code final} flags: {@code Ae2ThingsTransformer}. */
@Mixin(targets = "io.github.projectet.ae2things.util.StorageManager", remap = false)
public abstract class StorageManagerThreadSafetyMixin extends SavedData {

    @Unique
    private final AtomicLong tessellate$version = new AtomicLong();

    /** A {@code setDirty(false)} after the file write would drop a mutation landing mid-write. */
    @Override
    public void setDirty(boolean dirty) {
        if (dirty) {
            tessellate$version.incrementAndGet();
        }
        super.setDirty(dirty);
    }

    @Override
    public void save(File file, HolderLookup.Provider registries) {
        long seen = tessellate$version.get();
        super.save(file, registries);
        if (tessellate$version.get() != seen) {
            super.setDirty(true);
        }
    }

    /** Monitor: {@code Ae2ThingsTransformer}. */
    @Overwrite
    public void modifyDisk(UUID diskId, ListTag stackKeys, long[] stackAmounts, long itemCount) {
        DiskStorage.modify(this, diskId, stackKeys, stackAmounts, itemCount);
    }
}
