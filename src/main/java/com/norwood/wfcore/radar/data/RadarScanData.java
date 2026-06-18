package com.norwood.wfcore.radar.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import com.norwood.wfcore.radar.math.ClusterData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global store of completed radar scans, keyed by UUID. Attached to the overworld so the data is
 * shared regardless of which dimension the radar (or printer) sits in. The data stick written by a
 * radar carries the scan UUID; the printer reads it back here.
 */
public class RadarScanData extends SavedData {

    private static final String DATA_NAME = "wfcore_radar_scans";

    private final Map<UUID, ScanRecord> database = new HashMap<>();

    private record ScanRecord(List<ClusterData> clusters, long lastAccessed) {}

    public RadarScanData() {}

    public RadarScanData(CompoundTag tag) {
        ListTag entries = tag.getList("Database", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = new UUID(entry.getLong("UUIDMost"), entry.getLong("UUIDLeast"));
            long lastAccessed = entry.getLong("LastAccessed");

            ListTag clustersTag = entry.getList("Clusters", Tag.TAG_COMPOUND);
            List<ClusterData> clusters = new ArrayList<>(clustersTag.size());
            for (int j = 0; j < clustersTag.size(); j++) {
                clusters.add(ClusterData.fromNBT(clustersTag.getCompound(j)));
            }
            database.put(id, new ScanRecord(clusters, lastAccessed));
        }
    }

    public static RadarScanData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(RadarScanData::new, RadarScanData::new, DATA_NAME);
    }

    public void addScan(UUID id, List<ClusterData> clusters) {
        database.put(id, new ScanRecord(clusters, System.currentTimeMillis()));
        setDirty();
    }

    public void removeScan(UUID id) {
        if (database.remove(id) != null) {
            setDirty();
        }
    }

    public List<ClusterData> getScan(UUID id) {
        ScanRecord record = database.get(id);
        if (record == null) {
            return null;
        }
        database.put(id, new ScanRecord(record.clusters(), System.currentTimeMillis()));
        setDirty();
        return record.clusters();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (var entry : database.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("UUIDMost", entry.getKey().getMostSignificantBits());
            e.putLong("UUIDLeast", entry.getKey().getLeastSignificantBits());
            e.putLong("LastAccessed", entry.getValue().lastAccessed());

            ListTag clustersTag = new ListTag();
            for (ClusterData cluster : entry.getValue().clusters()) {
                clustersTag.add(cluster.toNBT());
            }
            e.put("Clusters", clustersTag);
            entries.add(e);
        }
        tag.put("Database", entries);
        return tag;
    }
}
