package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.network.chat.Component;

/**
 * Recipe logic for the {@link PrimitiveAlloyerMachine}. The machine is fuel-driven rather than powered, so
 * before letting the recipe advance each tick we require the machine to be fuelled (lava drained per-tick, or
 * a burning solid fuel). If it isn't, the recipe pauses (WAITING) without losing its output — exactly the
 * "process pauses until fuel is available again" behaviour. Fuel is only actually spent on ticks where the
 * recipe genuinely progressed, so a full output buffer or missing ingredients never wastes fuel.
 */
public class PrimitiveAlloyerRecipeLogic extends RecipeLogic {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            PrimitiveAlloyerRecipeLogic.class, RecipeLogic.MANAGED_FIELD_HOLDER);

    public PrimitiveAlloyerRecipeLogic(PrimitiveAlloyerMachine machine) {
        super(machine);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private PrimitiveAlloyerMachine alloyer() {
        return (PrimitiveAlloyerMachine) machine;
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
