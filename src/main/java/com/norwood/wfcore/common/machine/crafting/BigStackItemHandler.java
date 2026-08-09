package com.norwood.wfcore.common.machine.crafting;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;


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

    public CompoundTag saveBig() {
        ListTag items = new ListTag();
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("Slot", i);
            entry.putInt("RealCount", stack.getCount());
            ItemStack one = stack.copy();
            one.setCount(1);
            entry.put("Item", one.save(new CompoundTag()));
            items.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items);
        tag.putInt("Size", getSlots());
        return tag;
    }

    public void loadBig(CompoundTag tag) {
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int j = 0; j < items.size(); j++) {
            CompoundTag entry = items.getCompound(j);
            int slot = entry.getInt("Slot");
            if (slot < 0 || slot >= getSlots()) continue;
            ItemStack stack = ItemStack.of(entry.getCompound("Item"));
            if (stack.isEmpty()) continue;
            stack.setCount(Math.min(getSlotLimit(slot), entry.getInt("RealCount")));
            stacks.set(slot, stack);
        }
    }
}
