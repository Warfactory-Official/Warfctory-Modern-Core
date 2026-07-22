package com.norwood.wfcore.radar;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;


public final class RadarDataStick {

    /** NBT key holding the scan UUID that indexes into {@link com.norwood.wfcore.radar.data.RadarScanData}. */
    public static final String KEY_TARGET_UUID = "TargetUUID";
    /** NBT flag marking the stick as carrying a finished, printer-ready scan. */
    public static final String KEY_ANALYZED = "is_analyzed";

    private RadarDataStick() {}


    public static void writeScan(ItemStack stick, UUID scanId) {
        CompoundTag tag = stick.getOrCreateTag();
        tag.putUUID(KEY_TARGET_UUID, scanId);
        tag.putBoolean(KEY_ANALYZED, true);

        CompoundTag display = tag.getCompound("display");
        display.putString("Name", Component.Serializer.toJson(Component.literal("§bRecorded Radar Data")));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal("§7Contains structure density coordinates."))));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(
                Component.literal("§5Ready for Printer analysis."))));
        display.put("Lore", lore);
        tag.put("display", display);
    }

    /** A fresh data stick already carrying {@code scanId}, ready to drop into a printer. */
    public static ItemStack createDataStick(UUID scanId) {
        ItemStack stick = GTItems.TOOL_DATA_STICK.asStack();
        writeScan(stick, scanId);
        return stick;
    }
}
