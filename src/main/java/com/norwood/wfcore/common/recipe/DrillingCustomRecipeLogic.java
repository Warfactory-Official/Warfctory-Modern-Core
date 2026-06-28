package com.norwood.wfcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.norwood.wfcore.common.data.WFRecipeTypes;
import com.norwood.wfcore.common.machine.DrillRigMachine;
import com.norwood.wfcore.common.recipe.condition.DepositRecipeCondition;

import java.util.List;

/**
 * Feeds the drilling rig its <em>base</em> (input-less) recipe for the deposit beneath the drill head.
 * <p>
 * GregTech's indexed lookup only finds recipes with item/fluid inputs, so:
 * <ul>
 * <li>a base recipe (no inputs, just a {@link DepositRecipeCondition}) is invisible to it and is supplied here;</li>
 * <li>a fluid-boosted recipe (drilling fluid in → faster / extra outputs) IS indexed, so the normal lookup runs it
 * automatically whenever the fluid is present, taking precedence over the base;</li>
 * <li>a deposit with only a fluid recipe and no base simply idles until the fluid is supplied (gating).</li>
 * </ul>
 * Hence this logic returns only an input-less recipe; anything needing a fluid is left to the normal pipeline.
 */
public class DrillingCustomRecipeLogic implements GTRecipeType.ICustomRecipeLogic {

    @Override
    public GTRecipe createCustomRecipe(IRecipeCapabilityHolder holder) {
        if (!(holder instanceof DrillRigMachine drill)) {
            return null;
        }
        ResourceLocation active = drill.getActiveDepositTypeId();
        Level level = drill.getLevel();
        if (active == null || level == null) {
            return null;
        }
        for (GTRecipe recipe : level.getRecipeManager().getAllRecipesFor(WFRecipeTypes.DRILLING)) {
            if (hasInputs(recipe)) {
                continue;
            }
            for (RecipeCondition<?> condition : recipe.conditions) {
                if (condition instanceof DepositRecipeCondition deposit && deposit.matches(active)) {
                    return recipe;
                }
            }
        }
        return null;
    }

    /** True if the recipe needs an item or fluid input (handled by the normal indexed lookup, not here). */
    private static boolean hasInputs(GTRecipe recipe) {
        return !recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, List.of()).isEmpty() ||
                !recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, List.of()).isEmpty();
    }
}
