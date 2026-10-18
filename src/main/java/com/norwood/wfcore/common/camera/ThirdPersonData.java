package com.norwood.wfcore.common.camera;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import com.norwood.wfcore.config.WFCoreConfig;

import javax.annotation.Nullable;

/**
 * Whether players on this world may use third person (F5). Persists with the world. Until an op has set it, the
 * config's {@code defaultThirdPersonAllowed} decides, so a pack can ship either way without touching world data.
 */
public class ThirdPersonData extends SavedData {

    private static final String DATA_NAME = "wfcore_third_person";
    private static final String KEY = "Allowed";

    /** Null until an op sets it. */
    @Nullable
    private Boolean allowed;

    public ThirdPersonData() {}

    public ThirdPersonData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(KEY)) {
            allowed = tag.getBoolean(KEY);
        }
    }

    public static ThirdPersonData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ThirdPersonData::new, ThirdPersonData::new), DATA_NAME);
    }

    public boolean isAllowed() {
        return allowed != null ? allowed : WFCoreConfig.isThirdPersonDefaultAllowed();
    }

    public void setAllowed(boolean value) {
        allowed = value;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (allowed != null) {
            tag.putBoolean(KEY, allowed);
        }
        return tag;
    }
}
