package com.norwood.wfcore.radar.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * Per-level registry of radar-visible machines: {@link BlockPos#asLong} -&gt; richness value.
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

    public RadarRegistryData(CompoundTag tag, HolderLookup.Provider registries) {
        this();
        // "machines" (pre-Y packed x,z) ignored; /wfcore_retrofit rebuilds.
        ListTag list = tag.getList("targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            machineMap.put(entry.getLong("p"), entry.getInt("v"));
        }
    }

    public Long2IntOpenHashMap getMachineMap() {
        return machineMap;
    }

    public static RadarRegistryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(RadarRegistryData::new, RadarRegistryData::new), DATA_NAME);
    }

    public void addMachine(BlockPos pos, int value) {
        machineMap.put(pos.asLong(), value);
        setDirty();
    }

    public void removeMachine(BlockPos pos) {
        if (machineMap.containsKey(pos.asLong())) {
            machineMap.remove(pos.asLong());
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : machineMap.long2IntEntrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("p", entry.getLongKey());
            e.putInt("v", entry.getIntValue());
            list.add(e);
        }
        tag.put("targets", list);
        return tag;
    }
}
