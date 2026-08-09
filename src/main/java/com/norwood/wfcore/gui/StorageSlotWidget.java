package com.norwood.wfcore.gui;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.Nullable;


public class StorageSlotWidget extends SlotWidget {

    @Nullable
    private final IItemHandlerModifiable toolBay;

    public StorageSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int x, int y) {
        this(itemHandler, slotIndex, x, y, null);
    }

    public StorageSlotWidget(IItemHandlerModifiable itemHandler, int slotIndex, int x, int y,
                             @Nullable IItemHandlerModifiable toolBay) {
        super(itemHandler, slotIndex, x, y, true, true);
        this.toolBay = toolBay;
    }

    @Override
    public ItemStack slotClick(int dragType, ClickType clickType, Player player) {
        // Shift-click: a tool defaults into the tool bay; everything else (and any bay overflow) goes to the player.
        if (clickType != ClickType.QUICK_MOVE) {
            return null;
        }
        if (player.level().isClientSide) {
            return getItem();
        }
        ItemStack inSlot = getItem();
        if (inSlot.isEmpty()) {
            return getItem();
        }
        ItemStack moving = inSlot.copy();
        if (toolBay != null && CraftingStationUI.isTool(moving)) {
            moving = ItemHandlerHelper.insertItemStacked(toolBay, moving, false);
        }
        if (!moving.isEmpty()) {
            player.getInventory().add(moving);   // mutates 'moving' down to whatever would not fit
        }
        setItem(moving.isEmpty() ? ItemStack.EMPTY : moving);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
        }
        return getItem();
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
