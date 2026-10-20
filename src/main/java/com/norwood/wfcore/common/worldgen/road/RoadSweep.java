package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

import com.gregtechceu.gtceu.common.block.SurfaceRockBlock;
import com.norwood.wfcore.WFCore;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * {@link RoadRasterizer#sweep} at the head of the LIGHT step: LIGHT needs all 8 neighbours past FEATURES, and only
 * they write into a chunk => its decoration is final. Covers GT ore indicators (TAIL of
 * {@code applyBiomeDecoration}, after the road) and neighbour trees / vines.
 */
public final class RoadSweep {

    public static final TagKey<Block> CLEARABLE = TagKey.create(Registries.BLOCK, WFCore.id("road_clearable"));

    private static final ThreadLocal<IntArrayList> SCRATCH = ThreadLocal.withInitial(() -> new IntArrayList(8));

    private RoadSweep() {}

    static boolean clearable(BlockState state) {
        return state.is(CLEARABLE) || state.getBlock() instanceof SurfaceRockBlock;
    }

    public static void sweep(ServerLevel level, ChunkAccess chunk) {
        // Imposter = already FULL: swept on its first pass.
        if (!(chunk instanceof ProtoChunk proto) || chunk instanceof ImposterProtoChunk) {
            return;
        }
        RoadIndex index = RoadIndexManager.get(level);
        if (index.isEmpty()) {
            return;
        }
        new RoadRasterizer(index, new ChunkCanvas(proto))
                .sweep(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(), SCRATCH.get());
    }

    /** Own chunk only; {@code ProtoChunk.setBlockState} past INITIALIZE_LIGHT queues the light update. */
    private record ChunkCanvas(ProtoChunk chunk, BlockPos.MutableBlockPos pos) implements RoadRasterizer.Canvas {

        ChunkCanvas(ProtoChunk chunk) {
            this(chunk, new BlockPos.MutableBlockPos());
        }

        @Override
        public int minY() {
            return chunk.getMinBuildHeight();
        }

        @Override
        public int maxY() {
            return chunk.getMaxBuildHeight();
        }

        @Override
        public int top(int x, int z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int cell(int x, int y, int z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean vegetation(int x, int y, int z) {
            return clearable(chunk.getBlockState(pos.set(x, y, z)));
        }

        @Override
        public void clear(int x, int y, int z) {
            chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
        }

        @Override
        public void place(int x, int y, int z, RoadRasterizer.Part part, RoadIndex.LinkGeometry link, int station,
                int connections) {
            throw new UnsupportedOperationException();
        }
    }
}
