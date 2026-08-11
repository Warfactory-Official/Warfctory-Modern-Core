package com.norwood.wfcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

public final class WFRecipeTypeTweaks {

    private WFRecipeTypeTweaks() {}

    public static void init() {
        widenItemInputs(GTRecipeTypes.CUTTER_RECIPES, 2);
    }

    private static void widenItemInputs(GTRecipeType type, int slots) {
        if (type.getMaxInputs(ItemRecipeCapability.CAP) >= slots) {
            return;
        }
        type.setMaxSize(IO.IN, ItemRecipeCapability.CAP, slots);
        type.getRecipeUI().reloadCustomUI();
    }
}
