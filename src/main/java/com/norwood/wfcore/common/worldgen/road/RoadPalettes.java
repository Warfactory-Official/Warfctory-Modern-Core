package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.function.Function;

/**
 * The codec every block slot in a {@link RoadType} uses. Accepts either a bare block id —
 * {@code "surface": "minecraft:gray_concrete"} — or any vanilla {@link BlockStateProvider}, so a pack that wants a
 * weathered surface can drop in a {@code minecraft:weighted_state_provider} or {@code minecraft:noise_provider}
 * in the same slot without wfcore growing a palette format of its own.
 */
public final class RoadPalettes {

    /**
     * Decode-only. {@code BuiltInRegistries.BLOCK} is a defaulted registry, so {@code get} on an unknown id
     * quietly answers air and a typo would silently erase the road instead of failing the pack load — hence the
     * explicit {@code containsKey} gate.
     */
    private static final Codec<BlockStateProvider> BY_ID = ResourceLocation.CODEC.flatXmap(
            id -> {
                if (!BuiltInRegistries.BLOCK.containsKey(id)) {
                    return DataResult.error(() -> "Unknown block '" + id + "'");
                }
                Block block = BuiltInRegistries.BLOCK.get(id);
                return DataResult.success(BlockStateProvider.simple(block.defaultBlockState()));
            },
            provider -> DataResult.error(() -> "Block providers always re-encode in their long form"));

    public static final Codec<BlockStateProvider> CODEC = Codec.either(BY_ID, BlockStateProvider.CODEC)
            .xmap(either -> either.map(Function.identity(), Function.identity()), Either::right);

    private RoadPalettes() {}
}
