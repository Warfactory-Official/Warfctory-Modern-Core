package com.norwood.wfcore.common.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.config.WFCoreConfig;

/**
 * Places deposit clusters onto the bedrock floor, runs once per chunk when it first generates. Three sources, in
 * order:
 * <ol>
 * <li>explicit {@link com.norwood.wfcore.common.deposit.DepositNode}s whose coordinates fall in this chunk;</li>
 * <li>{@link com.norwood.wfcore.common.deposit.DepositRegion} quotas that deterministically chose this chunk for
 * one of their cells;</li>
 * <li>ambient weighted scatter (config rarity gate; can be disabled).</li>
 * </ol>
 * The node/region placement is shared with the retro-fit paths via {@link DepositPlacer}; only scatter is unique
 * to worldgen (it is not reproducible, so it is never retro-fit).
 */
public class DepositFeature extends Feature<NoneFeatureConfiguration> {

    public DepositFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!WFCoreConfig.isDepositWorldgenEnabled()) {
            return false;
        }
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        long seed = level.getSeed();
        ResourceLocation dimension = level.getLevel().dimension().location();
        BlockPos origin = ctx.origin();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;

        // Nodes + regions (shared with retro-fit); worldgen writes into the fresh chunk freely (retrofit = false).
        DepositPlacer.Result explicit =
                DepositPlacer.placeExplicit(level, random, dimension, chunkX, chunkZ, seed, false);
        boolean placed = explicit.placedBlocks() > 0;

        if (WFCoreConfig.isDepositScatterEnabled()) {
            int rarity = WFCoreConfig.getDepositWorldgenRarity();
            if (rarity <= 1 || random.nextInt(rarity) == 0) {
                DepositType type = WFDeposits.weightedRandomFor(dimension, random);
                if (type != null) {
                    int size = DepositPlacer.rollSize(type, -1, -1, random);
                    placed |= DepositPlacer.placeCluster(level, random, type,
                            origin.getX(), origin.getZ(), size, -1, false) > 0;
                }
            }
        }
        return placed;
    }
}
