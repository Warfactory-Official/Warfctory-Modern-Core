package com.norwood.wfcore.api.research;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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

    /** NBT key carrying the research id list on the data item. */
    public static final String KEY = "wfcore_research";

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

    /** Every research id written on the item (empty if none / not a data item). */
    public static List<String> readAll(ItemStack stack) {
        if (!isDataItem(stack) || !stack.hasTag()) return List.of();
        Tag entry = stack.getTag().get(KEY);
        if (entry instanceof ListTag list) {
            List<String> ids = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                String id = list.getString(i);
                if (!id.isEmpty()) ids.add(id);
            }
            return ids;
        }
        if (entry instanceof StringTag) {
            String id = stack.getTag().getString(KEY);
            return id.isEmpty() ? List.of() : List.of(id);
        }
        return List.of();
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
     * Appends {@code researchId} to the item's blueprint list as NBT. No-op success if it is already present;
     * returns false if the item is full or not a data item.
     */
    public static boolean write(ItemStack stack, String researchId) {
        if (!isDataItem(stack) || researchId == null || researchId.isEmpty()) return false;
        List<String> ids = new ArrayList<>(readAll(stack));
        if (ids.contains(researchId)) return true;
        if (ids.size() >= capacity(stack)) return false;
        ids.add(researchId);
        ListTag list = new ListTag();
        for (String id : ids) list.add(StringTag.valueOf(id));
        CompoundTag tag = stack.getOrCreateTag();
        tag.put(KEY, list);
        if(stack.getItem() == Items.PAPER) {
            var name =ResearchRegistry.get(researchId).getNameKey();
            String json = Component.Serializer.toJson(
                    Component.literal(String.format("Blueprint: %s ",name))
            );
            var displayTag = stack.getOrCreateTagElement(ItemStack.TAG_DISPLAY);
            displayTag.putString(ItemStack.TAG_DISPLAY_NAME, json);
        }
        return true;
    }
}
