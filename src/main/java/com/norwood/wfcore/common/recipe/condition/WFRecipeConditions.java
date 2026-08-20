package com.norwood.wfcore.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.mojang.serialization.MapCodec;
import com.norwood.wfcore.WFCore;

/**
 * Registers WFCore's recipe conditions into GregTech's shared condition registry. Call {@link #init()} once
 * from the mod's {@code RegisterEvent} listener, before any recipes are parsed or synced.
 *
 * <p>
 * This used to borrow GregTech's mod container and unfreeze/refreeze {@code GTRegistries.RECIPE_CONDITIONS}
 * by hand, because in 7.5.3 the registry rejected writes from another mod's container. In 8.0.0 it is an
 * ordinary vanilla {@link net.minecraft.core.Registry} and {@code GTRegistries.register} buffers entries
 * until the registry is unfrozen, so the whole workaround is gone — this now mirrors
 * {@code GTRecipeConditions} exactly.
 */
public final class WFRecipeConditions {

    public static final String RESEARCH_NAME = "research_wfcore";
    public static final String DEPOSIT_NAME = "deposit_wfcore";

    public static RecipeConditionType<ResearchRecipeCondition> RESEARCH;
    public static RecipeConditionType<DepositRecipeCondition> DEPOSIT;

    private static boolean initialized;

    private WFRecipeConditions() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        RESEARCH = register(RESEARCH_NAME, ResearchRecipeCondition::new, ResearchRecipeCondition.CODEC);
        DEPOSIT = register(DEPOSIT_NAME, DepositRecipeCondition::new, DepositRecipeCondition.CODEC);
    }

    private static <T extends RecipeCondition<T>> RecipeConditionType<T> register(
            String name, RecipeConditionType.ConditionFactory<T> factory, MapCodec<T> codec) {
        return GTRegistries.register(GTRegistries.RECIPE_CONDITIONS, WFCore.id(name),
                new RecipeConditionType<>(factory, codec));
    }
}
