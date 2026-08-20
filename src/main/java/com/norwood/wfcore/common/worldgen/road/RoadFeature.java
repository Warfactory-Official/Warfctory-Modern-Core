package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Rasterises the road network into blocks, one chunk at a time.
 *
 * <p>Roads are drawn as a distance field rather than as stamped pieces. Every column in the chunk asks how far it
 * is from the nearest centreline; that signed offset decides whether it is lane, shoulder, railing, tunnel wall
 * or embankment. Nothing is placed by walking the curve and painting a template, which is what keeps a diagonal
 * road free of the gaps and double-writes a template walk produces, and what lets the same code draw a 5-wide
 * dirt track and a 15-wide highway from two numbers in a JSON file.
 *
 * <p>Columns collect a hit per road, not one hit overall, and stamp them lowest deck first. That is what makes an
 * overpass work: the road underneath is written, then the bridge above it, and neither erases the other.
 *
 * <p>Runs once per chunk. {@code applyBiomeDecoration} de-duplicates placed features across every biome in the
 * chunk, so a placement present in all biomes is invoked exactly once with the chunk origin.
 */
public class RoadFeature extends Feature<NoneFeatureConfiguration> {

    /** Roads that can reach one column. More than a couple means a junction, and only the nearest few matter. */
    private static final int MAX_HITS = 4;
    /** How many stations of a bridge in a row carry a pier. */
    private static final int PIER_THICKNESS = 2;
    /** Ceiling on any single vertical edit, so a pathological profile cannot stall generation. */
    private static final int MAX_VERTICAL = 96;
    /** Chunk generation is multi-threaded, so the per-chunk run buffer is per-thread rather than shared. */
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
        IntArrayList runs = SCRATCH.get();
        index.collectRuns(minX >> 4, minZ >> 4, runs);
        runs.removeIf(run -> !index.runReaches(run, minX, minZ, minX + 15, minZ + 15));
        if (runs.isEmpty()) {
            return false;
        }

