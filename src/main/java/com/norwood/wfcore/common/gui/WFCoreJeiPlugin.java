package com.norwood.wfcore.common.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * Minimal JEI plugin whose only job is to capture the {@link IJeiRuntime} so the Research Unit GUI can open the
 * recipe page for an unlocked item when the player clicks it. JEI is a client mod loaded only when present, so
 * this class (and every JEI API type it names) is resolved solely on a client that actually has JEI installed -
 * {@link RecipeViewerBridge} gates the call behind a {@code ModList} check, so nothing here is ever touched
 * otherwise.
 */
@JeiPlugin
public final class WFCoreJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("wfcore", "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Opens JEI's recipe page for the recipes that produce {@code stack}; no-op if the runtime isn't up yet. */
    static void showRecipes(ItemStack stack) {
        if (runtime == null || stack.isEmpty()) return;
        IFocus<ItemStack> focus = runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        runtime.getRecipesGui().show(focus);
    }
}
