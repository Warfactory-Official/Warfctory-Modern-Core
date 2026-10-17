package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.IWorldGenLayer;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGeneratorUtils.WeightedVein;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Per vein grid cell: paint / assign force the picked vein in its layer (empty pick => no vein in any layer), else
 * weight rules scale each candidate's weight at the vein centre. Richness scales the generated vein's ore fraction.
 */
@Mixin(value = OreGenerator.class, remap = false)
public class GTOreGeneratorAllocationMixin {

    /** Weights are ints: scale up first so fractional multipliers keep their ratio. */
    @Unique
    private static final int WFCORE$RESOLUTION = 64;

    // getEntries' stream is drained inside createConfigs on this thread; getEntry has no position.
    @Unique
    private static final ThreadLocal<BlockPos> WFCORE$CENTER = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<ChunkPos> WFCORE$ORIGIN = new ThreadLocal<>();
    // generateOres(VeinConfiguration, ...) -> veinGenerator().generate(...) on this thread.
    @Unique
    private static final ThreadLocal<OreGenerator.VeinConfiguration> WFCORE$VEIN = new ThreadLocal<>();

    @Inject(method = "createConfigs", at = @At("HEAD"))
    private void wfcore$captureOrigin(WorldGenLevel level, ChunkGenerator generator, ChunkPos chunkPos,
                                      CallbackInfoReturnable<List<OreGenerator.VeinConfiguration>> cir) {
        WFCORE$ORIGIN.set(chunkPos);
    }

    @Inject(method = "getEntries", at = @At("HEAD"))
    private void wfcore$captureCenter(WorldGenLevel level, BlockPos veinCenter, XoroshiroRandomSource random,
                                      CallbackInfoReturnable<Stream<Holder<GTOreDefinition>>> cir) {
        WFCORE$CENTER.set(veinCenter);
    }

    @ModifyVariable(method = "getEntry", at = @At("STORE"), ordinal = 0)
    private List<WeightedVein> wfcore$allocate(List<WeightedVein> veins, WorldGenLevel level, Holder<Biome> biome,
                                               RandomSource random, IWorldGenLayer layer) {
        AllocationIndex index = AllocationIndexManager.get(level.getLevel());
        if (!index.has(ResourceSystem.ORE_VEIN)) {
            return veins;
        }
        ChunkPos origin = WFCORE$ORIGIN.get();
        List<AllocationIndex.Pick> picks = index.picks(ResourceSystem.ORE_VEIN, origin.x, origin.z);
        if (picks != null) {
            String target = picks.getFirst().target();
            if (target.isEmpty()) {
                return List.of();
            }
            Optional<Holder.Reference<GTOreDefinition>> vein = level.registryAccess()
                    .registryOrThrow(GTRegistries.Keys.ORE_VEIN)
                    .getHolder(ResourceKey.create(GTRegistries.Keys.ORE_VEIN, ResourceLocation.parse(target)));
            if (vein.isPresent() && vein.get().value().layer().equals(layer)) {
                return List.of(new WeightedVein(vein.get(), 1));
            }
        }
        BlockPos center = WFCORE$CENTER.get();
        List<WeightedVein> out = new ArrayList<>(veins.size());
        for (WeightedVein vein : veins) {
            double m = index.multiplier(ResourceSystem.ORE_VEIN,
                    vein.vein().unwrapKey().orElseThrow().location().toString(), center.getX(), center.getZ());
            int weight = (int) Math.round(vein.weight() * WFCORE$RESOLUTION * m);
            if (weight > 0) {
                out.add(new WeightedVein(vein.vein(), weight));
            }
        }
        return out;
    }

    @Inject(method = "generateOres(Lcom/gregtechceu/gtceu/api/data/worldgen/ores/OreGenerator$VeinConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Optional;",
            at = @At("HEAD"))
    private void wfcore$captureVein(OreGenerator.VeinConfiguration config, WorldGenLevel level, ChunkPos chunkPos,
                                    CallbackInfoReturnable<Optional<?>> cir) {
        WFCORE$VEIN.set(config);
    }

    /** Richness: pick's (painted / assigned to this vein) else weight rules' at the vein centre; ore fraction capped at 1. */
    @ModifyArg(method = "generateOres(Lcom/gregtechceu/gtceu/api/data/worldgen/ores/OreGenerator$VeinConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Optional;",
               at = @At(value = "INVOKE",
                        target = "Lcom/gregtechceu/gtceu/api/data/worldgen/generator/VeinGenerator;generate(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/util/RandomSource;Lcom/gregtechceu/gtceu/api/data/worldgen/GTOreDefinition;Lnet/minecraft/core/BlockPos;)Ljava/util/Map;"),
               index = 2)
    private GTOreDefinition wfcore$richness(WorldGenLevel level, RandomSource random, GTOreDefinition definition,
                                            BlockPos center) {
        AllocationIndex index = AllocationIndexManager.get(level.getLevel());
        if (!index.has(ResourceSystem.ORE_VEIN)) {
            return definition;
        }
        var data = WFCORE$VEIN.get().data();
        String id = data.definition().unwrapKey().orElseThrow().location().toString();
        List<AllocationIndex.Pick> picks = index.picks(ResourceSystem.ORE_VEIN, data.originChunk().x,
                data.originChunk().z);
        double r = picks != null && picks.getFirst().target().equals(id) ? picks.getFirst().richness()
                : index.richness(ResourceSystem.ORE_VEIN, id, center.getX(), center.getZ());
        if (r == 1.0) {
            return definition;
        }
        return new GTOreDefinition(definition).density((float) Math.min(1.0, definition.density() * r));
    }
}
