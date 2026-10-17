package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import com.gregtechceu.gtceu.api.data.worldgen.WorldGeneratorUtils;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.utils.GTMath;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Replaces GT's pick for unrolled chunks while the level has bedrock-fluid rules. Paint / assign on the 8x8-chunk
 * vein cell => that fluid (empty pick => none); else same query seed, biome and yield roll with weights scaled by
 * the multiplier at the vein's centre. Yield x richness.
 */
@Mixin(value = BedrockFluidVeinSavedData.class, remap = false)
public abstract class GTBedrockFluidAllocationMixin extends SavedData {

    @Shadow
    @Final
    public HashMap<ChunkPos, FluidVeinWorldEntry> veinFluids;

    @Shadow
    @Final
    private ServerLevel serverLevel;

    @Inject(method = "getFluidVeinWorldEntry", at = @At("HEAD"), cancellable = true)
    private void wfcore$allocate(int chunkX, int chunkZ, CallbackInfoReturnable<FluidVeinWorldEntry> cir) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (veinFluids.containsKey(pos)) {
            return;
        }
        AllocationIndex index = AllocationIndexManager.get(serverLevel);
        if (!index.has(ResourceSystem.BEDROCK_FLUID)) {
            return;
        }
        int veinX = BedrockFluidVeinSavedData.getVeinCoord(chunkX);
        int veinZ = BedrockFluidVeinSavedData.getVeinCoord(chunkZ);
        double span = BedrockFluidVeinSavedData.VEIN_CHUNK_SIZE * 16.0;
        double cx = (veinX + 0.5) * span;
        double cz = (veinZ + 0.5) * span;
        int query = RandomSource.create(GTMath.hashLongs(serverLevel.getSeed(), veinX, veinZ)).nextInt();
        var biome = serverLevel.getBiome(new BlockPos(chunkX << 4, 64, chunkZ << 4));

        List<Holder<BedrockFluidDefinition>> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0;
        for (var holder : serverLevel.registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID)
                .asHolderIdMap()) {
            BedrockFluidDefinition def = holder.value();
            if (!def.canGenerate() || !(def.getDimensionFilter().isEmpty() || def.getDimensionFilter().stream()
                    .anyMatch(dim -> WorldGeneratorUtils.isSameDimension(dim, serverLevel.dimension())))) {
                continue;
            }
            int stock = def.getWeight() + def.getBiomeWeightModifier().applyAsInt(biome);
            if (stock <= 0) {
                continue;
            }
            double weight = stock * index.multiplier(ResourceSystem.BEDROCK_FLUID,
                    holder.unwrapKey().orElseThrow().location().toString(), cx, cz);
            if (weight > 0.0) {
                candidates.add(holder);
                weights.add(weight);
                total += weight;
            }
        }

        Holder<BedrockFluidDefinition> definition = null;
        double richness = 1.0;
        List<AllocationIndex.Pick> picks = index.picks(ResourceSystem.BEDROCK_FLUID, chunkX, chunkZ);
        if (picks != null) {
            AllocationIndex.Pick pick = picks.getFirst();
            richness = pick.richness();
            if (!pick.target().isEmpty()) {
                definition = serverLevel.registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID)
                        .getHolderOrThrow(ResourceKey.create(GTRegistries.Keys.BEDROCK_FLUID,
                                ResourceLocation.parse(pick.target())));
            }
        } else {
            double roll = Integer.toUnsignedLong(query) / 4294967296.0 * total;
            for (int i = 0; i < candidates.size(); i++) {
                roll -= weights.get(i);
                if (roll < 0.0) {
                    definition = candidates.get(i);
                    richness = index.richness(ResourceSystem.BEDROCK_FLUID,
                            definition.unwrapKey().orElseThrow().location().toString(), cx, cz);
                    break;
                }
            }
        }

        var random = RandomSource.create(serverLevel.getSeed() ^ ChunkPos.asLong(chunkX, chunkZ));
        int maximumYield = 0;
        if (definition != null) {
            var value = definition.value();
            if (value.getMaximumYield() - value.getMinimumYield() <= 0) {
                maximumYield = value.getMinimumYield();
            } else {
                maximumYield = random.nextInt(value.getMaximumYield() - value.getMinimumYield()) +
                        value.getMinimumYield();
            }
            maximumYield = Math.min(maximumYield, value.getMaximumYield());
            if (richness != 1.0) {
                maximumYield = Math.max(1, (int) Math.round(maximumYield * richness));
            }
        }
        FluidVeinWorldEntry entry = new FluidVeinWorldEntry(definition, maximumYield,
                BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS);
        veinFluids.put(pos, entry);
        setDirty();
        cir.setReturnValue(entry);
    }
}
