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
import net.minecraft.world.level.block.Blocks;

/**
 * One road class, datapack-defined at {@code data/<ns>/wfcore/road_type/<id>.json}. Each link takes the
 * highest-priority type whose {@link Match} it satisfies. Block slots take {@link RoadPalettes#CODEC}.
 */
public record RoadType(
        Match match,
        Deck deck,
        Earthworks earthworks,
        Grade grade,
        Water water,
        Bridge bridge,
        Tunnel tunnel,
        Junction junction,
        Furniture furniture,
        List<Theme> themes) {

    public static final Codec<RoadType> CODEC = RecordCodecBuilder.create(i -> i.group(
            Match.CODEC.fieldOf("match").forGetter(RoadType::match),
            Deck.CODEC.fieldOf("deck").forGetter(RoadType::deck),
            Earthworks.CODEC.optionalFieldOf("earthworks", Earthworks.DEFAULT).forGetter(RoadType::earthworks),
            Grade.CODEC.optionalFieldOf("grade", Grade.DEFAULT).forGetter(RoadType::grade),
            Water.CODEC.optionalFieldOf("water", Water.DEFAULT).forGetter(RoadType::water),
            Bridge.CODEC.fieldOf("bridge").forGetter(RoadType::bridge),
            Tunnel.CODEC.fieldOf("tunnel").forGetter(RoadType::tunnel),
            Junction.CODEC.optionalFieldOf("junction", Junction.DEFAULT).forGetter(RoadType::junction),
            Furniture.CODEC.optionalFieldOf("furniture", Furniture.NONE).forGetter(RoadType::furniture),
            Theme.CODEC.listOf().optionalFieldOf("themes", List.of()).forGetter(RoadType::themes))
            .apply(i, RoadType::new));

    public RoadSpec spec() {
        Markings marks = deck.markings().orElse(null);
        return new RoadSpec(match.priority(), deck.width(), deck.shoulder(), deck.clearance(),
                deck.vegetationMargin(), marks == null ? 0 : marks.width(), marks == null ? 0 : marks.dash(),
                marks == null ? 0 : marks.gap(), earthworks.baseDepth(), earthworks.margin(), earthworks.batter(),
                earthworks.cutBatter().orElse(earthworks.batter()), grade, water, bridge.railing().isPresent(),
                bridge.railingHeight(), bridge.pierSpacing(), bridge.pierRadius(), bridge.minHeight(),
                bridge.archRise(), bridge.truss().isPresent() ? bridge.trussLength() : 0,
                tunnel.light().isPresent(), tunnel.lightSpacing(), tunnel.height(), tunnel.minCover(),
                tunnel.minLength(), junction.gradeSeparated(), junction.cornerRadius(),
                furniture.lamp().map(Lamp::spacing).orElse(0), furniture.lamp().map(Lamp::height).orElse(0),
                furniture.guardRail().map(GuardRail::minFill).orElse(0), furniture.sign().isPresent());
    }

    /** All present conditions must hold; highest {@code priority} wins. */
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
     * {@code width}: paved; {@code shoulder}: edging ring each side. {@code vegetation_margin}: extra corridor
     * swept of logs/leaves only.
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

    /** Dashed centre line; {@code dash} / {@code gap} in blocks along the road. */
    public record Markings(BlockStateProvider block, int width, int dash, int gap) {

        public static final Codec<Markings> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("block").forGetter(Markings::block),
                Codec.intRange(1, 8).optionalFieldOf("width", 1).forGetter(Markings::width),
                Codec.intRange(1, 64).optionalFieldOf("dash", 4).forGetter(Markings::dash),
                Codec.intRange(0, 64).optionalFieldOf("gap", 4).forGetter(Markings::gap))
                .apply(i, Markings::new));

    }

    /**
     * {@code margin}: side slope reach past the shoulder; past it the remaining height stands as a wall.
     * {@code batter} / {@code cut_batter}: blocks of height per block outwards, fill / cut (cut defaults to fill).
     * {@code embankment}: fill body; slope tops copy the column's natural surface.
     */
    public record Earthworks(BlockStateProvider base, int baseDepth, BlockStateProvider embankment, int margin,
            double batter, Optional<Double> cutBatter) {

        public static final Codec<Earthworks> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("base").forGetter(Earthworks::base),
                Codec.intRange(0, 32).optionalFieldOf("base_depth", 3).forGetter(Earthworks::baseDepth),
                RoadPalettes.CODEC.fieldOf("embankment").forGetter(Earthworks::embankment),
                Codec.intRange(0, 32).optionalFieldOf("margin", 5).forGetter(Earthworks::margin),
                Codec.doubleRange(0.1, 8.0).optionalFieldOf("batter", 1.0).forGetter(Earthworks::batter),
                Codec.doubleRange(0.1, 8.0).optionalFieldOf("cut_batter").forGetter(Earthworks::cutBatter))
                .apply(i, Earthworks::new));

        static final Earthworks DEFAULT = new Earthworks(BlockStateProvider.simple(
                Blocks.STONE), 3,
                BlockStateProvider.simple(Blocks.STONE), 5, 1.0, Optional.empty());
    }

    /**
     * {@code max}: rise / run. {@code smoothing_radius}: box blur of the fitted profile => vertical curves.
     * {@code sample_spacing}: terrain probe stride, the only expensive knob. {@code fill_cost}: cost per block of
     * deck above ground relative to 1 per block below it; higher => more cuttings/tunnels, fewer
     * embankments/bridges.
     */
    public record Grade(double max, int smoothingRadius, int sampleSpacing, double fillCost) {

        public static final Codec<Grade> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0.005, 1.0).optionalFieldOf("max", 0.06).forGetter(Grade::max),
                Codec.intRange(0, 256).optionalFieldOf("smoothing_radius", 8).forGetter(Grade::smoothingRadius),
                Codec.intRange(1, 32).optionalFieldOf("sample_spacing", 16).forGetter(Grade::sampleSpacing),
                Codec.doubleRange(0.01, 100.0).optionalFieldOf("fill_cost", 2.0).forGetter(Grade::fillCost))
                .apply(i, Grade::new));

        static final Grade DEFAULT = new Grade(0.06, 8, 16, 2.0);
    }

    /**
     * Deck >= water + {@code clearance}; wet runs >= {@code sea_crossing_length} also >= {@code min_deck_y}; wet
     * runs < {@code ford_length} are filled: deck >= water + 1, no bridge.
     */
    public record Water(int minDeckY, int clearance, int seaCrossingLength, int fordLength) {

        public static final Codec<Water> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(-64, 320).optionalFieldOf("min_deck_y", 90).forGetter(Water::minDeckY),
                Codec.intRange(0, 128).optionalFieldOf("clearance", 4).forGetter(Water::clearance),
                Codec.intRange(1, 4096).optionalFieldOf("sea_crossing_length", 48)
                        .forGetter(Water::seaCrossingLength),
                Codec.intRange(0, 4096).optionalFieldOf("ford_length", 0).forGetter(Water::fordLength))
                .apply(i, Water::new));

        static final Water DEFAULT = new Water(90, 4, 48, 0);
    }

    /**
     * {@code structure}: underside + piers + abutments; {@code min_height}: deck - ground at which embankment =>
     * bridge. {@code arch_rise}: soffit depth at the piers, tapering to the crown (0 = flat). {@code truss}: side
     * trusses over wet spans of {@code truss_length}+ stations, piers twice as far apart.
     */
    public record Bridge(Optional<BlockStateProvider> deck, BlockStateProvider structure, BlockStateProvider accent,
            Optional<BlockStateProvider> railing, int railingHeight, int pierSpacing, int pierRadius,
            int minHeight, int archRise, Optional<BlockStateProvider> truss, int trussLength) {

        public static final Codec<Bridge> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.optionalFieldOf("deck").forGetter(Bridge::deck),
                RoadPalettes.CODEC.fieldOf("structure").forGetter(Bridge::structure),
                RoadPalettes.CODEC.fieldOf("accent").forGetter(Bridge::accent),
                RoadPalettes.CODEC.optionalFieldOf("railing").forGetter(Bridge::railing),
                Codec.intRange(0, 8).optionalFieldOf("railing_height", 1).forGetter(Bridge::railingHeight),
                Codec.intRange(2, 128).optionalFieldOf("pier_spacing", 16).forGetter(Bridge::pierSpacing),
                Codec.intRange(0, 16).optionalFieldOf("pier_radius", 2).forGetter(Bridge::pierRadius),
                Codec.intRange(1, 64).optionalFieldOf("min_height", 4).forGetter(Bridge::minHeight),
                Codec.intRange(0, 32).optionalFieldOf("arch_rise", 0).forGetter(Bridge::archRise),
                RoadPalettes.CODEC.optionalFieldOf("truss").forGetter(Bridge::truss),
                Codec.intRange(1, 4096).optionalFieldOf("truss_length", 96).forGetter(Bridge::trussLength))
                .apply(i, Bridge::new));
    }

    /**
     * {@code min_cover}: rock above the headroom before a cutting becomes a tunnel. {@code min_length}: shorter
     * covered runs stay open cuttings.
     */
    public record Tunnel(BlockStateProvider wall, Optional<BlockStateProvider> ceiling,
            Optional<BlockStateProvider> floor, Optional<BlockStateProvider> light, int lightSpacing, int height,
            int minCover, int minLength) {

        public static final Codec<Tunnel> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("wall").forGetter(Tunnel::wall),
                RoadPalettes.CODEC.optionalFieldOf("ceiling").forGetter(Tunnel::ceiling),
                RoadPalettes.CODEC.optionalFieldOf("floor").forGetter(Tunnel::floor),
                RoadPalettes.CODEC.optionalFieldOf("light").forGetter(Tunnel::light),
                Codec.intRange(1, 64).optionalFieldOf("light_spacing", 8).forGetter(Tunnel::lightSpacing),
                Codec.intRange(3, 32).optionalFieldOf("height", 6).forGetter(Tunnel::height),
                Codec.intRange(1, 64).optionalFieldOf("min_cover", 8).forGetter(Tunnel::minCover),
                Codec.intRange(1, 4096).optionalFieldOf("min_length", 40).forGetter(Tunnel::minLength))
                .apply(i, Tunnel::new));
    }

    /**
     * {@code grade_separated}: roads crossing this one mid-link pass over or under instead of meeting it at grade.
     * {@code corner_radius}: fillet between two arms of a junction pad; a corner takes the lesser of its arms'.
     */
    public record Junction(boolean gradeSeparated, int cornerRadius) {

        public static final Codec<Junction> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("grade_separated", false).forGetter(Junction::gradeSeparated),
                Codec.intRange(0, 16).optionalFieldOf("corner_radius", 5).forGetter(Junction::cornerRadius))
                .apply(i, Junction::new));

        static final Junction DEFAULT = new Junction(false, 5);
    }

    public record Furniture(Optional<Lamp> lamp, Optional<GuardRail> guardRail, Optional<Sign> sign) {

        public static final Codec<Furniture> CODEC = RecordCodecBuilder.create(i -> i.group(
                Lamp.CODEC.optionalFieldOf("lamp").forGetter(Furniture::lamp),
                GuardRail.CODEC.optionalFieldOf("guard_rail").forGetter(Furniture::guardRail),
                Sign.CODEC.optionalFieldOf("sign").forGetter(Furniture::sign))
                .apply(i, Furniture::new));

        static final Furniture NONE = new Furniture(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * On the kerb: {@code post} blocks with {@code light} on top at {@code height}. Alternating sides every
     * {@code spacing} stations (0 = stop lines only).
     */
    public record Lamp(BlockStateProvider post, BlockStateProvider light, int height, int spacing) {

        public static final Codec<Lamp> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("post").forGetter(Lamp::post),
                RoadPalettes.CODEC.fieldOf("light").forGetter(Lamp::light),
                Codec.intRange(2, 12).optionalFieldOf("height", 5).forGetter(Lamp::height),
                Codec.intRange(0, 1024).optionalFieldOf("spacing", 0).forGetter(Lamp::spacing))
                .apply(i, Lamp::new));
    }

    /** On the kerb where the deck stands {@code min_fill}+ over the ground, and outside tight curves. */
    public record GuardRail(BlockStateProvider block, int minFill) {

        public static final Codec<GuardRail> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("block").forGetter(GuardRail::block),
                Codec.intRange(1, 64).optionalFieldOf("min_fill", 3).forGetter(GuardRail::minFill))
                .apply(i, GuardRail::new));
    }

    /** Standing sign facing traffic at each stop line; up to 4 {@code lines}. */
    public record Sign(BlockStateProvider block, List<String> lines) {

        public static final Codec<Sign> CODEC = RecordCodecBuilder.create(i -> i.group(
                RoadPalettes.CODEC.fieldOf("block").forGetter(Sign::block),
                Codec.STRING.listOf(0, 4).optionalFieldOf("lines", List.of("", "STOP")).forGetter(Sign::lines))
                .apply(i, Sign::new));
    }

    /** Biome swap of the structural slots only; the driving surface stays constant across biome edges. */
    public record Theme(HolderSet<Biome> biomes, Optional<BlockStateProvider> structure,
            Optional<BlockStateProvider> accent, Optional<BlockStateProvider> wall) {

        public static final Codec<Theme> CODEC = RecordCodecBuilder.create(i -> i.group(
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biomes").forGetter(Theme::biomes),
                RoadPalettes.CODEC.optionalFieldOf("structure").forGetter(Theme::structure),
                RoadPalettes.CODEC.optionalFieldOf("accent").forGetter(Theme::accent),
                RoadPalettes.CODEC.optionalFieldOf("wall").forGetter(Theme::wall))
                .apply(i, Theme::new));
    }

    /** First {@link Theme} claiming the biome wins. */
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
