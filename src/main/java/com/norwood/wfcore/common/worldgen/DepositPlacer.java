package com.norwood.wfcore.common.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.deposit.DepositNode;
import com.norwood.wfcore.common.deposit.DepositRegion;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.common.machine.DepositBlockEntity;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.config.WFCoreConfig;

import java.util.ArrayList;
import java.util.List;


public final class DepositPlacer {

    private DepositPlacer() {}

    /** Outcome of one explicit-placement pass over a single chunk. */
    public record Result(int hostedClusters, int placedBlocks) {

        public static final Result EMPTY = new Result(0, 0);

        public boolean hosted() {
            return hostedClusters > 0;
        }
    }


    public static Result placeExplicit(WorldGenLevel level, RandomSource random, ResourceLocation dimension,
                                       int chunkX, int chunkZ, long seed, boolean retrofit) {
        int hosted = 0;
        int placed = 0;
        AllocationIndex allocation = AllocationIndexManager.get(level.getLevel());
        for (DepositNode node : concat(WFDeposits.nodesIn(dimension), allocation.depositNodes())) {
            if (!node.inChunk(chunkX, chunkZ)) {
                continue;
            }
            DepositType type = WFDeposits.get(node.type());
            if (type == null) {
                continue;
            }
            hosted++;
            placed += placeCluster(level, random, type, node.x(), node.z(),
                    rollSize(type, node.size(), node.size(), random), node.yield(), 1.0, retrofit);
        }
        for (DepositRegion region : concat(WFDeposits.regionsIn(dimension), allocation.depositRegions())) {
            BlockPos spot = region.chosenOrigin(chunkX, chunkZ, seed);
            if (spot == null) {
                continue;
            }
            DepositType type = WFDeposits.get(region.type());
            if (type == null) {
                continue;
            }
            hosted++;
            placed += placeCluster(level, random, type, spot.getX(), spot.getZ(),
                    rollSize(type, region.minSize(), region.maxSize(), random), region.yield(), 1.0, retrofit);
        }
        return hosted == 0 ? Result.EMPTY : new Result(hosted, placed);
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        if (b.isEmpty()) {
            return a;
        }
        List<T> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    public static int rollSize(DepositType type, int min, int max, RandomSource random) {
        int lo = min > 0 ? min : type.clusterMin();
        int hi = max > 0 ? max : type.clusterMax();
        if (hi <= lo) {
            return lo;
        }
        return lo + random.nextInt(hi - lo + 1);
    }


    /** richness scales each block's yield (fixed or rolled), at least 1. */
    public static int placeCluster(WorldGenLevel level, RandomSource random, DepositType type,
                                   int originX, int originZ, int size, int fixedYield, double richness,
                                   boolean retrofit) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int x = originX + dx;
                int z = originZ + dz;
                if (retrofit && !level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int cap = findBedrockCap(level, pos, x, z);
                if (cap == Integer.MIN_VALUE) {
                    continue;
                }
                pos.set(x, cap + 1, z);
                BlockState current = level.getBlockState(pos);
                if (current.is(Blocks.BEDROCK)) {
                    continue;
                }
                if (retrofit && !isReplaceableForRetrofit(current)) {
                    continue;
                }
                level.setBlock(pos, WFBlocks.DEPOSIT.get().defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof DepositBlockEntity deposit) {
                    int yield = fixedYield > 0 ? fixedYield : type.rollYield(random);
                    deposit.init(type.id(), richness == 1.0 ? yield : Math.max(1, (int) Math.round(yield * richness)));
                    placed++;
                }
            }
        }
        if (placed > 0 && WFCoreConfig.isDepositLogPlacements()) {
            com.norwood.wfcore.WFCore.LOGGER.info("[deposit] placed {} cluster size {} at x={} z={}",
                    type.id(), size, originX, originZ);
        }
        return placed;
    }


    private static boolean isReplaceableForRetrofit(BlockState state) {
        return state.isAir()
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.STONE)
                || state.is(Blocks.TUFF);
    }

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
