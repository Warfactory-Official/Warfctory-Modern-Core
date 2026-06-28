package com.norwood.wfcore.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Registers WFCore's recipe conditions into GregTech's shared condition registry. Call {@link #init()} once
 * from common setup, before any recipes are parsed or synced.
 */
public final class WFRecipeConditions {

    public static final String RESEARCH_NAME = "research_wfcore";
    public static final String DEPOSIT_NAME = "deposit_wfcore";

    public static RecipeConditionType<ResearchRecipeCondition> RESEARCH;
    public static RecipeConditionType<DepositRecipeCondition> DEPOSIT;

    private static boolean initialized;

    private WFRecipeConditions() {}

    @SuppressWarnings("removal")
    public static void init() {
        if (initialized) return;
        initialized = true;
        // GregTech freezes its registries after its own setup, and unfreeze()/freeze() are no-ops unless the
        // active mod container is gtceu/minecraft. Borrow GregTech's container for the registration so the
        // unfreeze actually takes effect, then restore ours.
        ModLoadingContext ctx = ModLoadingContext.get();
        ModContainer previous = ctx.getActiveContainer();
        ModContainer gtceu = ModList.get().getModContainerById("gtceu").orElse(null);
        try {
            if (gtceu != null) ctx.setActiveContainer(gtceu);
            boolean wasFrozen = GTRegistries.RECIPE_CONDITIONS.isFrozen();
            if (wasFrozen) GTRegistries.RECIPE_CONDITIONS.unfreeze();
            RESEARCH = GTRegistries.RECIPE_CONDITIONS.register(RESEARCH_NAME,
                    new RecipeConditionType<>(ResearchRecipeCondition::new, ResearchRecipeCondition.CODEC));
            DEPOSIT = GTRegistries.RECIPE_CONDITIONS.register(DEPOSIT_NAME,
                    new RecipeConditionType<>(DepositRecipeCondition::new, DepositRecipeCondition.CODEC));
            if (wasFrozen) GTRegistries.RECIPE_CONDITIONS.freeze();
        } finally {
            ctx.setActiveContainer(previous);
        }
    }
}
