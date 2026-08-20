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
        // 8.0.0 builds the recipe UI from the type's .UI(...) hook at registration, so widening the
        // input count is all there is to do — there is no cached UI left to invalidate.
        type.setMaxSize(IO.IN, ItemRecipeCapability.CAP, slots);
    }
}
