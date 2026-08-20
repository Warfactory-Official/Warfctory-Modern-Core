package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.Iterator;


/**
 * GT 8.0.0 turned machine traits into objects attached to a machine rather than constructed against one,
 * so this no longer takes the machine in its constructor — {@link #getMachine()} supplies it once
 * {@code attachTrait} has run.
 */
public class DrillRigRecipeLogic extends RecipeLogic {

    @Override
    public void onRecipeFinish() {
        super.onRecipeFinish();
        if (getMachine() instanceof DrillRigMachine drill) {
            drill.onDrillCycleFinished();
        }
    }


    @Override
    public Iterator<GTRecipe> searchRecipe() {
      Iterator<GTRecipe> stock = super.searchRecipe();
        if (stock.hasNext()) {
            return stock;
        }
        if (getMachine() instanceof DrillRigMachine drill) {
            var base = drill.findBaseDrillingRecipe();
            if (base != null) {
                return java.util.List.of(base).iterator();
            }
        }
        return stock;
    }
}
