package com.norwood.wfcore.gui;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;


public class StorageSlotWidget extends SlotWidget {

    public StorageSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int x, int y) {
        super(itemHandler, slotIndex, x, y, true, true);
    }

    @Override
    protected Slot createSlot(IItemHandlerModifiable itemHandler, int index) {
        final IItemHandlerModifiable handler = itemHandler;
        final int slot = index;
        return new WidgetSlotItemHandler(itemHandler, index, 0, 0) {
            @Override
            public int getMaxStackSize(ItemStack stack) {
                return handler.getSlotLimit(slot);
            }

            @Override
            public boolean mayPickup(@Nullable Player playerIn) {

                return !handler.extractItem(slot, 1, true).isEmpty();
            }
        };
    }
}
