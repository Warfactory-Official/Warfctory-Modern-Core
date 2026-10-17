package com.norwood.wfcore.common.worldgen.resource;

import net.minecraft.resources.ResourceLocation;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Membership field over XZ, in [0, 1]. Every ramp = smoothstep over {@code falloff}; {@code falloff = 0} => hard
 * edge. Formulas mirrored by {@code worldgen-workspace} allocation.go; change both.
 */
public sealed interface Zone {

    List<String> TYPES = List.of("circle", "rect", "density", "noise", "cells", "intersect", "union", "multiply",
            "add", "blend", "subtract", "invert", "remap", "warp");

    Codec<Zone> CODEC = Codec.STRING.validate(t -> TYPES.contains(t) ? DataResult.success(t)
            : DataResult.error(() -> "unknown zone type " + t + ", expected one of " + TYPES))
            .dispatch("type", Zone::type, type -> switch (type) {
        case "circle" -> Circle.CODEC;
        case "rect" -> Rect.CODEC;
        case "density" -> Density.CODEC;
        case "noise" -> Noise.CODEC;
        case "cells" -> Cells.CODEC;
        case "intersect", "union", "multiply", "add", "blend" -> Combine.codec(type);
        case "subtract" -> Subtract.CODEC;
        case "invert" -> Invert.CODEC;
        case "remap" -> Remap.CODEC;
        case "warp" -> Warp.CODEC;
        default -> throw new IllegalStateException(type);
    });

    String type();

    /** 1 inside, 0 beyond {@code edge + falloff}; {@code outside} = distance past the edge, <= 0 inside. */
    static double ramp(double outside, double falloff) {
        if (outside <= 0.0) {
            return 1.0;
        }
        if (outside >= falloff) {
            return 0.0;
        }
        return smooth(1.0 - outside / falloff);
    }

