package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.Iterator;


public class DrillRigRecipeLogic extends RecipeLogic {

    public DrillRigRecipeLogic(DrillRigMachine machine) {
        super(machine);
    }

    @Override
    public void onRecipeFinish() {
        super.onRecipeFinish();
        if (machine.self() instanceof DrillRigMachine drill) {
            drill.onDrillCycleFinished();
        }
    }


    @Override
    public Iterator<GTRecipe> searchRecipe() {
      Iterator<GTRecipe> stock = super.searchRecipe();
        if (stock.hasNext()) {
            return stock;
        }
        if (machine.self() instanceof DrillRigMachine drill) {
            var base = drill.findBaseDrillingRecipe();
            if (base != null) {
                return java.util.List.of(base).iterator();
            }
        }
        return stock;
    }
}
