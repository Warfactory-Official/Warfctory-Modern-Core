package com.norwood.wfcore.common.ballistics;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.storage.RegionFile;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.RadarChunkParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.io.DataInputStream;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AsyncTerrainSource {

    private final int minSection;
    private final int maxSection;
    private final HolderGetter<Block> blockLookup;
    private final Path regionDir;

    private final ConcurrentHashMap<Long, Object> cache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, CompletableFuture<Void>> inFlightChunks = new ConcurrentHashMap<>();

    public AsyncTerrainSource(ServerLevel level) {

        this.blockLookup = level.holderLookup(Registries.BLOCK);
        this.regionDir = RadarChunkParser.regionDirectory(level);
        this.minSection = level.getMinSection();
        this.maxSection = level.getMaxSection();
    }

    private static long sectionKey(int sx, int sy, int sz) {

        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sy & 0xFFFFF) << 22) | (sz & 0x3FFFFFL);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public SectionSolidity get(int sx, int sy, int sz) {

        if (sy < minSection || sy >= maxSection) {
            return SectionSolidity.AIR;
        }
        Object v = cache.get(sectionKey(sx, sy, sz));
        if (v instanceof SectionSolidity ss) {
            return ss;
        }
        if (v instanceof CompletableFuture) {
            return null;
        }

        kickOffChunk(sx, sz);
        return null;
    }

    public boolean isSolidBlock(int bx, int by, int bz) {
        SectionSolidity ss = get(bx >> 4, by >> 4, bz >> 4);
        if (ss == null) {
            return false;
        }
        return ss.isSolid(bx & 15, by & 15, bz & 15);
    }

    private void kickOffChunk(int cx, int cz) {
        long ck = chunkKey(cx, cz);
        if (inFlightChunks.containsKey(ck)) {
            return;
        }
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> decodeChunkInto(cx, cz));

        if (inFlightChunks.putIfAbsent(ck, future) == null) {
            future.whenComplete((ignored, err) -> inFlightChunks.remove(ck));
        }

        for (int sy = minSection; sy < maxSection; sy++) {
            cache.putIfAbsent(sectionKey(cx, sy, cz), future);
        }
    }

    private void decodeChunkInto(int cx, int cz) {
        Int2ObjectMap<SectionSolidity> decoded = null;
        try {
            int regionX = cx >> 5;
            int regionZ = cz >> 5;
            Path mca = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
            if (mca.toFile().exists()) {
                try (RegionFile region = new RegionFile(mca, regionDir, true)) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    if (region.hasChunk(pos)) {
                        try (DataInputStream dis = region.getChunkDataInputStream(pos)) {
                            if (dis != null) {
                                CompoundTag chunk = NbtIo.read(dis);
                                decoded = SolidityDecoder.decodeChunk(chunk, blockLookup);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            WFCore.LOGGER.error("Ballistics: off-thread terrain decode failed for chunk {},{}", cx, cz, e);
            decoded = null;
        }

        for (int sy = minSection; sy < maxSection; sy++) {
            SectionSolidity ss = (decoded != null) ? decoded.get(sy) : null;
            if (ss == null) {
                ss = SectionSolidity.AIR;
            }
            cache.put(sectionKey(cx, sy, cz), ss);
        }
    }

    public void invalidateChunk(int cx, int cz) {
        for (int sy = minSection; sy < maxSection; sy++) {
            cache.remove(sectionKey(cx, sy, cz));
        }
        inFlightChunks.remove(chunkKey(cx, cz));
    }
}
