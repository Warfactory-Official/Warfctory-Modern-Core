package com.norwood.wfcore.common.gui;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import brachy.modularui.ModularUIMenuTypes;
import brachy.modularui.screen.ModularContainerMenu;
import brachy.modularui.widgets.slot.ModularSlot;
import brachy.modularui.widgets.slot.SlotGroup;

import com.norwood.wfcore.gui.CraftingStationUI;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lets JEI push a crafting recipe into the Crafting Station's 3x3 grid, sourcing from the store, the tool bay
 * and the player's inventory.
 *
 * <p>
 * Every ModularUI 3 machine shares one {@code ModularContainerMenu}, so unlike the LDLib version this cannot
 * identify the station — or its slot ranges — from the menu class or from hardcoded indices. It reads the
 * slot groups instead: slots carry the group they were registered with, which makes the lookup independent
 * of the order the panel happens to build its widgets in.
 */
final class CraftingStationTransferHandler implements IRecipeTransferInfo<ModularContainerMenu, RecipeHolder<CraftingRecipe>> {

    @Override
    public Class<? extends ModularContainerMenu> getContainerClass() {
        return ModularContainerMenu.class;
    }

    @Override
    public Optional<MenuType<ModularContainerMenu>> getMenuType() {
        return Optional.of(ModularUIMenuTypes.MODULAR_CONTAINER.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public boolean canHandle(ModularContainerMenu container, RecipeHolder<CraftingRecipe> recipe) {
        return !slotsInGroup(container, CraftingStationUI.GRID_GROUP).isEmpty();
    }

    @Override
    public List<Slot> getRecipeSlots(ModularContainerMenu container, RecipeHolder<CraftingRecipe> recipe) {
        return slotsInGroup(container, CraftingStationUI.GRID_GROUP);
    }

    @Override
    public List<Slot> getInventorySlots(ModularContainerMenu container, RecipeHolder<CraftingRecipe> recipe) {
        List<Slot> slots = new ArrayList<>();
        // The store is 512 slots and mostly empty; handing JEI every empty one would make it walk the whole
        // thing for each ingredient, so only the occupied ones are offered as sources.
        for (Slot slot : slotsInGroup(container, CraftingStationUI.STORE_GROUP)) {
            if (!slot.getItem().isEmpty()) {
                slots.add(slot);
            }
        }
        slots.addAll(slotsInGroup(container, CraftingStationUI.BAY_GROUP));
        for (Slot slot : container.slots) {
            if (ModularSlot.isPlayerSlot(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static List<Slot> slotsInGroup(ModularContainerMenu container, String groupName) {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : container.slots) {
            if (slot instanceof ModularSlot modularSlot) {
                SlotGroup group = modularSlot.getSlotGroup();
                if (group != null && groupName.equals(group.getName())) {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }
}
