package com.norwood.wfcore.api.research;

import com.gregtechceu.gtceu.common.data.GTItems;

import com.norwood.wfcore.common.data.WFDataComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes WFCore research ids onto a data item (GregTech data stick / orb / module, or plain paper) -
 * the research unit's "library" blueprint. A crafting machine treats holding such an item as proof that a
 * recipe's research is unlocked. Higher-tier data items hold several blueprints at once ({@link #capacity});
 * writing appends the next id rather than overwriting.
 */
public final class ResearchDataItem {

    private ResearchDataItem() {}

    public static boolean isDataItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == GTItems.TOOL_DATA_STICK.asItem() || item == GTItems.TOOL_DATA_ORB.asItem() ||
                item == GTItems.TOOL_DATA_MODULE.asItem() || item == Items.PAPER;
    }

    /** How many distinct blueprints this data item can hold. */
    public static int capacity(ItemStack stack) {
        Item item = stack.getItem();
        if (item == GTItems.TOOL_DATA_MODULE.asItem()) return 16;
        if (item == GTItems.TOOL_DATA_ORB.asItem()) return 4;
        return 1; // data stick, paper
    }

    /**
     * Every research id written on the item (empty if none / not a data item).
     *
     * <p>The 1.20.1 version read a raw NBT key that could be either a single string or a list; the
     * {@link WFDataComponents#RESEARCH_BLUEPRINTS} component is always a list, so the string branch is gone.
     */
    public static List<String> readAll(ItemStack stack) {
        if (!isDataItem(stack)) return List.of();
        List<String> ids = stack.get(WFDataComponents.RESEARCH_BLUEPRINTS.get());
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id != null && !id.isEmpty()) out.add(id);
        }
        return out;
    }

    /** The first research id on the item, or {@code null} if none. */
    public static String read(ItemStack stack) {
        List<String> ids = readAll(stack);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public static boolean matches(ItemStack stack, String researchId) {
        return researchId != null && readAll(stack).contains(researchId);
    }

    /** A data item with nothing written yet. */
    public static boolean isBlank(ItemStack stack) {
        return isDataItem(stack) && readAll(stack).isEmpty();
    }

    public static boolean isFull(ItemStack stack) {
        return isDataItem(stack) && readAll(stack).size() >= capacity(stack);
    }

    /**
     * Appends {@code researchId} to the item's blueprint list. No-op success if it is already present;
     * returns false if the item is full or not a data item.
     */
    public static boolean write(ItemStack stack, String researchId) {
        if (!isDataItem(stack) || researchId == null || researchId.isEmpty()) return false;
        List<String> ids = new ArrayList<>(readAll(stack));
        if (ids.contains(researchId)) return true;
        if (ids.size() >= capacity(stack)) return false;
        ids.add(researchId);
        stack.set(WFDataComponents.RESEARCH_BLUEPRINTS.get(), List.copyOf(ids));
        if (stack.getItem() == Items.PAPER) {
            // The display-name NBT subtag is now the CUSTOM_NAME component and takes a Component directly,
            // so there is no JSON round-trip any more.
            var name = ResearchRegistry.get(researchId).getNameKey();
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(String.format("Blueprint: %s ", name)));
        }
        return true;
    }
}
