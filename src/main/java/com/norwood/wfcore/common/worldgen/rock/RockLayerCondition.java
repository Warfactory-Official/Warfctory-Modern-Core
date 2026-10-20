package com.norwood.wfcore.common.worldgen.rock;

import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SurfaceRules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** {@code wfcore:rock_layer}: vertical_gradient whose top follows {@link RockLayers}. */
public record RockLayerCondition(Holder<RockLayers> layers, String layer, ResourceLocation randomName)
        implements SurfaceRules.ConditionSource {

    public static final MapCodec<RockLayerCondition> DATA_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFileCodec.create(RockLayers.REGISTRY, RockLayers.CODEC).fieldOf("layers")
                    .forGetter(RockLayerCondition::layers),
            Codec.STRING.validate(s -> s.equals("deeprock") || s.equals("deepslate") ? DataResult.success(s) :
                    DataResult.error(() -> "rock layer " + s))
                    .fieldOf("layer").forGetter(RockLayerCondition::layer),
            ResourceLocation.CODEC.fieldOf("random_name").forGetter(RockLayerCondition::randomName))
            .apply(i, RockLayerCondition::new));

    static final KeyDispatchDataCodec<RockLayerCondition> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    // per generator thread: surface corners of the chunk being built, shared by the deeprock + deepslate rules
    private static final ThreadLocal<ChunkCorners> CORNERS = ThreadLocal.withInitial(ChunkCorners::new);

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context ctx) {
        RockLayers rock = layers.value();
        RockLayers.Layer which = rock.layer(layer);
        DensityFunction surface = rock.surface(ctx.randomState);
        PositionalRandomFactory random = ctx.randomState.getOrCreateRandomFactory(randomName);
        int blend = rock.blend();
        return new SurfaceRules.Condition() {

            private long updated = Long.MIN_VALUE;
            private int top;

            @Override
            public boolean test() {
                if (updated != ctx.lastUpdateXZ) {
                    updated = ctx.lastUpdateXZ;
                    top = which.top(CORNERS.get().column(surface, ctx.blockX, ctx.blockZ));
                }
                int y = ctx.blockY;
                int bottom = top - blend;
                if (y <= bottom) {
                    return true;
                }
                if (y >= top) {
                    return false;
                }
                return random.at(ctx.blockX, y, ctx.blockZ).nextFloat() < Mth.map(y, bottom, top, 1.0, 0.0);
            }
        };
    }

    /** 5x5 quart corners of one chunk; {@link RockLayers#column} bitwise. */
    private static final class ChunkCorners {

        private DensityFunction surface;
        private int chunkX, chunkZ;
        private final double[] values = new double[25];

        double column(DensityFunction fn, int x, int z) {
            int cx = x >> 4, cz = z >> 4;
            if (fn != surface || cx != chunkX || cz != chunkZ) {
                surface = fn;
                chunkX = cx;
                chunkZ = cz;
                for (int j = 0; j < 5; j++) {
                    for (int i = 0; i < 5; i++) {
                        values[j * 5 + i] = RockLayers.corner(fn, (cx << 4) + 4 * i, (cz << 4) + 4 * j);
                    }
                }
            }
            int i = (x & 15) >> 2, j = (z & 15) >> 2;
            return Mth.lerp2((x & 3) / 4.0, (z & 3) / 4.0, values[j * 5 + i], values[j * 5 + i + 1],
                    values[(j + 1) * 5 + i], values[(j + 1) * 5 + i + 1]);
        }
    }
}
