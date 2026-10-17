package com.norwood.wfcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

public final class WFRecipeTypeTweaks {

    private WFRecipeTypeTweaks() {}

    public static void init() {
        widenItemInputs(GTRecipeTypes.CUTTER_RECIPES.get(), 2);
    }

    private static void widenItemInputs(GTRecipeType type, int slots) {
        if (type.getMaxInputs(ItemRecipeCapability.CAP) >= slots) {
            return;
        }
        // No post-registration setter; sizes are read per machine instance / per UI build.
        type.maxInputs.put(ItemRecipeCapability.CAP, slots);
    }
}
