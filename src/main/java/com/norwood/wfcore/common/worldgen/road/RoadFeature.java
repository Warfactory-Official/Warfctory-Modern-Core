package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * {@link RoadRasterizer} over a {@link WorldGenLevel}. Once per chunk: {@code applyBiomeDecoration} dedupes a
 * placement present in every biome to one call at the chunk origin.
 */
public class RoadFeature extends Feature<NoneFeatureConfiguration> {

    private static final ThreadLocal<IntArrayList> SCRATCH = ThreadLocal.withInitial(() -> new IntArrayList(8));

    public RoadFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RoadIndex index = RoadIndexManager.get(level.getLevel());
        if (index.isEmpty()) {
            return false;
        }
        BlockPos origin = context.origin();
        int minX = origin.getX() & ~15;
        int minZ = origin.getZ() & ~15;
        // One biome per chunk: themes change on chunk borders, not per block across a bridge.
        int centreX = minX + 8;
        int centreZ = minZ + 8;
        Holder<Biome> biome = level.getBiome(new BlockPos(centreX,
                level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centreX, centreZ), centreZ));
        return new RoadRasterizer(index, new LevelCanvas(index, level, context.random(), biome))
                .chunk(minX, minZ, SCRATCH.get());
    }

    private static final class LevelCanvas implements RoadRasterizer.Canvas {

        private final RoadIndex index;
        private final WorldGenLevel level;
        private final RandomSource random;
        private final Holder<Biome> biome;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos along = new BlockPos.MutableBlockPos();
        private final Map<RoadType, RoadType.Palettes> themed = new IdentityHashMap<>(2);

        LevelCanvas(RoadIndex index, WorldGenLevel level, RandomSource random, Holder<Biome> biome) {
            this.index = index;
            this.level = level;
            this.random = random;
            this.biome = biome;
        }

        @Override
        public int minY() {
            return level.getMinBuildHeight();
        }

        @Override
        public int maxY() {
            return level.getMaxBuildHeight();
        }

        /** {@code *_WG} heightmaps freeze after SURFACE => trees and mushrooms invisible. */
        @Override
        public int top(int x, int z) {
            return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        }

        @Override
        public int cell(int x, int y, int z) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir()) {
                return AIR | HOLLOW;
            }
            int flags = state.is(Blocks.BEDROCK) ? BEDROCK : 0;
            return !state.getFluidState().isEmpty() || state.canBeReplaced() ? flags | HOLLOW : flags;
        }

        @Override
        public boolean vegetation(int x, int y, int z) {
            return RoadSweep.clearable(level.getBlockState(pos.set(x, y, z)));
        }

        @Override
        public void clear(int x, int y, int z) {
            level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }

        @Override
        public void place(int x, int y, int z, RoadRasterizer.Part part, RoadIndex.LinkGeometry link, int station,
                int connections) {
            RoadType type = index.type(link);
            RoadType.Palettes palettes = themed.computeIfAbsent(type, key -> key.palettesFor(biome));
            // Surface slots sample at the centreline station: noise providers keep their pattern along the road.
            along.set(link.x(station), 0, link.z(station));
            pos.set(x, y, z);
            BlockState state = switch (part) {
                case SURFACE -> type.deck().surface().getState(random, along);
                case EDGE -> type.deck().edge().getState(random, along);
                case MARKING -> type.deck().markings().orElseThrow().block().getState(random, along);
                case BRIDGE_DECK -> type.bridge().deck().orElse(type.deck().surface()).getState(random, along);
                case BASE -> type.earthworks().base().getState(random, pos);
                case EMBANKMENT -> type.earthworks().embankment().getState(random, pos);
                case STRUCTURE -> palettes.structure().getState(random, pos);
                case ACCENT -> palettes.accent().getState(random, pos);
                case WALL -> palettes.wall().getState(random, pos);
                case RAILING -> connect(type.bridge().railing().orElseThrow().getState(random, pos), connections);
                case TUNNEL_FLOOR -> type.tunnel().floor().orElse(type.deck().edge()).getState(random, pos);
                case CEILING -> type.tunnel().ceiling().orElse(palettes.wall()).getState(random, pos);
                case LIGHT -> type.tunnel().light().orElseThrow().getState(random, pos);
            };
            level.setBlock(pos, state, 2);
        }

        /** Worldgen writes skip neighbour updates => connections baked from geometry, else loose posts. */
        private static BlockState connect(BlockState state, int connections) {
            boolean north = (connections & 1) != 0;
            boolean east = (connections & 2) != 0;
            boolean south = (connections & 4) != 0;
            boolean west = (connections & 8) != 0;
            if (state.hasProperty(BlockStateProperties.NORTH_WALL)) {
                boolean straight = (north && south && !east && !west) || (east && west && !north && !south);
                return state.setValue(BlockStateProperties.NORTH_WALL, north ? WallSide.LOW : WallSide.NONE)
                        .setValue(BlockStateProperties.EAST_WALL, east ? WallSide.LOW : WallSide.NONE)
                        .setValue(BlockStateProperties.SOUTH_WALL, south ? WallSide.LOW : WallSide.NONE)
                        .setValue(BlockStateProperties.WEST_WALL, west ? WallSide.LOW : WallSide.NONE)
                        .setValue(BlockStateProperties.UP, !straight);
            }
            if (state.hasProperty(BlockStateProperties.NORTH)) {
                return state.setValue(BlockStateProperties.NORTH, north)
                        .setValue(BlockStateProperties.EAST, east)
                        .setValue(BlockStateProperties.SOUTH, south)
                        .setValue(BlockStateProperties.WEST, west);
            }
            return state;
        }
    }
}
