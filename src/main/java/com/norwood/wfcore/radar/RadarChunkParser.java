package com.norwood.wfcore.radar;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.data.RadarRegistryData;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.io.DataInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;

public final class RadarChunkParser {

    private static final int SECTION_VOLUME = 16 * 16 * 16;

    private static volatile Set<ResourceLocation> gtMachineIds;

    private RadarChunkParser() {}

    @FunctionalInterface
    public interface PositionSink {
        void accept(long packed, int value);
    }

    public static int gtMachineValue(ResourceLocation id) {
        return isGtMachine(id) ? 1 : -1;
    }

    public static boolean isGtMachine(ResourceLocation id) {
        Set<ResourceLocation> ids = gtMachineIds;
        if (ids == null) {
            synchronized (RadarChunkParser.class) {
                ids = gtMachineIds;
                if (ids == null) {
                    ids = buildGtMachineIds();
                    gtMachineIds = ids;
                }
            }
        }
        return ids.contains(id);
    }

    private static Set<ResourceLocation> buildGtMachineIds() {
        Set<ResourceLocation> set = new HashSet<>();
        for (Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            if (entry.getValue() instanceof MetaMachineBlock) {
                set.add(entry.getKey().location());
            }
        }
        WFCore.LOGGER.info("Radar: indexed {} GregTech machine block ids for unfiltered scans", set.size());
        return set;
    }

    public static Path regionDirectory(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return DimensionType.getStorageFolder(level.dimension(), worldRoot).resolve("region");
    }


    public static CompletableFuture<Long2IntMap> scanDimension(ServerLevel level,
                                                               ToIntFunction<ResourceLocation> classifier) {
        Path regionDir = regionDirectory(level);
        File[] files = regionDir.toFile().listFiles((dir, name) -> name.endsWith(".mca"));
        if (files == null || files.length == 0) {
            return CompletableFuture.completedFuture(new Long2IntOpenHashMap());
        }

        List<CompletableFuture<Long2IntMap>> perFile = new ArrayList<>(files.length);
        for (File file : files) {
            perFile.add(CompletableFuture.supplyAsync(() -> {
                Long2IntOpenHashMap local = new Long2IntOpenHashMap();
                parseRegionFile(file, regionDir, classifier, local::put);
                return (Long2IntMap) local;
            }));
        }
        return CompletableFuture.allOf(perFile.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            Long2IntOpenHashMap merged = new Long2IntOpenHashMap();
            for (CompletableFuture<Long2IntMap> future : perFile) {
                merged.putAll(future.join());
            }
            return merged;
        });
    }

    public static void parseRegionFile(File mcaFile, Path regionDir, ToIntFunction<ResourceLocation> classifier,
                                       PositionSink sink) {
        String[] parts = mcaFile.getName().split("\\.");
        if (parts.length < 4) {
            return; // r.<x>.<z>.mca
        }
        int regionX;
        int regionZ;
        try {
            regionX = Integer.parseInt(parts[1]);
            regionZ = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        try (RegionFile region = new RegionFile(mcaFile.toPath(), regionDir, true)) {
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    ChunkPos pos = new ChunkPos(regionX * 32 + x, regionZ * 32 + z);
                    if (!region.hasChunk(pos)) {
                        continue;
                    }
                    try (DataInputStream dis = region.getChunkDataInputStream(pos)) {
                        if (dis != null) {
                            decodeChunk(NbtIo.read(dis), classifier, sink);
                        }
                    }
                }
            }
        } catch (Exception e) {
            WFCore.LOGGER.error("Radar scan: failed to parse region {}", mcaFile.getName(), e);
        }
    }

    public static void decodeChunk(CompoundTag chunk, ToIntFunction<ResourceLocation> classifier,
                                   PositionSink sink) {
        if (chunk == null) {
            return;
        }
        int chunkX = chunk.getInt("xPos");
        int chunkZ = chunk.getInt("zPos");
        ListTag sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        if (sections.isEmpty()) {
            return;
        }

        Long2IntOpenHashMap positions = new Long2IntOpenHashMap();

        for (int s = 0; s < sections.size(); s++) {
            CompoundTag section = sections.getCompound(s);
            if (!section.contains("block_states", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag blockStates = section.getCompound("block_states");
            ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
            if (palette.isEmpty()) {
                continue;
            }

            int[] paletteValue = new int[palette.size()];
            boolean anyMatch = false;
            for (int p = 0; p < palette.size(); p++) {
                ResourceLocation id = ResourceLocation.tryParse(palette.getCompound(p).getString("Name"));
                int value = id == null ? -1 : classifier.applyAsInt(id);
                paletteValue[p] = value;
                if (value >= 0) {
                    anyMatch = true;
                }
            }
            if (!anyMatch) {
                continue;
            }

            if (palette.size() == 1) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        positions.put(RadarRegistryData.pack(chunkX * 16 + x, chunkZ * 16 + z), paletteValue[0]);
                    }
                }
                continue;
            }

            long[] data = blockStates.getLongArray("data");
            if (data.length == 0) {
                continue;
            }
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int entriesPerLong = 64 / bits;
            long mask = (1L << bits) - 1;

            for (int idx = 0; idx < SECTION_VOLUME; idx++) {
                int longIndex = idx / entriesPerLong;
                if (longIndex >= data.length) {
                    break;
                }
                int offset = (idx % entriesPerLong) * bits;
                int palIdx = (int) ((data[longIndex] >>> offset) & mask);
                if (palIdx >= 0 && palIdx < paletteValue.length && paletteValue[palIdx] >= 0) {
                    int x = idx & 15;
                    int z = (idx >> 4) & 15;
                    positions.put(RadarRegistryData.pack(chunkX * 16 + x, chunkZ * 16 + z), paletteValue[palIdx]);
                }
            }
        }

        for (Long2IntMap.Entry entry : positions.long2IntEntrySet()) {
            sink.accept(entry.getLongKey(), entry.getIntValue());
        }
    }
}
