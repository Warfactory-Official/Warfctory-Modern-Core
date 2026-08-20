package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import net.minecraft.network.chat.Component;

/**
 * Recipe logic for the {@link PrimitiveAlloyerMachine}. The machine is fuel-driven rather than powered, so
 * before letting the recipe advance each tick we require the machine to be fuelled (lava drained per-tick, or
 * a burning solid fuel). If it isn't, the recipe pauses (WAITING) without losing its output — exactly the
 * "process pauses until fuel is available again" behaviour. Fuel is only actually spent on ticks where the
 * recipe genuinely progressed, so a full output buffer or missing ingredients never wastes fuel.
 *
 * <p>GT 8.0.0 turned machine traits into objects that are attached to a machine rather than constructed
 * against one, so this no longer takes the machine in its constructor — {@link #getMachine()} supplies it
 * once {@code attachTrait} has run. The LDLib {@code ManagedFieldHolder} is gone with the sync rewrite.
 */
public class PrimitiveAlloyerRecipeLogic extends RecipeLogic {

    private PrimitiveAlloyerMachine alloyer() {
        return (PrimitiveAlloyerMachine) getMachine();
    }

    @Override
    public void handleRecipeWorking() {
        if (!alloyer().hasFuelAvailable()) {
            setWaiting(Component.translatable("wfcore.machine.primitive_alloyer.no_fuel"));
            regressRecipe();
            return;
        }
        int before = progress;
        super.handleRecipeWorking();
        if (progress > before) {
            alloyer().consumeFuelTick();
        }
    }
}
