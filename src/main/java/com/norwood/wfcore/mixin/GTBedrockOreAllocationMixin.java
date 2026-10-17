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
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.OreVeinWorldEntry;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTMath;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndexManager;
import com.norwood.wfcore.common.worldgen.resource.ResourceSystem;

import org.jetbrains.annotations.Nullable;
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
 * {@link GTBedrockFluidAllocationMixin} for bedrock ore; cell = bedrockOreDistance^2 chunks, fields sampled at the
 * vein origin chunk. Richness scales every chunk the vein writes.
 */
@Mixin(value = BedrockOreVeinSavedData.class, remap = false)
public abstract class GTBedrockOreAllocationMixin extends SavedData {

    @Shadow
    @Final
    public HashMap<ChunkPos, OreVeinWorldEntry> veinOres;

    @Shadow
    @Final
    private ServerLevel serverLevel;

    @Shadow
    public abstract void createVein(ChunkPos pos, @Nullable Holder<BedrockOreDefinition> holder);

    @Inject(method = "getOreVeinWorldEntry", at = @At("HEAD"), cancellable = true)
    private void wfcore$allocate(int chunkX, int chunkZ, CallbackInfoReturnable<OreVeinWorldEntry> cir) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        int minDistance = ConfigHolder.INSTANCE.worldgen.oreVeins.bedrockOreDistance;
        if (veinOres.containsKey(pos) || chunkX % minDistance != 0 || chunkZ % minDistance != 0) {
            return;
        }
        AllocationIndex index = AllocationIndexManager.get(serverLevel);
        if (!index.has(ResourceSystem.BEDROCK_ORE)) {
            return;
        }
        double cx = (chunkX << 4) + 8.0;
        double cz = (chunkZ << 4) + 8.0;
        int query = RandomSource.create(GTMath.hashLongs(serverLevel.getSeed(),
                BedrockOreVeinSavedData.getVeinCoord(chunkX), BedrockOreVeinSavedData.getVeinCoord(chunkZ)))
                .nextInt();
        var biome = serverLevel.getBiome(new BlockPos(chunkX << 4, 64, chunkZ << 4));

        List<Holder<BedrockOreDefinition>> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0.0;
        for (var holder : serverLevel.registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_ORE)
                .asHolderIdMap()) {
            BedrockOreDefinition def = holder.value();
            if (!def.canGenerate() || !(def.dimensionFilter().isEmpty() || def.dimensionFilter().stream()
                    .anyMatch(dim -> WorldGeneratorUtils.isSameDimension(dim, serverLevel.dimension())))) {
                continue;
            }
            int stock = def.weight() + def.biomeWeightModifier().applyAsInt(biome);
            if (stock <= 0) {
                continue;
            }
            double weight = stock * index.multiplier(ResourceSystem.BEDROCK_ORE,
                    holder.unwrapKey().orElseThrow().location().toString(), cx, cz);
            if (weight > 0.0) {
                candidates.add(holder);
                weights.add(weight);
                total += weight;
            }
        }

        Holder<BedrockOreDefinition> definition = null;
        double richness = 1.0;
        List<AllocationIndex.Pick> picks = index.picks(ResourceSystem.BEDROCK_ORE, chunkX, chunkZ);
        if (picks != null) {
            AllocationIndex.Pick pick = picks.getFirst();
            richness = pick.richness();
            if (!pick.target().isEmpty()) {
                definition = serverLevel.registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_ORE)
                        .getHolderOrThrow(ResourceKey.create(GTRegistries.Keys.BEDROCK_ORE,
                                ResourceLocation.parse(pick.target())));
            }
        } else {
            double roll = Integer.toUnsignedLong(query) / 4294967296.0 * total;
            for (int i = 0; i < candidates.size(); i++) {
                roll -= weights.get(i);
                if (roll < 0.0) {
                    definition = candidates.get(i);
                    richness = index.richness(ResourceSystem.BEDROCK_ORE,
                            definition.unwrapKey().orElseThrow().location().toString(), cx, cz);
                    break;
                }
            }
        }
        if (definition != null) {
            createVein(pos, definition);
            if (richness != 1.0) {
                int radius = definition.value().size() / 2;
                for (int x = chunkX - radius; x <= chunkX + radius; x++) {
                    for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                        OreVeinWorldEntry e = veinOres.get(new ChunkPos(x, z));
                        if (e != null && e.getDefinition() == definition) {
                            ((GTOreVeinWorldEntryAccessor) e).wfcore$setOreYield(
                                    Math.max(1, (int) Math.round(e.getOreYield() * richness)));
                        }
                    }
                }
            }
        }
        setDirty();
        OreVeinWorldEntry entry = veinOres.get(pos);
        if (entry == null) {
            entry = new OreVeinWorldEntry(null, 0, BedrockOreVeinSavedData.MAXIMUM_VEIN_OPERATIONS);
            veinOres.put(pos, entry);
        }
        cir.setReturnValue(entry);
    }
}