    static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    record Circle(double x, double z, double radius, double falloff) implements Zone {

        static final MapCodec<Circle> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(Circle::x),
                Codec.DOUBLE.fieldOf("z").forGetter(Circle::z),
                Codec.DOUBLE.fieldOf("radius").forGetter(Circle::radius),
                Codec.DOUBLE.optionalFieldOf("falloff", 0.0).forGetter(Circle::falloff))
                .apply(i, Circle::new));

        @Override
        public String type() {
            return "circle";
        }
    }

    record Rect(double minX, double minZ, double maxX, double maxZ, double falloff) implements Zone {

        static final MapCodec<Rect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("min_x").forGetter(Rect::minX),
                Codec.DOUBLE.fieldOf("min_z").forGetter(Rect::minZ),
                Codec.DOUBLE.fieldOf("max_x").forGetter(Rect::maxX),
                Codec.DOUBLE.fieldOf("max_z").forGetter(Rect::maxZ),
                Codec.DOUBLE.optionalFieldOf("falloff", 0.0).forGetter(Rect::falloff))
                .apply(i, Rect::new));

        @Override
        public String type() {
            return "rect";
        }
    }

    /** {@code min}/{@code max} absent => unbounded on that side. Sampled at {@code y}. */
    record Density(ResourceLocation function, Optional<Double> min, Optional<Double> max, double falloff, int y)
            implements Zone {

        static final MapCodec<Density> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("function").forGetter(Density::function),
                Codec.DOUBLE.optionalFieldOf("min").forGetter(Density::min),
                Codec.DOUBLE.optionalFieldOf("max").forGetter(Density::max),
                Codec.DOUBLE.optionalFieldOf("falloff", 0.0).forGetter(Density::falloff),
                Codec.INT.optionalFieldOf("y", 63).forGetter(Density::y))
                .apply(i, Density::new));

        @Override
        public String type() {
            return "density";
        }
    }

    /** Octaves seeded under {@code wfcore:resource_zone} by {@link Noise#seed} (default: zone name). */
    record InlineNoise(int firstOctave, List<Double> amplitudes) {

        static final Codec<InlineNoise> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("first_octave").forGetter(InlineNoise::firstOctave),
                Codec.DOUBLE.listOf(1, Integer.MAX_VALUE).fieldOf("amplitudes").forGetter(InlineNoise::amplitudes))
                .apply(i, InlineNoise::new));
    }

    /** Registered noise id or inline octaves. */
    Codec<Either<ResourceLocation, InlineNoise>> NOISE = Codec.either(ResourceLocation.CODEC, InlineNoise.CODEC);

    /** min/max absent => (n + 1) / 2 clamped; else ramps in noise units. Sampled at (x * xz_scale, 0, z * xz_scale). */
    record Noise(Either<ResourceLocation, InlineNoise> noise, double xzScale, Optional<String> seed,
            Optional<Double> min, Optional<Double> max, double falloff) implements Zone {

        static final MapCodec<Noise> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                NOISE.fieldOf("noise").forGetter(Noise::noise),
                Codec.DOUBLE.optionalFieldOf("xz_scale", 1.0).forGetter(Noise::xzScale),
                Codec.STRING.optionalFieldOf("seed").forGetter(Noise::seed),
                Codec.DOUBLE.optionalFieldOf("min").forGetter(Noise::min),
                Codec.DOUBLE.optionalFieldOf("max").forGetter(Noise::max),
                Codec.DOUBLE.optionalFieldOf("falloff", 0.0).forGetter(Noise::falloff))
                .apply(i, Noise::new));

        @Override
        public String type() {
            return "noise";
        }
    }

    /** Worley patches; min/max absent => the cell's uniform value, else 1 in cells with value in [min, max). */
    record Cells(double size, double jitter, Optional<String> seed, Optional<Double> min, Optional<Double> max,
            double falloff) implements Zone {

        static final MapCodec<Cells> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("size").forGetter(Cells::size),
                Codec.DOUBLE.optionalFieldOf("jitter", 1.0).forGetter(Cells::jitter),
                Codec.STRING.optionalFieldOf("seed").forGetter(Cells::seed),
                Codec.DOUBLE.optionalFieldOf("min").forGetter(Cells::min),
                Codec.DOUBLE.optionalFieldOf("max").forGetter(Cells::max),
                Codec.DOUBLE.optionalFieldOf("falloff", 0.0).forGetter(Cells::falloff))
                .apply(i, Cells::new));

        @Override
        public String type() {
            return "cells";
        }
    }

    /** intersect = min, union = max, multiply, add (capped at 1), blend = weighted mean. */
    record Combine(String type, List<String> zones, Optional<List<Double>> weights) implements Zone {

        static MapCodec<Combine> codec(String type) {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.listOf().fieldOf("zones").forGetter(Combine::zones),
                    Codec.DOUBLE.listOf().optionalFieldOf("weights").forGetter(Combine::weights))
                    .apply(i, (zones, weights) -> new Combine(type, zones, weights)));
        }
    }

    /** max(zone - sum(subtract), 0). */
    record Subtract(String zone, List<String> subtract) implements Zone {

        static final MapCodec<Subtract> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("zone").forGetter(Subtract::zone),
                Codec.STRING.listOf().fieldOf("subtract").forGetter(Subtract::subtract))
                .apply(i, Subtract::new));

        @Override
        public String type() {
            return "subtract";
        }
    }

    /** 1 - named zone. */
    record Invert(String zone) implements Zone {

        static final MapCodec<Invert> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("zone").forGetter(Invert::zone))
                .apply(i, Invert::new));

        @Override
        public String type() {
            return "invert";
        }
    }

    /** smooth(clamp((zone - low) / (high - low))). */
    record Remap(String zone, double low, double high) implements Zone {

        static final MapCodec<Remap> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("zone").forGetter(Remap::zone),
                Codec.DOUBLE.fieldOf("low").forGetter(Remap::low),
                Codec.DOUBLE.fieldOf("high").forGetter(Remap::high))
                .apply(i, Remap::new));

        @Override
        public String type() {
            return "remap";
        }
    }

    /** Zone sampled at (x, z) + amplitude * (noise seed/x, noise seed/z); no density below. */
    record Warp(String zone, Either<ResourceLocation, InlineNoise> noise, double xzScale, double amplitude,
            Optional<String> seed) implements Zone {

        static final MapCodec<Warp> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("zone").forGetter(Warp::zone),
                NOISE.fieldOf("noise").forGetter(Warp::noise),
                Codec.DOUBLE.optionalFieldOf("xz_scale", 1.0).forGetter(Warp::xzScale),
                Codec.DOUBLE.fieldOf("amplitude").forGetter(Warp::amplitude),
                Codec.STRING.optionalFieldOf("seed").forGetter(Warp::seed))
                .apply(i, Warp::new));

        @Override
        public String type() {
            return "warp";
        }
    }

    /** Name table type, for {@link ResourceAllocation}. */
    Codec<Map<String, Zone>> TABLE = Codec.unboundedMap(Codec.STRING, CODEC);
}
