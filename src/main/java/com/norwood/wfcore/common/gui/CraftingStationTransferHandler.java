package com.norwood.wfcore.common.gui;

import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;

import com.norwood.wfcore.common.machine.crafting.CraftingStationMachine;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


final class CraftingStationTransferHandler implements IRecipeTransferInfo<ModularUIContainer, CraftingRecipe> {

    @Override
    public Class<? extends ModularUIContainer> getContainerClass() {
        return ModularUIContainer.class;
    }

    @Override
    public Optional<MenuType<ModularUIContainer>> getMenuType() {
        return Optional.of(ModularUIContainer.MENUTYPE);
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public boolean canHandle(ModularUIContainer container, CraftingRecipe recipe) {
        return container.getModularUI() != null
                && container.getModularUI().holder instanceof CraftingStationMachine;
    }

    @Override
    public List<Slot> getRecipeSlots(ModularUIContainer container, CraftingRecipe recipe) {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < CraftingStationMachine.GRID_SIZE; i++) {
            slots.add(container.getSlot(CraftingStationMachine.GRID_START + i));
        }
        return slots;
    }

    @Override
    public List<Slot> getInventorySlots(ModularUIContainer container, CraftingRecipe recipe) {

        List<Slot> slots = new ArrayList<>();

        for (int i = CraftingStationMachine.STORAGE_START; i < CraftingStationMachine.PLAYER_START; i++) {
            Slot slot = container.getSlot(i);
            if (!slot.getItem().isEmpty()) {
                slots.add(slot);
            }
        }

        for (int i = CraftingStationMachine.PLAYER_START; i < container.slots.size(); i++) {
            slots.add(container.getSlot(i));
        }
        return slots;
    }
}
