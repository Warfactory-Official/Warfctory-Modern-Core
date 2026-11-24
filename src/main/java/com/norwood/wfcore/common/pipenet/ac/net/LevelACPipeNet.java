package com.norwood.wfcore.common.pipenet.ac.net;

import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import com.norwood.wfcore.common.pipenet.ac.ACPipeProperties;
import com.norwood.wfcore.common.world.LevelSavedCache;

public class LevelACPipeNet extends LevelPipeNet<ACPipeProperties, ACPipeNet> {

    private static final String DATA_ID = "wfcore_ac_pipe_net";

    private static final LevelSavedCache<LevelACPipeNet> CACHE = LevelSavedCache.perLevel(DATA_ID,
            level -> new SavedData.Factory<>(() -> new LevelACPipeNet(level),
                    (tag, provider) -> new LevelACPipeNet(level, tag, provider)));

    public static LevelACPipeNet getOrCreate(ServerLevel serverLevel) {
        return CACHE.get(serverLevel);
    }

    public LevelACPipeNet(ServerLevel serverLevel) {
        super(serverLevel);
    }

    public LevelACPipeNet(ServerLevel serverLevel, CompoundTag tag, HolderLookup.Provider provider) {
        super(serverLevel, tag, provider);
    }

    @Override
    protected ACPipeNet createNetInstance() {
        return new ACPipeNet(this);
    }
}
