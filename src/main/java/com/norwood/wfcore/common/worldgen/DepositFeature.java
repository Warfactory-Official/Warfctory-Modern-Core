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
import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;
import com.norwood.wfcore.config.WFCoreConfig;

import java.util.List;

/**
 * Places deposit clusters onto the bedrock floor, runs once per chunk when it first generates. Sources, in
 * order:
 * <ol>
 * <li>explicit {@link com.norwood.wfcore.common.deposit.DepositNode}s whose coordinates fall in this chunk;</li>
 * <li>{@link com.norwood.wfcore.common.deposit.DepositRegion} quotas that deterministically chose this chunk for
 * one of their cells;</li>
 * <li>allocation paint / assign for this chunk: one cluster of the pick (empty pick => nothing), replacing
 * scatter;</li>
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

        AllocationIndex allocation = AllocationIndexManager.get(level.getLevel());
        List<AllocationIndex.Pick> picks = allocation.picks(ResourceSystem.DEPOSIT, chunkX, chunkZ);
        if (picks != null) {
            AllocationIndex.Pick pick = picks.getFirst();
            DepositType type = pick.target().isEmpty() ? null : WFDeposits.get(ResourceLocation.parse(pick.target()));
            if (type != null) {
                placed |= DepositPlacer.placeCluster(level, random, type, origin.getX(), origin.getZ(),
                        DepositPlacer.rollSize(type, -1, -1, random), -1, pick.richness(), false) > 0;
            }
        } else if (WFCoreConfig.isDepositScatterEnabled()) {
            int rarity = WFCoreConfig.getDepositWorldgenRarity();
            if (allocation.has(ResourceSystem.DEPOSIT)) {
                placed |= scatterAllocated(level, random, dimension, origin, rarity, allocation);
            } else if (rarity <= 1 || random.nextInt(rarity) == 0) {
                DepositType type = WFDeposits.weightedRandomFor(dimension, random);
                if (type != null) {
                    int size = DepositPlacer.rollSize(type, -1, -1, random);
                    placed |= DepositPlacer.placeCluster(level, random, type,
                            origin.getX(), origin.getZ(), size, -1, 1.0, false) > 0;
                }
            }
        }
        return placed;
    }

    /**
     * Expected clusters/chunk = {@code sum(weight*m) / sum(weight) / rarity}: m = 1 everywhere => stock rate and
     * mix; m scales a type's local rate without stealing from the others.
     */
    private static boolean scatterAllocated(WorldGenLevel level, RandomSource random, ResourceLocation dimension,
                                            BlockPos origin, int rarity, AllocationIndex allocation) {
        List<DepositType> types = WFDeposits.all().stream().filter(t -> t.generatesIn(dimension)).toList();
        double cx = origin.getX() + 8.0;
        double cz = origin.getZ() + 8.0;
        double[] weights = new double[types.size()];
        double stock = 0.0;
        double total = 0.0;
        for (int i = 0; i < weights.length; i++) {
            DepositType type = types.get(i);
            weights[i] = type.weight() * allocation.multiplier(ResourceSystem.DEPOSIT, type.id().toString(), cx, cz);
            stock += type.weight();
            total += weights[i];
        }
        if (total <= 0.0) {
            return false;
        }
        double expected = total / stock / Math.max(1, rarity);
        int clusters = (int) expected;
        if (random.nextDouble() < expected - clusters) {
            clusters++;
        }
        boolean placed = false;
        for (int c = 0; c < clusters; c++) {
            double roll = random.nextDouble() * total;
            int pick = 0;
            while (pick < weights.length - 1 && (roll -= weights[pick]) >= 0.0) {
                pick++;
            }
            DepositType type = types.get(pick);
            int size = DepositPlacer.rollSize(type, -1, -1, random);
            int x = c == 0 ? origin.getX() : origin.getX() + random.nextInt(16);
            int z = c == 0 ? origin.getZ() : origin.getZ() + random.nextInt(16);
            placed |= DepositPlacer.placeCluster(level, random, type, x, z, size, -1,
                    allocation.richness(ResourceSystem.DEPOSIT, type.id().toString(), cx, cz), false) > 0;
        }
        return placed;
    }
}
