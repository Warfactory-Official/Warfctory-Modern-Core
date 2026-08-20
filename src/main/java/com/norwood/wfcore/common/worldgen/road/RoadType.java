package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Everything about how one class of road looks: how wide it is, what it is made of, how steep it may get, and what
 * it turns into over a valley or inside a mountain. Datapack-defined at
 * {@code data/<ns>/wf_road_type/<id>.json}, so the whole block composition is retunable with a world reload.
 *
 * <p>A road type is not chosen by name. Every type carries a {@link Match} and each link in the
 * {@link RoadNetwork} takes the highest-priority type whose match it satisfies — so "paved links over 6 km become
 * highways" is a rule in a JSON file, not a branch in the generator.
 *
 * <p>Block slots all take {@link RoadPalettes#CODEC}: a bare id for the common case, a full
 * {@link BlockStateProvider} when a pack wants variation.
 */
public record RoadType(
        Match match,
        Deck deck,
        Earthworks earthworks,
        Grade grade,
        Water water,
        Bridge bridge,
        Tunnel tunnel,
        List<Theme> themes) {

    public static final Codec<RoadType> CODEC = RecordCodecBuilder.create(i -> i.group(
            Match.CODEC.fieldOf("match").forGetter(RoadType::match),
            Deck.CODEC.fieldOf("deck").forGetter(RoadType::deck),
            Earthworks.CODEC.optionalFieldOf("earthworks", Earthworks.DEFAULT).forGetter(RoadType::earthworks),
            Grade.CODEC.optionalFieldOf("grade", Grade.DEFAULT).forGetter(RoadType::grade),
            Water.CODEC.optionalFieldOf("water", Water.DEFAULT).forGetter(RoadType::water),
            Bridge.CODEC.fieldOf("bridge").forGetter(RoadType::bridge),
            Tunnel.CODEC.fieldOf("tunnel").forGetter(RoadType::tunnel),
            Theme.CODEC.listOf().optionalFieldOf("themes", List.of()).forGetter(RoadType::themes))
            .apply(i, RoadType::new));

    /** Half the corridor that gets road blocks in it, in whole columns either side of the centreline. */
    public double pavedHalfWidth() {
        return deck.width() / 2.0;
    }

    /** Half the corridor including the shoulder / edging ring. */
    public double outerHalfWidth() {
        return deck.width() / 2.0 + deck.shoulder();
    }

    /** Half the corridor including the graded embankment — the widest the generator will cut or fill. */
    public double gradedHalfWidth() {
        return outerHalfWidth() + earthworks.margin();
    }

    /** Half the corridor including the vegetation sweep, i.e. everything this road may touch at all. */
    public double clearedHalfWidth() {
        return outerHalfWidth() + Math.max(earthworks.margin(), deck.vegetationMargin());
    }

    /**
     * Which links this type claims. All present conditions must hold; {@code priority} breaks ties when several
     * types match the same link, highest wins.
     */
    public record Match(List<String> kinds, int minLength, int maxLength, Optional<Boolean> seaPassage,
            int priority) {

        public static final Codec<Match> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("kinds", List.of()).forGetter(Match::kinds),
                Codec.INT.optionalFieldOf("min_length", 0).forGetter(Match::minLength),
                Codec.INT.optionalFieldOf("max_length", Integer.MAX_VALUE).forGetter(Match::maxLength),
                Codec.BOOL.optionalFieldOf("sea_passage").forGetter(Match::seaPassage),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Match::priority))
                .apply(i, Match::new));

        public boolean matches(RoadNetwork.Link link, int length) {
            if (!kinds.isEmpty() && !kinds.contains(link.kind())) {
                return false;
            }
            if (length < minLength || length > maxLength) {
                return false;
            }
            return seaPassage.map(want -> want == link.seaPassage()).orElse(true);
        }
    }

    /**
     * The driving surface itself. {@code width} is the paved lane width; {@code shoulder} is the edging ring.
     *
     * <p>{@code vegetation_margin} widens the corridor for one purpose only: pulling logs and leaves out of the
     * road's headroom. A tree whose trunk stands clear of the earthworks can still hang its canopy over the
     * carriageway, and those leaves never decay on their own — worldgen places blocks without neighbour updates,
     * so their distance property is stale from birth and the decay check never fires.
     */
    public record Deck(int width, int shoulder, int clearance, int vegetationMargin, BlockStateProvider surface,
            BlockStateProvider edge, Optional<Markings> markings) {

        public static final Codec<Deck> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 64).fieldOf("width").forGetter(Deck::width),
                Codec.intRange(0, 16).optionalFieldOf("shoulder", 0).forGetter(Deck::shoulder),
                Codec.intRange(1, 32).optionalFieldOf("clearance", 5).forGetter(Deck::clearance),
                Codec.intRange(0, 32).optionalFieldOf("vegetation_margin", 6).forGetter(Deck::vegetationMargin),
                RoadPalettes.CODEC.fieldOf("surface").forGetter(Deck::surface),
                RoadPalettes.CODEC.fieldOf("edge").forGetter(Deck::edge),
                Markings.CODEC.optionalFieldOf("markings").forGetter(Deck::markings))
                .apply(i, Deck::new));
    }

    /** A dashed centre line. {@code dash} and {@code gap} are measured in blocks along the road. */
    public record Markings(BlockStateProvider block, int width, int dash, int gap) {

        public static final Codec<Markings> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("block").forGetter(Markings::block),
                Codec.intRange(1, 8).optionalFieldOf("width", 1).forGetter(Markings::width),
                Codec.intRange(1, 64).optionalFieldOf("dash", 4).forGetter(Markings::dash),
                Codec.intRange(0, 64).optionalFieldOf("gap", 4).forGetter(Markings::gap))
                .apply(i, Markings::new));

        /** True on the stretch of centreline that should be painted, for a station at arc length {@code s}. */
        public boolean paintedAt(double s) {
            int period = dash + gap;
            return period <= 0 || Math.floorMod((int) Math.floor(s), period) < dash;
        }
    }

    /**
     * What holds the road up and what happens either side of it. {@code margin} is how far past the shoulder the
     * generator may cut or fill, and {@code batter} is the slope it does that on — 1.0 is 45 degrees, lower is a
     * gentler, wider flare.
     */
    public record Earthworks(BlockStateProvider base, int baseDepth, BlockStateProvider embankment, int margin,
            double batter) {

        public static final Codec<Earthworks> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("base").forGetter(Earthworks::base),
                Codec.intRange(0, 32).optionalFieldOf("base_depth", 3).forGetter(Earthworks::baseDepth),
                RoadPalettes.CODEC.fieldOf("embankment").forGetter(Earthworks::embankment),
                Codec.intRange(0, 32).optionalFieldOf("margin", 5).forGetter(Earthworks::margin),
                Codec.doubleRange(0.1, 8.0).optionalFieldOf("batter", 1.0).forGetter(Earthworks::batter))
                .apply(i, Earthworks::new));

        static final Earthworks DEFAULT = new Earthworks(BlockStateProvider.simple(
                net.minecraft.world.level.block.Blocks.STONE), 3,
                BlockStateProvider.simple(net.minecraft.world.level.block.Blocks.STONE), 5, 1.0);
    }

    /**
     * The vehicle-drivability budget. {@code max} is rise over run, so 0.06 is a 6% grade;
     * {@code smoothing_radius} is how far the profile is averaged before the grade limit is re-applied, which is
     * what turns a terrain-following line into vertical curves a vehicle can take at speed.
     * {@code sample_spacing} is how often real terrain is probed — the only expensive knob here.
     *
     * <p>{@code attraction} is how hard each pass pulls the profile back onto the ground it just smoothed away.
     * Without it, repeated blurring has no fixed point other than the average height of the whole road, and a
     * road over rolling terrain drifts up onto a permanent viaduct. At 0.5 the profile tracks the terrain's low
     * frequencies and only leaves it where the gradient limit genuinely forces it to — which is exactly where a
     * bridge or a tunnel belongs.
     */
    public record Grade(double max, int smoothingRadius, int relaxPasses, int sampleSpacing, double attraction) {

        public static final Codec<Grade> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.005, 1.0).optionalFieldOf("max", 0.06).forGetter(Grade::max),
                Codec.intRange(1, 256).optionalFieldOf("smoothing_radius", 10).forGetter(Grade::smoothingRadius),
                Codec.intRange(1, 64).optionalFieldOf("relax_passes", 8).forGetter(Grade::relaxPasses),
                Codec.intRange(1, 32).optionalFieldOf("sample_spacing", 16).forGetter(Grade::sampleSpacing),
                Codec.doubleRange(0.0, 1.0).optionalFieldOf("attraction", 0.85).forGetter(Grade::attraction))
                .apply(i, Grade::new));

        static final Grade DEFAULT = new Grade(0.06, 8, 8, 16, 0.85);
    }

    /**
     * Water crossings. Every deck clears the water surface by {@code clearance}; only crossings longer than
     * {@code sea_crossing_length} are additionally pinned up to {@code min_deck_y}, so an ocean strait gets a
     * high span with room to sail under it while a river gets an ordinary low bridge instead of a 30-block hump.
     */
    public record Water(int minDeckY, int clearance, int seaCrossingLength) {

        public static final Codec<Water> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(-64, 320).optionalFieldOf("min_deck_y", 90).forGetter(Water::minDeckY),
                Codec.intRange(0, 128).optionalFieldOf("clearance", 4).forGetter(Water::clearance),
                Codec.intRange(1, 4096).optionalFieldOf("sea_crossing_length", 48)
                        .forGetter(Water::seaCrossingLength))
                .apply(i, Water::new));

        static final Water DEFAULT = new Water(90, 4, 48);
    }

    /**
     * What a span over a valley or a strait is built from. {@code structure} is the load path — underside and
     * piers — and {@code accent} trims the deck edge. {@code min_height} is how far the road has to stand off the
     * ground before it stops being an embankment and becomes a bridge.
     */
    public record Bridge(Optional<BlockStateProvider> deck, BlockStateProvider structure, BlockStateProvider accent,
            Optional<BlockStateProvider> railing, int railingHeight, int pierSpacing, int pierRadius,
            int minHeight) {

        public static final Codec<Bridge> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.optionalFieldOf("deck").forGetter(Bridge::deck),
                RoadPalettes.CODEC.fieldOf("structure").forGetter(Bridge::structure),
                RoadPalettes.CODEC.fieldOf("accent").forGetter(Bridge::accent),
                RoadPalettes.CODEC.optionalFieldOf("railing").forGetter(Bridge::railing),
                Codec.intRange(0, 8).optionalFieldOf("railing_height", 1).forGetter(Bridge::railingHeight),
                Codec.intRange(2, 128).optionalFieldOf("pier_spacing", 16).forGetter(Bridge::pierSpacing),
                Codec.intRange(0, 16).optionalFieldOf("pier_radius", 2).forGetter(Bridge::pierRadius),
                Codec.intRange(1, 64).optionalFieldOf("min_height", 4).forGetter(Bridge::minHeight))
                .apply(i, Bridge::new));
    }

    /**
     * What a bore through a mountain is lined with. {@code min_cover} is how much rock has to sit above the
     * headroom before the generator stops cutting a trench and starts tunnelling.
     */
    public record Tunnel(BlockStateProvider wall, Optional<BlockStateProvider> ceiling,
            Optional<BlockStateProvider> floor, Optional<BlockStateProvider> light, int lightSpacing, int height,
            int minCover) {

        public static final Codec<Tunnel> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("wall").forGetter(Tunnel::wall),
                RoadPalettes.CODEC.optionalFieldOf("ceiling").forGetter(Tunnel::ceiling),
                RoadPalettes.CODEC.optionalFieldOf("floor").forGetter(Tunnel::floor),
                RoadPalettes.CODEC.optionalFieldOf("light").forGetter(Tunnel::light),
                Codec.intRange(1, 64).optionalFieldOf("light_spacing", 8).forGetter(Tunnel::lightSpacing),
                Codec.intRange(3, 32).optionalFieldOf("height", 6).forGetter(Tunnel::height),
                Codec.intRange(1, 64).optionalFieldOf("min_cover", 8).forGetter(Tunnel::minCover))
                .apply(i, Tunnel::new));
    }

    /**
     * A biome-keyed swap of the structural palettes, which is how a bridge ends up sandstone in a desert and
     * stone brick everywhere else without a second road type. Only the three structural slots are themable —
     * the driving surface stays the same so a road still reads as the same road across a biome edge.
     */
    public record Theme(HolderSet<Biome> biomes, Optional<BlockStateProvider> structure,
            Optional<BlockStateProvider> accent, Optional<BlockStateProvider> wall) {

        public static final Codec<Theme> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(Theme::biomes),
                RoadPalettes.CODEC.optionalFieldOf("structure").forGetter(Theme::structure),
                RoadPalettes.CODEC.optionalFieldOf("accent").forGetter(Theme::accent),
                RoadPalettes.CODEC.optionalFieldOf("wall").forGetter(Theme::wall))
                .apply(i, Theme::new));
    }

    /** The structural palettes to use at a position, after any {@link Theme} that claims its biome. */
    public Palettes palettesFor(Holder<Biome> biome) {
        BlockStateProvider structure = bridge.structure();
        BlockStateProvider accent = bridge.accent();
        BlockStateProvider wall = tunnel.wall();
        for (Theme theme : themes) {
            if (theme.biomes().contains(biome)) {
                structure = theme.structure().orElse(structure);
                accent = theme.accent().orElse(accent);
                wall = theme.wall().orElse(wall);
                break;
            }
        }
        return new Palettes(structure, accent, wall);
    }

    public record Palettes(BlockStateProvider structure, BlockStateProvider accent, BlockStateProvider wall) {}
}
