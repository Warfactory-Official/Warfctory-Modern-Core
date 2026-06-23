package com.norwood.wfcore.radar;

import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.data.RadarRegistryData;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.io.DataInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Retroactively populates the radar registry by scanning a dimension's region files off-thread.
 *
 * <p>
 * Ported from the 1.12.2 version: targets are identified purely by their block registry name from
 * the chunk's block-state palette (no tile-entity reads needed in modern, since the palette already
 * carries the block id). Region files are parsed on virtual threads; results queue up and are drained
 * a few per server tick to avoid a spike.
 */
public class Retrofitter {

    public static final Retrofitter INSTANCE = new Retrofitter();
    private static final int ENTRIES_PER_TICK = 16;
    private static final int SECTION_VOLUME = 16 * 16 * 16;

    private volatile boolean active = false;
    private volatile ServerLevel targetLevel;

    public final Queue<Combined> queue = new ConcurrentLinkedQueue<>();

    public record Combined(long packed, int value) {}

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!active || event.phase != TickEvent.Phase.END || targetLevel == null) {
            return;
        }
        for (int i = 0; i < ENTRIES_PER_TICK; i++) {
            Combined entry = queue.poll();
            if (entry == null) {
                active = false;
                WFCore.LOGGER.info("Retrofitter scan finished; queue empty.");
                break;
            }
            RadarRegistryData.get(targetLevel)
                    .addMachine((int) (entry.packed >> 32), (int) entry.packed, entry.value);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_retrofit")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    startGlobalScan(level);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "Started radar retrofit scan for " + level.dimension().location()), true);
                    return 1;
                }));
    }

    public void startGlobalScan(ServerLevel level) {
        this.targetLevel = level;
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path regionDir = DimensionType.getStorageFolder(level.dimension(), worldRoot).resolve("region");
        File[] files = regionDir.toFile().listFiles((dir, name) -> name.endsWith(".mca"));
        if (files == null || files.length == 0) {
            WFCore.LOGGER.warn("Retrofitter: no region files found in {}", regionDir);
            return;
        }

        active = true;
        WFCore.LOGGER.info("Retrofitter: scanning {} region files...", files.length);

        // Run the (blocking) fan-out on its own daemon thread so the command returns immediately;
        // the server tick drains the queue as it fills.
        Thread worker = new Thread(() -> {
            int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            for (File mcaFile : files) {
                executor.submit(() -> scanRegionFile(mcaFile, regionDir));
            }
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            WFCore.LOGGER.info("Retrofitter: scan finished, {} positions queued.", queue.size());
        }, "wfcore-retrofit");
        worker.setDaemon(true);
        worker.start();
    }

    private void scanRegionFile(File mcaFile, Path regionDir) {
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
                            processChunkNBT(NbtIo.read(dis));
                        }
                    }
                }
            }
        } catch (Exception e) {
            WFCore.LOGGER.error("Retrofitter: failed to parse region {}", mcaFile.getName(), e);
        }
    }

    private void processChunkNBT(CompoundTag chunk) {
        if (chunk == null) {
            return;
        }
        int chunkX = chunk.getInt("xPos");
        int chunkZ = chunk.getInt("zPos");
        ListTag sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        if (sections.isEmpty()) {
            return;
        }

        // dedup column positions within this chunk; the radar registry is 2D (x, z)
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
            boolean anyWhitelisted = false;
            for (int p = 0; p < palette.size(); p++) {
                ResourceLocation id = ResourceLocation.tryParse(palette.getCompound(p).getString("Name"));
                if (id != null && RadarConfig.isWhitelisted(id)) {
                    paletteValue[p] = RadarConfig.getValue(id);
                    anyWhitelisted = true;
                } else {
                    paletteValue[p] = -1;
                }
            }
            if (!anyWhitelisted) {
                continue;
            }

            // single-entry palette: the whole section is that block, no data array present
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

        for (var entry : positions.long2IntEntrySet()) {
            queue.add(new Combined(entry.getLongKey(), entry.getIntValue()));
        }
    }
}
