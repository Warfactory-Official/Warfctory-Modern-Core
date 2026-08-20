package com.norwood.wfcore.common.machine.crafting;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * The Crafting Station's store: 512 slots that each hold up to 512 items.
 *
 * <p>
 * The bespoke NBT format is not legacy baggage — {@code ItemStack.CODEC} caps {@code count} at
 * {@code ExtraCodecs.intRange(1, 99)}, so the inherited {@code serializeNBT} would silently truncate every
 * over-stacked slot on the next save. Each entry therefore stores a single-item stack plus the real count
 * alongside it, and the count is re-applied on load.
 */
public class BigStackItemHandler extends CustomItemStackHandler {

    public static final int SLOTS = 512;
    public static final int LIMIT = 512;

    public BigStackItemHandler(int size) {
        super(size);
    }

    @Override
    public int getSlotLimit(int slot) {
        return LIMIT;
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return getSlotLimit(slot);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        ListTag items = new ListTag();
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.putInt("RealCount", stack.getCount());
            entry.put("Item", stack.copyWithCount(1).save(provider));
            items.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items);
        tag.putInt("Size", getSlots());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int j = 0; j < items.size(); j++) {
            CompoundTag entry = items.getCompound(j);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= getSlots()) continue;
            ItemStack stack = ItemStack.parseOptional(provider, entry.getCompound("Item"));
            if (stack.isEmpty()) continue;
            stack.setCount(Math.min(getSlotLimit(slot), entry.getInt("RealCount")));
            stacks.set(slot, stack);
        }
    }
}
