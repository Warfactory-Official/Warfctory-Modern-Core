package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.generator.VeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.generator.veins.CuboidVeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.generator.veins.DikeVeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.generator.veins.VeinedVeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;

import com.norwood.wfcore.common.worldgen.rock.RockLayers;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * GT vein heights are absolute; {@link RockLayers} tops follow the surface. Origin and absolute generator bounds
 * (veined range, dike column + block ranges) remapped through {@link RockLayers#gtY} at the vein's column.
 */
@Mixin(value = OreGenerator.class, remap = false)
public class GTOreFrameMixin {

    @Inject(method = "computeVeinOrigin", at = @At("RETURN"), cancellable = true)
    private static void wfcore$frameOrigin(WorldGenLevel level, ChunkGenerator generator, ChunkPos pos,
                                           RandomSource random, BlockPos veinCenter, GTOreDefinition entry,
                                           CallbackInfoReturnable<Optional<BlockPos>> cir) {
        cir.getReturnValue().ifPresent(origin -> {
            IntUnaryOperator map = wfcore$frame(level, origin);
            if (map != null) {
                cir.setReturnValue(Optional.of(origin.atY(map.applyAsInt(origin.getY()))));
            }
        });
    }

    @ModifyArg(method = "generateOres(Lcom/gregtechceu/gtceu/api/data/worldgen/ores/OreGenerator$VeinConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Optional;",
               at = @At(value = "INVOKE",
                        target = "Lcom/gregtechceu/gtceu/api/data/worldgen/generator/VeinGenerator;generate(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/util/RandomSource;Lcom/gregtechceu/gtceu/api/data/worldgen/GTOreDefinition;Lnet/minecraft/core/BlockPos;)Ljava/util/Map;"),
               index = 2)
    private GTOreDefinition wfcore$frameBounds(WorldGenLevel level, RandomSource random, GTOreDefinition definition,
                                               BlockPos center) {
        IntUnaryOperator map = wfcore$frame(level, center);
        if (map == null) {
            return definition;
        }
        VeinGenerator mapped;
        switch (definition.veinGenerator()) {
            case VeinedVeinGenerator v -> {
                var c = (VeinedVeinGenerator) v.copy();
                c.minYLevel = map.applyAsInt(v.minYLevel);
                c.maxYLevel = map.applyAsInt(v.maxYLevel);
                mapped = c;
            }
            case DikeVeinGenerator d -> {
                var c = (DikeVeinGenerator) d.copy();
                c.minYLevel = map.applyAsInt(d.minYLevel);
                c.maxYLevel = map.applyAsInt(d.maxYLevel);
                c.blocks = d.blocks.stream().map(b -> new DikeVeinGenerator.DikeBlockDefinition(b.block(),
                        b.weight(), map.applyAsInt(b.minY()), map.applyAsInt(b.maxY()))).toList();
                mapped = c;
            }
            case CuboidVeinGenerator ignored -> throw new IllegalStateException(
                    "cuboid vein bounds not remapped onto rock layers: " + definition);
            default -> {
                return definition;
            }
        }
        return new GTOreDefinition(definition).veinGenerator(mapped);
    }

    /** null => level has no rock layers. */
    @Unique
    private static IntUnaryOperator wfcore$frame(WorldGenLevel level, BlockPos column) {
        ServerLevel server = level.getLevel();
        return RockLayers.of(server).map(rock -> {
            double s = RockLayers.column(rock.surface(server.getChunkSource().randomState()), column.getX(),
                    column.getZ());
            return (IntUnaryOperator) y -> rock.gtY(y, s);
        }).orElse(null);
    }
}
