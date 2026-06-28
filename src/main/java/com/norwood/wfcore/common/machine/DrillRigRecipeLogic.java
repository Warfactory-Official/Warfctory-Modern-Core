package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

/**
 * Recipe logic for the drilling rig. Each completed cycle drains one yield from the deposit cluster beneath the
 * drill (outer blocks first); the recipe's item output is handled by the stock {@link RecipeLogic} pipeline.
 */
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
}
