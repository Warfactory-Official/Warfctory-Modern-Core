package com.norwood.wfcore.common.gui;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * A tiny, viewer-agnostic entry point for "show me the recipes that produce this item", used when the player
 * clicks an unlocked item in the Research Unit's detail panel.
 * <p>
 * This class references no recipe-viewer types itself; it only delegates to {@link WFCoreJeiPlugin} once it has
 * confirmed JEI is present. That keeps every JEI API class off this (and the GUI's) call graph unless JEI is
 * actually installed, so the code no-ops cleanly on a pack that ships a different viewer (or none). EMI/REI
 * branches can be slotted in here the same way if those are ever added to the dependency set.
 */
final class RecipeViewerBridge {

    private RecipeViewerBridge() {}

    /** Opens the recipe-viewer page showing how to obtain {@code stack}; a no-op if no supported viewer is loaded. */
    static void showRecipes(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (ModList.get().isLoaded("jei")) {
            WFCoreJeiPlugin.showRecipes(stack);
        }
    }
}
