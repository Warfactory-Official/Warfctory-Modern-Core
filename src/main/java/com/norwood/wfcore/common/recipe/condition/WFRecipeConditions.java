package com.norwood.wfcore.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.tterrag.registrate.util.entry.RegistryEntry;

import com.mojang.serialization.MapCodec;
import com.norwood.wfcore.WFCore;

/** Registered through {@code WFCore.WF_MACHINES}; call {@link #init()} at mod construction. */
public final class WFRecipeConditions {

    public static final String RESEARCH_NAME = "research_wfcore";
    public static final String DEPOSIT_NAME = "deposit_wfcore";

    public static RegistryEntry<RecipeConditionType<?>, RecipeConditionType<ResearchRecipeCondition>> RESEARCH;
    public static RegistryEntry<RecipeConditionType<?>, RecipeConditionType<DepositRecipeCondition>> DEPOSIT;

    private static boolean initialized;

    private WFRecipeConditions() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        RESEARCH = register(RESEARCH_NAME, ResearchRecipeCondition::new, ResearchRecipeCondition.CODEC);
        DEPOSIT = register(DEPOSIT_NAME, DepositRecipeCondition::new, DepositRecipeCondition.CODEC);
    }

    private static <T extends RecipeCondition<T>> RegistryEntry<RecipeConditionType<?>, RecipeConditionType<T>> register(
            String name, RecipeConditionType.ConditionFactory<T> factory, MapCodec<T> codec) {
        return WFCore.WF_MACHINES.simple(name, GTRegistries.Keys.RECIPE_CONDITION,
                () -> new RecipeConditionType<>(factory, codec));
    }
}
