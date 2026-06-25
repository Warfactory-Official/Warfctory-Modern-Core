package com.norwood.wfcore.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;

import net.minecraft.network.chat.Component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchGate;
import com.norwood.wfcore.api.research.ResearchRegistry;

/**
 * GregTech recipe condition that gates a recipe behind a WFCore research. Because GregTech evaluates a
 * recipe's conditions for every machine before it may run, attaching this to a recipe makes any crafting
 * machine (assembler, assembly line, ...) refuse that exact recipe until its research is unlocked. Add it in
 * KubeJS with {@code someRecipe.addCondition(WFResearch.condition('my_research'))}.
 */
public class ResearchRecipeCondition extends RecipeCondition<ResearchRecipeCondition> {

    public static final Codec<ResearchRecipeCondition> CODEC = RecordCodecBuilder.create(instance -> RecipeCondition
            .isReverse(instance)
            .and(Codec.STRING.fieldOf("research").forGetter(ResearchRecipeCondition::getResearch))
            .apply(instance, ResearchRecipeCondition::new));

    private String research = "";

    public ResearchRecipeCondition() {}

    public ResearchRecipeCondition(String research) {
        this.research = research == null ? "" : research;
    }

    public ResearchRecipeCondition(boolean isReverse, String research) {
        super(isReverse);
        this.research = research == null ? "" : research;
    }

    public String getResearch() {
        return research;
    }

    @Override
    public RecipeConditionType<ResearchRecipeCondition> getType() {
        return WFRecipeConditions.RESEARCH;
    }

    @Override
    public Component getTooltips() {
        Research r = ResearchRegistry.get(research);
        Component name = r == null ? Component.literal(research) : Component.translatable(r.getNameKey());
        return Component.translatable("wfcore.recipe.condition.research", name);
    }

    @Override
    public boolean testCondition(GTRecipe recipe, RecipeLogic recipeLogic) {
        return ResearchGate.isUnlocked(recipeLogic.machine.self(), research);
    }

    @Override
    public ResearchRecipeCondition createTemplate() {
        return new ResearchRecipeCondition();
    }
}