        // One biome lookup for the whole chunk, taken at the surface in the middle of it. Themes then change on
        // chunk borders rather than block by block, which is what stops a bridge speckling across a biome edge.
        int centreX = minX + 8;
        int centreZ = minZ + 8;
        BlockPos centre = new BlockPos(centreX, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, centreX, centreZ),
                centreZ);
        Column column = new Column(index, level, context.random(), level.getBiome(centre));
        boolean placed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                placed |= column.build(runs, minX + dx, minZ + dz);
            }
        }
        return placed;
    }

    /**
     * How close two decks have to be before the columns are treated as one road surface rather than two. Roads
     * that overlap laterally — two sea passages funnelled through the same isthmus — would otherwise each draw
     * their own centre line and their own railing straight down the middle of the other's carriageway.
     */
    private static final int OVERLAP_TOLERANCE = 4;
    /** Height above the headroom that a canopy still counts as overhanging the road. */
    private static final int CANOPY_REACH = 6;

    /**
     * Everything needed to write a single column, held together so the per-chunk lookups — the biome theme, the
     * mutable position — are done once rather than per block.
     */
    private static final class Column {

        private final RoadIndex index;
        private final WorldGenLevel level;
        private final RandomSource random;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final Map<RoadType, RoadType.Palettes> themed = new IdentityHashMap<>(2);
        private final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome;
        // Parallel arrays rather than a record per hit: this is allocated for every road reaching every one of
        // the 256 columns in every chunk a road passes through, which is the single largest source of garbage
        // the rasteriser can produce.
        private final RoadIndex.LinkGeometry[] hitLink = new RoadIndex.LinkGeometry[MAX_HITS];
        private final int[] hitStation = new int[MAX_HITS];
        private final double[] hitOffset = new double[MAX_HITS];

        Column(RoadIndex index, WorldGenLevel level, RandomSource random,
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
            this.index = index;
            this.level = level;
            this.random = random;
            this.biome = biome;
        }

        boolean build(IntArrayList runs, int x, int z) {
            int found = collect(runs, x, z);
            if (found == 0) {
                return false;
            }
            // WORLD_SURFACE_WG rather than the solved centreline height: the feature runs last, so this is the
            // real top of the column including any tree that grew over the corridor, and it is what has to come
            // off. Everything that needs the *soil* level finds it by scanning down for solid blocks instead.
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            for (int i = 0; i < found; i++) {
                RoadIndex.LinkGeometry link = hitLink[i];
                int station = hitStation[i];
                double offset = hitOffset[i];
                // Anything past the earthworks is only here to have its trees taken out of the road's headroom.
                if (offset > link.type().gradedHalfWidth()) {
                    sweepVegetation(link, station, x, z);
                    continue;
                }
                switch (link.mode(station)) {
                    case RoadProfile.BRIDGE -> bridge(link, station, offset, x, z, top);
                    case RoadProfile.TUNNEL -> tunnel(link, station, offset, x, z);
                    default -> onGround(link, station, offset, x, z, top);
                }
                sweepVegetation(link, station, x, z);
            }
            return true;
        }

        /**
         * The nearest station of every road that reaches this column, closest deck first. Keeping one hit per
         * road rather than one overall is what stops a bridge deck from deleting the road it flies over.
         */
        private int collect(IntArrayList runs, int x, int z) {
            double cx = x + 0.5;
            double cz = z + 0.5;
            int found = 0;
            for (int i = 0, n = runs.size(); i < n; i++) {
                int run = runs.getInt(i);
                double reach = index.runReach(run);
                if (!index.runReaches(run, x, z, x, z)) {
                    continue;
                }
                RoadIndex.LinkGeometry link = index.link(index.runLink(run));
                int last = index.runLast(run);
                int best = -1;
                double bestDistance = reach * reach;
                for (int station = index.runFirst(run); station <= last; station++) {
                    double dx = link.x(station) - cx;
                    double dz = link.z(station) - cz;
                    double distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = station;
                    }
                }
                if (best < 0) {
                    continue;
                }
                double offset = perpendicular(link, best, cx, cz);
                if (offset > reach) {
                    continue;
                }
                found = merge(found, link, best, offset);
            }
            return found;
        }

        /**
         * Distance to the centreline itself rather than to the nearest station, by projecting onto the two
         * segments either side of it. Stations are a block apart, so this only matters at the road edge — but
         * that is exactly where a half-block error shows up as a ragged kerb.
         */
        private double perpendicular(RoadIndex.LinkGeometry link, int station, double cx, double cz) {
            double ax = link.x(station);
            double az = link.z(station);
            // Squared throughout, one square root at the end. Math.hypot is a precise, branchy routine that
            // guards against overflow neither coordinate here can reach, and it sits in a loop that runs a few
            // hundred times per chunk.
            double best = (cx - ax) * (cx - ax) + (cz - az) * (cz - az);
            for (int other = station - 1; other <= station + 1; other += 2) {
                if (other < 0 || other >= link.stationCount()) {
                    continue;
                }
                double segX = link.x(other) - ax;
                double segZ = link.z(other) - az;
                double lengthSquared = segX * segX + segZ * segZ;
                if (lengthSquared <= 0.0) {
                    continue;
                }
                double t = Math.clamp(((cx - ax) * segX + (cz - az) * segZ) / lengthSquared, 0.0, 1.0);
                double offX = cx - (ax + segX * t);
                double offZ = cz - (az + segZ * t);
                best = Math.min(best, offX * offX + offZ * offZ);
            }
            return Math.sqrt(best);
        }

        /**
         * Inserts a hit in deck order, keeping one per road, and collapsing roads that share a deck height into
         * whichever one this column is nearest the centre of. Two roads at genuinely different heights both
         * survive — that is an overpass, and both halves of it have to be built.
         */
        private int merge(int found, RoadIndex.LinkGeometry link, int station, double offset) {
            int deck = link.deck(station);
            for (int i = 0; i < found; i++) {
                boolean sameRoad = hitLink[i] == link;
                boolean sameSurface = Math.abs(hitLink[i].deck(hitStation[i]) - deck) <= OVERLAP_TOLERANCE;
                if (sameRoad || sameSurface) {
                    if (offset < hitOffset[i]) {
                        hitLink[i] = link;
                        hitStation[i] = station;
                        hitOffset[i] = offset;
                    }
                    return found;
                }
            }
            if (found == MAX_HITS) {
                return found;
            }
            int at = found;
            while (at > 0 && hitLink[at - 1].deck(hitStation[at - 1]) > deck) {
                hitLink[at] = hitLink[at - 1];
                hitStation[at] = hitStation[at - 1];
                hitOffset[at] = hitOffset[at - 1];
                at--;
            }
            hitLink[at] = link;
            hitStation[at] = station;
            hitOffset[at] = offset;
            return found + 1;
        }

        // -----------------------------------------------------------------------------------------------------

        private void onGround(RoadIndex.LinkGeometry link, int station, double offset, int x, int z, int top) {
            RoadType type = link.type();
            int deck = link.deck(station);

            if (offset <= type.outerHalfWidth()) {
                // Up to the top of the column, not just to the headroom: a road stays on the ground through
                // anything shallower than the tunnel threshold, so there can be a dozen blocks of hillside — and
                // whatever grew on it — to take off.
                clear(x, z, deck + 1, Math.max(deck + type.deck().clearance(), top));
                set(x, deck, z, surfaceState(link, station, type, offset));
                foundation(x, z, deck, type, type.earthworks().base());
                return;
            }
            // Beyond the shoulder the road blends back into the hillside: a cutting whose walls slope up away
            // from it, or an embankment whose shoulder slopes down. Without this a road through any relief is a
            // trench with vertical walls.
            double flare = (offset - type.outerHalfWidth()) * type.earthworks().batter();
            int cutCeiling = deck + type.deck().clearance() + (int) Math.round(flare);
            clear(x, z, cutCeiling + 1, top);
            fillDown(x, z, deck - (int) Math.round(flare), type.earthworks().embankment());
        }

        private void bridge(RoadIndex.LinkGeometry link, int station, double offset, int x, int z, int top) {
            RoadType type = link.type();
            RoadType.Bridge spec = type.bridge();
            RoadType.Palettes palettes = palettes(type);
            int deck = link.deck(station);
            if (offset > type.outerHalfWidth()) {
                return;
            }

            boolean kerb = offset > type.outerHalfWidth() - 1.0;
            BlockState surface = kerb ? palettes.accent().getState(random, pos.set(x, deck, z))
                    : deckState(link, station, type, spec, offset);
            // Cleared before the railing goes up, so the headroom sweep cannot take it back out again.
            clear(x, z, deck + 1, Math.max(deck + type.deck().clearance(), top));
            set(x, deck, z, surface);
            set(x, deck - 1, z, palettes.structure().getState(random, pos.set(x, deck - 1, z)));

            if (kerb && spec.railing().isPresent()) {
                double outer = type.outerHalfWidth();
                boolean north = isKerb(link, station, x + 0.5, z - 0.5, outer);
                boolean south = isKerb(link, station, x + 0.5, z + 1.5, outer);
                boolean west = isKerb(link, station, x - 0.5, z + 0.5, outer);
                boolean east = isKerb(link, station, x + 1.5, z + 0.5, outer);
                for (int h = 1; h <= spec.railingHeight(); h++) {
                    BlockState rail = spec.railing().get().getState(random, pos.set(x, deck + h, z));
                    set(x, deck + h, z, connect(rail, north, east, south, west));
                }
            }
            if (offset <= spec.pierRadius()
                    && Math.floorMod(station, spec.pierSpacing()) < PIER_THICKNESS) {
                // Down until it lands on something. A pier over an ocean has to find the sea bed, and the sea
                // bed is not where the centreline profile says the ground is when the road is 8 blocks wide.
                fillDown(x, z, deck - 2, palettes.structure());
            }
        }

        private void tunnel(RoadIndex.LinkGeometry link, int station, double offset, int x, int z) {
            RoadType type = link.type();
            RoadType.Tunnel spec = type.tunnel();
            RoadType.Palettes palettes = palettes(type);
            int deck = link.deck(station);
            int ceiling = deck + spec.height() + 1;

            boolean bore = offset <= type.pavedHalfWidth();
            boolean lining = offset <= type.outerHalfWidth();
            if (!lining && !(link.portal(station) && offset <= type.outerHalfWidth() + 2.0)) {
                return;
            }

            if (!lining) {
                // Portal face: a solid frame around the mouth so the bore does not open out of bare hillside.
                for (int y = deck; y <= ceiling; y++) {
                    set(x, y, z, palettes.wall().getState(random, pos.set(x, y, z)));
                }
                return;
            }

            set(x, deck, z, bore ? surfaceState(link, station, type, offset)
                    : spec.floor().orElse(type.deck().edge()).getState(random, pos.set(x, deck, z)));
            foundation(x, z, deck, type, type.earthworks().base());

            if (bore) {
                clear(x, z, deck + 1, deck + spec.height());
            } else {
                for (int y = deck + 1; y <= deck + spec.height(); y++) {
                    set(x, y, z, palettes.wall().getState(random, pos.set(x, y, z)));
                }
            }
            boolean lit = spec.light().isPresent() && offset <= 1.0
                    && Math.floorMod(station, spec.lightSpacing()) == 0;
            BlockStateProvider roof = lit ? spec.light().get() : spec.ceiling().orElse(palettes.wall());
            set(x, ceiling, z, roof.getState(random, pos.set(x, ceiling, z)));
        }

        // -----------------------------------------------------------------------------------------------------

        private BlockState surfaceState(RoadIndex.LinkGeometry link, int station, RoadType type, double offset) {
            BlockState painted = paint(link, station, type, offset);
            if (painted != null) {
                return painted;
            }
            BlockStateProvider provider = offset <= type.pavedHalfWidth() ? type.deck().surface()
                    : type.deck().edge();
            return provider.getState(random, at(link, station));
        }

        private BlockState deckState(RoadIndex.LinkGeometry link, int station, RoadType type,
                RoadType.Bridge spec, double offset) {
            BlockState painted = paint(link, station, type, offset);
            return painted != null ? painted
                    : spec.deck().orElse(type.deck().surface()).getState(random, at(link, station));
        }

        /** The centre line, or null if this column is not on it or is on a gap rather than a dash. */
        private BlockState paint(RoadIndex.LinkGeometry link, int station, RoadType type, double offset) {
            if (type.deck().markings().isEmpty()) {
                return null;
            }
            RoadType.Markings markings = type.deck().markings().get();
            // Station index is arc length in blocks, which is what makes a dash pattern hold its rhythm through
            // a curve instead of stretching on the outside of it.
            // Quarter-block of slack on the width: on a 45-degree run the columns nearest the centreline sit at
            // offsets of 0.35, 0.71, 1.06..., and an exact half-width test would skip every other one and turn a
            // solid line into an unintended dash.
            if (offset > markings.width() / 2.0 + 0.25 || !markings.paintedAt(station)) {
                return null;
            }
            return markings.block().getState(random, at(link, station));
        }

        private BlockPos at(RoadIndex.LinkGeometry link, int station) {
            return pos.set(link.x(station), 0, link.z(station));
        }

        /**
         * True when the given point also lands on this road's kerb ring — i.e. a railing will stand there too.
         * Searched over a short window of stations because the neighbouring column's nearest station is not
         * necessarily this one, and the answer has to agree with what that column will decide for itself.
         */
        private boolean isKerb(RoadIndex.LinkGeometry link, int station, double cx, double cz, double outer) {
            double best = Double.MAX_VALUE;
            for (int s = Math.max(0, station - 3); s <= Math.min(link.stationCount() - 1, station + 3); s++) {
                best = Math.min(best, perpendicular(link, s, cx, cz));
            }
            return best > outer - 1.0 && best <= outer;
        }

        /**
         * Bakes the connections into the block instead of relying on a neighbour update to work them out.
         * Worldgen writes blocks without updates, so a wall or fence placed here keeps its default unconnected
         * state forever and reads as a row of loose posts. Forcing real updates during generation would be far
         * more expensive than deriving the answer from geometry we already have.
         *
         * <p>Handles both shapes a railing can take: walls, whose sides are a {@link WallSide} enum, and fences
         * and panes, whose sides are plain booleans. Anything else is returned untouched.
         */
        private static BlockState connect(BlockState state, boolean north, boolean east, boolean south,
                boolean west) {
            if (state.hasProperty(BlockStateProperties.NORTH_WALL)) {
                // A wall post only shows on a corner, a junction or a dead end — a straight run reads as a rail.
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

        /**
         * Takes logs and leaves out of the road's vertical envelope. Trees are placed at an earlier decoration
         * step than this feature, so they are already standing; the ones whose trunks clear the earthworks still
         * hang canopy over the carriageway, and that canopy will never decay on its own because the distance
         * property it was placed with was never recomputed.
         */
        private void sweepVegetation(RoadIndex.LinkGeometry link, int station, int x, int z) {
            RoadType type = link.type();
            int deck = link.deck(station);
            int from = link.mode(station) == RoadProfile.TUNNEL ? deck + type.tunnel().height() + 2 : deck + 1;
            int to = Math.min(from + type.deck().clearance() + CANOPY_REACH, level.getMaxBuildHeight() - 1);
            for (int y = from; y <= to; y++) {
                BlockState existing = level.getBlockState(pos.set(x, y, z));
                if (existing.isAir()) {
                    continue;
                }
                if (existing.is(BlockTags.LOGS) || existing.is(BlockTags.LEAVES)
                        || existing.is(BlockTags.SAPLINGS) || existing.is(BlockTags.REPLACEABLE)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        /**
         * Roadbed under the surface. Fills the declared depth and then keeps going while the column is still
         * hollow, so a road crossing a cave or standing on a low embankment is carried rather than floating.
         */
        private void foundation(int x, int z, int deck, RoadType type, BlockStateProvider base) {
            int depth = type.earthworks().baseDepth();
            for (int k = 1; k <= depth + MAX_VERTICAL; k++) {
                int y = deck - k;
                if (y < level.getMinBuildHeight() + 1) {
                    return;
                }
                BlockState existing = level.getBlockState(pos.set(x, y, z));
                boolean hollow = existing.isAir() || !existing.getFluidState().isEmpty()
                        || existing.canBeReplaced();
                if (!hollow && k > depth) {
                    return;
                }
                if (hollow || k <= depth) {
                    set(x, y, z, base.getState(random, pos.set(x, y, z)));
                }
            }
        }

        /**
         * Builds downward from {@code from} for as long as the column is hollow, which is how an embankment
         * shoulder and a bridge pier both find their footing without anyone having to know where the ground is.
         */
        private void fillDown(int x, int z, int from, BlockStateProvider provider) {
            for (int y = from; from - y <= MAX_VERTICAL; y--) {
                if (y < level.getMinBuildHeight() + 1) {
                    return;
                }
                BlockState existing = level.getBlockState(pos.set(x, y, z));
                if (!existing.isAir() && existing.getFluidState().isEmpty() && !existing.canBeReplaced()) {
                    return;
                }
                set(x, y, z, provider.getState(random, pos.set(x, y, z)));
            }
        }

        /** Air out the headroom over the road. Bedrock is left alone; nothing should be able to punch through it. */
        private void clear(int x, int z, int from, int to) {
            int top = Math.min(to, Math.min(from + MAX_VERTICAL, level.getMaxBuildHeight() - 1));
            for (int y = Math.max(from, level.getMinBuildHeight()); y <= top; y++) {
                BlockState existing = level.getBlockState(pos.set(x, y, z));
                if (!existing.isAir() && !existing.is(Blocks.BEDROCK)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        private void set(int x, int y, int z, BlockState state) {
            if (y < level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight()) {
                return;
            }
            pos.set(x, y, z);
            if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
                level.setBlock(pos, state, 2);
            }
        }

        private RoadType.Palettes palettes(RoadType type) {
            return themed.computeIfAbsent(type, key -> key.palettesFor(biome));
        }
    }
}
