package com.norwood.wfcore.common.worldgen.density;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * wfworld continent skeleton: min over continents of u, u < 1 land. One leaf in place of the disc CSG tree:
 * lithosphere's terrain graph inlines {@code continents} ~476x per NoiseChunk.
 * Arithmetic == the JSON tree it replaces, bitwise; wfworld {@code df.continentSkeleton} mirrors it.
 */
public record ContinentSkeleton(List<Continent> continents) implements DensityFunction.SimpleFunction {

    public static final MapCodec<ContinentSkeleton> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Continent.CODEC.listOf().fieldOf("continents").forGetter(ContinentSkeleton::continents))
            .apply(i, ContinentSkeleton::new));

    public static final KeyDispatchDataCodec<ContinentSkeleton> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    /** sdf = |p - c| - r. */
    public record Disc(double x, double z, double r) {

        static final Codec<Disc> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(Disc::x),
                Codec.DOUBLE.fieldOf("z").forGetter(Disc::z),
                Codec.DOUBLE.fieldOf("r").forGetter(Disc::r)).apply(i, Disc::new));

        double sdf(double px, double pz) {
            double dx = px + -x;
            double dz = pz + -z;
            return Math.sqrt(dx * dx + dz * dz) + -r;
        }
    }

    /** s = max(min solids, max -bites); u = s*scale + 1. bound.sdf >= cull => far. */
    public record Continent(List<Disc> solids, List<Disc> bites, double scale, Disc bound, double cull, double far) {

        static final Codec<Continent> CODEC = RecordCodecBuilder.create(i -> i.group(
                Disc.CODEC.listOf().fieldOf("solids").forGetter(Continent::solids),
                Disc.CODEC.listOf().optionalFieldOf("bites", List.of()).forGetter(Continent::bites),
                Codec.DOUBLE.fieldOf("scale").forGetter(Continent::scale),
                Disc.CODEC.fieldOf("bound").forGetter(Continent::bound),
                Codec.DOUBLE.fieldOf("cull").forGetter(Continent::cull),
                Codec.DOUBLE.fieldOf("far").forGetter(Continent::far)).apply(i, Continent::new));

        double u(double px, double pz) {
            if (!(bound.sdf(px, pz) < cull)) {
                return far;
            }
            double s = Double.POSITIVE_INFINITY;
            for (Disc d : solids) {
                s = Math.min(s, d.sdf(px, pz));
            }
            if (!bites.isEmpty()) {
                double b = Double.NEGATIVE_INFINITY;
                for (Disc d : bites) {
                    b = Math.max(b, d.sdf(px, pz) * -1.0);
                }
                s = Math.max(s, b);
            }
            return s * scale + 1.0;
        }

        // s >= -max r; inside cull: s <= max(cull + 2*bound.r, max bite r).
        double lo() {
            double r = 0.0;
            for (Disc d : solids) {
                r = Math.max(r, d.r);
            }
            return Math.min(far, 1.0 - r * scale);
        }

        double hi() {
            double s = cull + 2.0 * bound.r;
            for (Disc d : bites) {
                s = Math.max(s, d.r);
            }
            return Math.max(far, s * scale + 1.0);
        }
    }

    @Override
    public double compute(FunctionContext ctx) {
        double px = ctx.blockX();
        double pz = ctx.blockZ();
        double out = Double.POSITIVE_INFINITY;
        for (Continent c : continents) {
            out = Math.min(out, c.u(px, pz));
        }
        return out;
    }

    @Override
    public double minValue() {
        return continents.stream().mapToDouble(Continent::lo).min().orElse(0.0);
    }

    @Override
    public double maxValue() {
        return continents.stream().mapToDouble(Continent::hi).min().orElse(0.0);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
