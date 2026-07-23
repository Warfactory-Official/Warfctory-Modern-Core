package com.norwood.wfcore.common.ballistics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.Map;
import java.util.UUID;

public class BallisticsSavedData extends SavedData {

    private static final String DATA_NAME = "wfcore_ballistics_inflight";

    private final Map<UUID, VirtualProjectile> inFlight = new Object2ObjectLinkedOpenHashMap<>();

    public BallisticsSavedData() {}

    public BallisticsSavedData(CompoundTag tag) {
        this();
        ListTag list = tag.getList("inflight", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            VirtualProjectile v = VirtualProjectile.load(list.getCompound(i));
            inFlight.put(v.id, v);
        }
    }

    public static BallisticsSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BallisticsSavedData::new, BallisticsSavedData::new, DATA_NAME);
    }

    public Map<UUID, VirtualProjectile> getInFlight() {
        return inFlight;
    }

    public void add(VirtualProjectile v) {
        inFlight.put(v.id, v);
        setDirty();
    }

    public void remove(UUID id) {
        if (inFlight.remove(id) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (VirtualProjectile v : inFlight.values()) {
            list.add(v.save());
        }
        tag.put("inflight", list);
        return tag;
    }
}
