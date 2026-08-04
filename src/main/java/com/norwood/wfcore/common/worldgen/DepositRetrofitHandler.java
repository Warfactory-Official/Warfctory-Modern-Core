package com.norwood.wfcore.common.worldgen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.norwood.wfcore.common.deposit.DepositRetrofitState;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.config.WFCoreConfig;


public final class DepositRetrofitHandler {

    public static final DepositRetrofitHandler INSTANCE = new DepositRetrofitHandler();

    private DepositRetrofitHandler() {}

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getLevel().isClientSide()) {
            return;
        }
        if (!WFCoreConfig.isDepositWorldgenEnabled() || !WFCoreConfig.isDepositHealEnabled()
                || !WFDeposits.hasPlacements()) {
            return;
        }
        ResourceLocation dimension = level.dimension().location();
        if (WFDeposits.nodesIn(dimension).isEmpty() && WFDeposits.regionsIn(dimension).isEmpty()) {
            return;
        }

        ChunkPos pos = event.getChunk().getPos();
        long key = pos.toLong();
        DepositRetrofitState state = DepositRetrofitState.get(level);
        if (state.isProcessed(key)) {
            return;
        }

        long seed = level.getSeed();
        RandomSource random =
                RandomSource.create(seed ^ ((long) pos.x * 341873128712L) ^ ((long) pos.z * 132897987541L));
        DepositPlacer.Result result = DepositPlacer.placeExplicit(level, random, dimension, pos.x, pos.z, seed, true);

        if (result.hosted()) {
            state.markProcessed(key);
        }
    }
}
