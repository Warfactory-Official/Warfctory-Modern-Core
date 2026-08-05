package com.norwood.wfcore.common.worldgen.transplant;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVein;
import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OrePlacer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;


public final class VeinBackfill {

    private VeinBackfill() {}

    /** @param veinsPlaced number of transplant veins rooted here; @param chunksTouched chunks written */
    public record Result(int veinsPlaced, int chunksTouched) {
        public static final Result EMPTY = new Result(0, 0);
    }

    /**
     * @param level       the (server) level to write into
     * @param origin       a grid-aligned chunk (x % gridSize == 0 && z % gridSize == 0)
     * @param isGenerated  may we write into this chunk? Only already-generated chunks return true.
     */
    public static Result placeAt(ServerLevel level, ChunkPos origin, Predicate<ChunkPos> isGenerated) {
        ChunkGenerator chunkGen = level.getChunkSource().getGenerator();
        OrePlacer placer = new OrePlacer();
        OreGenerator generator = placer.getOreGenCache().getOreGenerator();

        // Same derivation as live worldgen: selection + per-layer rolls from seed ^ origin.
        List<GeneratedVeinMetadata> all = generator.generateMetadata(level, chunkGen, origin);
        if (all.isEmpty()) {
            return Result.EMPTY;
        }

        List<GeneratedVeinMetadata> ours = new ArrayList<>();
        for (GeneratedVeinMetadata md : all) {
            if (VeinTransplant.isTransplantId(md.id())) {
                ours.add(md);
            }
        }
        if (ours.isEmpty()) {
            return Result.EMPTY;
        }

        List<GeneratedVein> veins = generator.generateOres(level, ours, origin);
        if (veins.isEmpty()) {
            return Result.EMPTY;
        }

        RandomSource random = new XoroshiroRandomSource(level.getSeed() ^ origin.toLong());
        Set<ChunkPos> touched = new HashSet<>();
        BulkSectionAccess access = new BulkSectionAccess(level);
        try {
            for (GeneratedVein vein : veins) {
                for (ChunkPos cp : new ArrayList<>(vein.getGeneratedChunks())) {
                    if (!isGenerated.test(cp)) {
                        continue; // never expand the world
                    }
                    level.getChunk(cp.x, cp.z);
                    placer.placeVein(cp, random, access, vein, null);
                    touched.add(cp);
                }
            }
        } finally {
            access.close();
        }

        for (ChunkPos cp : touched) {
            level.getChunk(cp.x, cp.z).setUnsaved(true);
        }
        return new Result(ours.size(), touched.size());
    }
}
