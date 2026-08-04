package com.norwood.wfcore.common.deposit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;


public class DepositRetrofitState extends SavedData {

    private static final String DATA_NAME = "wfcore_deposit_retrofit";

    private final LongOpenHashSet processed = new LongOpenHashSet();

    public DepositRetrofitState() {}

    public DepositRetrofitState(CompoundTag tag) {
        this();
        for (long key : tag.getLongArray("processed")) {
            processed.add(key);
        }
    }

    public static DepositRetrofitState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DepositRetrofitState::new, DepositRetrofitState::new, DATA_NAME);
    }

    public boolean isProcessed(long chunkKey) {
        return processed.contains(chunkKey);
    }

    public boolean markProcessed(long chunkKey) {
        if (processed.add(chunkKey)) {
            setDirty();
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray("processed", processed.toLongArray());
        return tag;
    }
}
