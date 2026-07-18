package com.norwood.wfcore.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Adapts a vehicle's {@link IItemHandlerModifiable} inventory to the vanilla {@link Container} interface so the
 * WFCore ModularUI's {@link com.lowdragmc.lowdraglib.gui.widget.SlotWidget}s (which bind to a {@code Container})
 * can read and write it.
 *
 * <p>
 * Superb Warfare 0.8.9 stopped making {@code VehicleEntity} a {@code Container} and moved its storage to an
 * item-handler capability ({@code VehicleContainerHandler}); this bridges the two without forcing the vehicle to
 * re-implement {@code Container}. All state lives in the underlying handler, so this wrapper is stateless and can
 * be built fresh each time the UI opens on either side.
 */
public final class VehicleInventoryContainer implements Container {

    private final IItemHandlerModifiable handler;

    public VehicleInventoryContainer(IItemHandlerModifiable handler) {
        this.handler = handler;
    }

    @Override
    public int getContainerSize() {
        return handler.getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < handler.getSlots() ? handler.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= handler.getSlots() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = handler.getStackInSlot(slot).copy();
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack split = existing.split(amount);
        handler.setStackInSlot(slot, existing);
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= handler.getSlots()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = handler.getStackInSlot(slot);
        handler.setStackInSlot(slot, ItemStack.EMPTY);
        return existing;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < handler.getSlots()) {
            handler.setStackInSlot(slot, stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return handler.getSlots() > 0 ? handler.getSlotLimit(0) : 64;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < handler.getSlots() && handler.isItemValid(slot, stack);
    }

    @Override
    public void setChanged() {
        // The underlying handler notifies the vehicle from setStackInSlot#onContentsChanged; nothing to do here.
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < handler.getSlots(); i++) {
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }
    }
}
