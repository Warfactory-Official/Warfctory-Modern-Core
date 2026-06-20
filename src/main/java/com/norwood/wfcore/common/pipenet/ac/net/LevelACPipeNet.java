package com.norwood.wfcore.common.pipenet.ac.net;

import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import com.norwood.wfcore.common.pipenet.ac.ACPipeProperties;

public class LevelACPipeNet extends LevelPipeNet<ACPipeProperties, ACPipeNet> {

    private static final String DATA_ID = "wfcore_ac_pipe_net";

    public static LevelACPipeNet getOrCreate(ServerLevel serverLevel) {
        return serverLevel.getDataStorage().computeIfAbsent(tag -> new LevelACPipeNet(serverLevel, tag),
                () -> new LevelACPipeNet(serverLevel), DATA_ID);
    }

    public LevelACPipeNet(ServerLevel serverLevel) {
        super(serverLevel);
    }

    public LevelACPipeNet(ServerLevel serverLevel, CompoundTag tag) {
        super(serverLevel, tag);
    }

    @Override
    protected ACPipeNet createNetInstance() {
        return new ACPipeNet(this);
    }
}
