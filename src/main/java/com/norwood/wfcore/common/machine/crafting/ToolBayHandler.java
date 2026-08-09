package com.norwood.wfcore.common.machine.crafting;

import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;


public class ToolBayHandler extends CustomItemStackHandler {

    public ToolBayHandler(int size) {
        super(size);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        return !ToolHelper.getCraftingToolTypes(stack).isEmpty();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < getSlots()) {
                setStackInSlot(slot, ItemStack.of(entry));
            }
        }
    }
}
