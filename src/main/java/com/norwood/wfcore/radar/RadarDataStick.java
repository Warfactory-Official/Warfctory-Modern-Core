package com.norwood.wfcore.radar;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import com.norwood.wfcore.common.data.WFDataComponents;

import java.util.List;
import java.util.UUID;

/**
 * The radar data stick: a GregTech data stick carrying one recorded scan.
 *
 * <p>1.20.5 moved item data out of free-form NBT, so the scan id, display name and lore are all data
 * components now. The old {@code is_analyzed} boolean is gone with the NBT: carrying a scan id
 * <em>is</em> being analyzed, so a separate flag could only ever disagree with itself.
 */
public final class RadarDataStick {

    private RadarDataStick() {}

    public static void writeScan(ItemStack stick, UUID scanId) {
        stick.set(WFDataComponents.RADAR_SCAN_ID.get(), scanId);
        stick.set(DataComponents.CUSTOM_NAME,
                Component.literal("Recorded Radar Data").withStyle(ChatFormatting.AQUA));
        stick.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Contains structure density coordinates.").withStyle(ChatFormatting.GRAY),
                Component.literal("Ready for Printer analysis.").withStyle(ChatFormatting.DARK_PURPLE))));
    }

    /** The scan this stick carries, or {@code null} if it is blank. */
    public static UUID readScan(ItemStack stick) {
        return stick.get(WFDataComponents.RADAR_SCAN_ID.get());
    }

    public static boolean isAnalyzed(ItemStack stick) {
        return readScan(stick) != null;
    }

    public static void wipeScan(ItemStack stick) {
        stick.remove(WFDataComponents.RADAR_SCAN_ID.get());
        stick.remove(DataComponents.CUSTOM_NAME);
        stick.remove(DataComponents.LORE);
    }

    /** A fresh data stick already carrying {@code scanId}, ready to drop into a printer. */
    public static ItemStack createDataStick(UUID scanId) {
        ItemStack stick = GTItems.TOOL_DATA_STICK.asStack();
        writeScan(stick, scanId);
        return stick;
    }
}
