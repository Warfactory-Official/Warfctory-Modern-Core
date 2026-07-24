package com.norwood.wfcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.norwood.wfcore.common.machine.DrillRigMachine;


public class DrillingCustomRecipeLogic implements GTRecipeType.ICustomRecipeLogic {

    @Override
    public GTRecipe createCustomRecipe(IRecipeCapabilityHolder holder) {
        return holder instanceof DrillRigMachine drill ? drill.findBaseDrillingRecipe() : null;
    }
}
