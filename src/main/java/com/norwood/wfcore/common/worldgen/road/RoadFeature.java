package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.common.Tags;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.IdentityHashMap;
import java.util.List;
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
        public void turf(int x, int y, int z, int fromY, boolean fill, RoadIndex.LinkGeometry link, int station) {
            BlockState source = level.getBlockState(pos.set(x, fromY, z));
            pos.set(x, y, z);
            if (source.hasProperty(BlockStateProperties.SNOWY)) {
                source = source.setValue(BlockStateProperties.SNOWY, false);
            }
            if (!fill) {
                // Rock exposed by a cut stays rock.
                if (source.is(BlockTags.DIRT) && level.getBlockState(pos).is(BlockTags.DIRT)) {
                    level.setBlock(pos, source, 2);
                }
                return;
            }
            boolean natural = source.isSolidRender(level, pos) && !source.hasBlockEntity()
                    && !source.is(Tags.Blocks.ORES);
            level.setBlock(pos, natural ? source
                    : index.type(link).earthworks().embankment().getState(random, pos), 2);
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
                case TRUSS -> type.bridge().truss().orElseThrow().getState(random, pos);
                case GUARD_RAIL -> connect(type.furniture().guardRail().orElseThrow().block().getState(random, pos),
                        connections);
                case LAMP_POST -> type.furniture().lamp().orElseThrow().post().getState(random, pos);
                case LAMP_LIGHT -> type.furniture().lamp().orElseThrow().light().getState(random, pos);
                case SIGN -> rotate(type.furniture().sign().orElseThrow().block().getState(random, pos),
                        connections);
                case TUNNEL_FLOOR -> type.tunnel().floor().orElse(type.deck().edge()).getState(random, pos);
                case CEILING -> type.tunnel().ceiling().orElse(palettes.wall()).getState(random, pos);
                case LIGHT -> type.tunnel().light().orElseThrow().getState(random, pos);
            };
            level.setBlock(pos, state, 2);
            if (part == RoadRasterizer.Part.SIGN && state.getBlock() instanceof SignBlock) {
                write(type.furniture().sign().orElseThrow().lines());
            }
        }

        private static BlockState rotate(BlockState state, int rotation) {
            return state.hasProperty(BlockStateProperties.ROTATION_16)
                    ? state.setValue(BlockStateProperties.ROTATION_16, rotation) : state;
        }

        /**
         * Worldgen chunks hold block entities as NBT until promoted. {@code SignBlockEntity.setText} needs a level
         * => tag built by hand.
         */
        private void write(List<String> lines) {
            SignText text = new SignText();
            for (int i = 0; i < lines.size(); i++) {
                text = text.setMessage(i, Component.literal(lines.get(i)));
            }
            Tag encoded = SignText.DIRECT_CODEC.encodeStart(
                    level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text).getOrThrow();
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(BlockEntityType.SIGN).toString());
            tag.putInt("x", pos.getX());
            tag.putInt("y", pos.getY());
            tag.putInt("z", pos.getZ());
            tag.put("front_text", encoded);
            tag.put("back_text", encoded.copy());
            tag.putBoolean("is_waxed", true);
            level.getChunk(pos).setBlockEntityNbt(tag);
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
