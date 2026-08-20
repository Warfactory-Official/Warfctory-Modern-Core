package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.OptionalInt;

/**
 * The road plan, as resolved by {@code worldgen-workspace} and written into the datapack at
 * {@code data/<ns>/wf_road_network/<id>.json}. Pure XZ geometry: junctions, and links whose {@code points} are
 * the filleted centreline the router produced. Nothing here knows about height or blocks — the elevation profile
 * is solved in {@link RoadProfile} against the real terrain this world generates, and the block composition comes
 * from whichever {@link RoadType} claims each link.
 *
 * <p>{@code points} is a flat {@code [x, z, x, z, ...]} array rather than a list of pairs: a full network runs to
 * tens of thousands of vertices, and the flat form halves both the file and the parse.
 */
public record RoadNetwork(ResourceKey<Level> dimension, int seaLevel, List<Junction> junctions, List<Link> links) {

    public static final Codec<RoadNetwork> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension", Level.OVERWORLD)
                    .forGetter(RoadNetwork::dimension),
            Codec.INT.optionalFieldOf("sea_level", 63).forGetter(RoadNetwork::seaLevel),
            Junction.CODEC.listOf().optionalFieldOf("junctions", List.of()).forGetter(RoadNetwork::junctions),
            Link.CODEC.listOf().fieldOf("links").forGetter(RoadNetwork::links))
            .apply(i, RoadNetwork::new));

    /** A node of the network. Its array position is the index links refer to. */
    public record Junction(int x, int z, int landmass) {

        public static final Codec<Junction> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Junction::x),
                Codec.INT.fieldOf("z").forGetter(Junction::z),
                Codec.INT.optionalFieldOf("landmass", 0).forGetter(Junction::landmass))
                .apply(i, Junction::new));
    }

    /**
     * One routed road. {@code from} / {@code to} are junction indices where the link is a planned connection;
     * dendritic tracks carry only a {@code from}, because their far end is a dead end and their near end attaches
     * to an arbitrary point on whatever was already grown rather than to a junction.
     */
    public record Link(String kind, OptionalInt from, OptionalInt to, boolean loop, boolean seaPassage,
            List<Integer> points) {

        private static final Codec<List<Integer>> POINTS = Codec.INT.listOf().validate(list -> {
            if (list.size() < 4 || list.size() % 2 != 0) {
                return DataResult.error(() -> "points must be an even-length [x, z, ...] array of at least 2 points");
            }
            return DataResult.success(list);
        });

        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Link::kind),
                intOptional("from", Link::from),
                intOptional("to", Link::to),
                Codec.BOOL.optionalFieldOf("loop", false).forGetter(Link::loop),
                Codec.BOOL.optionalFieldOf("sea_passage", false).forGetter(Link::seaPassage),
                POINTS.fieldOf("points").forGetter(Link::points))
                .apply(i, Link::new));

        private static RecordCodecBuilder<Link, OptionalInt> intOptional(String name,
                java.util.function.Function<Link, OptionalInt> getter) {
            return Codec.INT.optionalFieldOf(name)
                    .xmap(o -> o.map(OptionalInt::of).orElse(OptionalInt.empty()),
                            o -> o.isPresent() ? java.util.Optional.of(o.getAsInt()) : java.util.Optional.empty())
                    .forGetter(getter);
        }

        public int pointCount() {
            return points.size() / 2;
        }

        public int x(int index) {
            return points.get(index * 2);
        }

        public int z(int index) {
            return points.get(index * 2 + 1);
        }
    }
}
