package com.norwood.wfcore.common.worldgen.density;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * |(blockX, blockZ) - (x, z)| * scale; JSON {@code square} => max(|dx|, |dz|) * scale (component {@code chebyshev}:
 * {@code DensityFunction.square()} taken). Record: lithostitched:sqrt has no equals => NoiseChunk memo never shares.
 */
public record Distance(double x, double z, double scale, boolean chebyshev) implements DensityFunction.SimpleFunction {

    public static final MapCodec<Distance> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Distance::x),
            Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(Distance::z),
            Codec.DOUBLE.fieldOf("scale").forGetter(Distance::scale),
            Codec.BOOL.optionalFieldOf("square", false).forGetter(Distance::chebyshev)).apply(i, Distance::new));

    public static final KeyDispatchDataCodec<Distance> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    // |blockX|, |blockZ| < 3.0E7
    private static final double MAX = 3.0E7 * Math.sqrt(2.0) * 2.0;

    @Override
    public double compute(FunctionContext ctx) {
        double dx = ctx.blockX() + -x;
        double dz = ctx.blockZ() + -z;
        if (chebyshev) {
            return Math.max(Math.abs(dx), Math.abs(dz)) * scale;
        }
        return Math.sqrt(dx * dx + dz * dz) * scale;
    }

    @Override
    public double minValue() {
        return 0.0;
    }

    @Override
    public double maxValue() {
        return MAX * Math.abs(scale);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
