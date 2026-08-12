package com.norwood.wfcore.radar.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public class CalibratorData extends SavedData {

    private static final String DATA_NAME = "wfcore_calibrators";

    private final LongOpenHashSet positions = new LongOpenHashSet();

    public CalibratorData() {}

    public CalibratorData(CompoundTag tag) {
        for (long packed : tag.getLongArray("positions")) {
            positions.add(packed);
        }
    }

    public static CalibratorData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CalibratorData::new, CalibratorData::new, DATA_NAME);
    }

    public void add(BlockPos pos) {
        if (positions.add(pos.asLong())) {
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            setDirty();
        }
    }

    public boolean hasWithin(int tx, int tz, int tol) {
        long tolSq = (long) tol * tol;
        for (LongIterator it = positions.iterator(); it.hasNext();) {
            BlockPos p = BlockPos.of(it.nextLong());
            long dx = p.getX() - tx;
            long dz = p.getZ() - tz;
            if (dx * dx + dz * dz <= tolSq) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("positions", positions.toLongArray());
        return tag;
    }
}
