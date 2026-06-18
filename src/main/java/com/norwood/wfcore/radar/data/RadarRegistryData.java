package com.norwood.wfcore.radar.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * Per-level registry of radar-visible machines: packed (x, z) -&gt; richness value.
 *
 * <p>
 * Machines self-register here when their block entity is added to the level (see the
 * registry hook), keyed by registry name through the radar config.
 */
public class RadarRegistryData extends SavedData {

    private static final String DATA_NAME = "wfcore_radar_registry";

    private final Long2IntOpenHashMap machineMap;

    public RadarRegistryData() {
        this.machineMap = new Long2IntOpenHashMap();
    }

    public RadarRegistryData(CompoundTag tag) {
        this();
        ListTag list = tag.getList("machines", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            machineMap.put(entry.getLong("p"), entry.getInt("v"));
        }
    }

    public static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public Long2IntOpenHashMap getMachineMap() {
        return machineMap;
    }

    public static RadarRegistryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RadarRegistryData::new, RadarRegistryData::new, DATA_NAME);
    }

    public void addMachine(int x, int z, int value) {
        machineMap.put(pack(x, z), value);
        setDirty();
    }

    public void removeMachine(int x, int z) {
        if (machineMap.remove(pack(x, z)) != 0) {
            setDirty();
        }
    }

    public boolean hasMachine(int x, int z) {
        return machineMap.containsKey(pack(x, z));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var entry : machineMap.long2IntEntrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("p", entry.getLongKey());
            e.putInt("v", entry.getIntValue());
            list.add(e);
        }
        tag.put("machines", list);
        return tag;
    }
}
