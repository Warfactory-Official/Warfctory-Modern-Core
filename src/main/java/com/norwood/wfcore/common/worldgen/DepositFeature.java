package com.norwood.wfcore.common.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.deposit.DepositNode;
import com.norwood.wfcore.common.deposit.DepositRegion;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.common.machine.DepositBlockEntity;
import com.norwood.wfcore.config.WFCoreConfig;

/**
 * Places deposit clusters onto the bedrock floor, runs once per chunk. Three sources, in order:
 * <ol>
 * <li>explicit {@link DepositNode}s whose coordinates fall in this chunk;</li>
 * <li>{@link DepositRegion} quotas that deterministically chose this chunk for one of their cells;</li>
 * <li>ambient weighted scatter (config rarity gate; can be disabled).</li>
 * </ol>
 * Everything reads the runtime {@link WFDeposits} registry, weighted/filtered by the current dimension.
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
        boolean placed = false;

        for (DepositNode node : WFDeposits.nodesIn(dimension)) {
            if (!node.inChunk(chunkX, chunkZ)) {
                continue;
            }
            DepositType type = WFDeposits.get(node.type());
            if (type != null) {
                placed |= placeCluster(level, random, type, node.x(), node.z(),
                        rollSize(type, node.size(), node.size(), random), node.yield());
            }
        }

        for (DepositRegion region : WFDeposits.regionsIn(dimension)) {
            BlockPos spot = region.chosenOrigin(chunkX, chunkZ, seed);
            if (spot == null) {
                continue;
            }
            DepositType type = WFDeposits.get(region.type());
            if (type != null) {
                placed |= placeCluster(level, random, type, spot.getX(), spot.getZ(),
                        rollSize(type, region.minSize(), region.maxSize(), random), region.yield());
            }
        }

        if (WFCoreConfig.isDepositScatterEnabled()) {
            int rarity = WFCoreConfig.getDepositWorldgenRarity();
            if (rarity <= 1 || random.nextInt(rarity) == 0) {
                DepositType type = WFDeposits.weightedRandomFor(dimension, random);
                if (type != null) {
                    int size = rollSize(type, -1, -1, random);
                    placed |= placeCluster(level, random, type, origin.getX(), origin.getZ(), size, -1);
                }
            }
        }
        return placed;
    }

    /** Explicit size range overrides the type's; {@code -1} bounds fall back to the type's cluster range. */
    private static int rollSize(DepositType type, int min, int max, RandomSource random) {
        int lo = min > 0 ? min : type.clusterMin();
        int hi = max > 0 ? max : type.clusterMax();
        if (hi <= lo) {
            return lo;
        }
        return lo + random.nextInt(hi - lo + 1);
    }

    /** Stamp a {@code size}x{@code size} cluster; {@code fixedYield <= 0} rolls each block from the type. */
    private static boolean placeCluster(WorldGenLevel level, RandomSource random, DepositType type,
                                        int originX, int originZ, int size, int fixedYield) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placedAny = false;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = originX + dx;
                int z = originZ + dz;
                int cap = findBedrockCap(level, pos, x, z);
                if (cap == Integer.MIN_VALUE) {
                    continue;
                }
                pos.set(x, cap + 1, z);
                if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                    continue;
                }
                level.setBlock(pos, WFBlocks.DEPOSIT.get().defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof DepositBlockEntity deposit) {
                    deposit.init(type.id(), fixedYield > 0 ? fixedYield : type.rollYield(random));
                    placedAny = true;
                }
            }
        }
        return placedAny;
    }

    /** Highest bedrock Y in the bottom slab of this column, or {@link Integer#MIN_VALUE} if none. */
    private static int findBedrockCap(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int min = level.getMinBuildHeight();
        int cap = Integer.MIN_VALUE;
        for (int y = min; y <= min + 8; y++) {
            pos.set(x, y, z);
            if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                cap = y;
            }
        }
        return cap;
    }
}
