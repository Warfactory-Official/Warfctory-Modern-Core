package com.norwood.wfcore.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.common.machine.DrillRigMachine;

/**
 * GregTech recipe condition that binds a {@code wfcore:drilling} recipe to a single deposit type. The drilling
 * rig only runs the recipe whose deposit id matches the deposit currently beneath its drill head. Add it in
 * KubeJS with {@code recipe.addCondition(WFDeposits.condition('iron_deposit'))}.
 */
public class DepositRecipeCondition extends RecipeCondition<DepositRecipeCondition> {

    public static final Codec<DepositRecipeCondition> CODEC = RecordCodecBuilder.create(instance -> RecipeCondition
            .isReverse(instance)
            .and(Codec.STRING.fieldOf("deposit").forGetter(DepositRecipeCondition::getDeposit))
            .apply(instance, DepositRecipeCondition::new));

    private String deposit = "";

    public DepositRecipeCondition() {}

    public DepositRecipeCondition(String deposit) {
        this.deposit = deposit == null ? "" : deposit;
    }

    public DepositRecipeCondition(boolean isReverse, String deposit) {
        super(isReverse);
        this.deposit = deposit == null ? "" : deposit;
    }

    public String getDeposit() {
        return deposit;
    }

    /** Resolve the configured id, defaulting a bare path to the {@code wfcore} namespace. */
    public ResourceLocation depositId() {
        if (deposit.isEmpty()) {
            return null;
        }
        return deposit.indexOf(':') >= 0 ? ResourceLocation.tryParse(deposit) : WFCore.id(deposit);
    }

    public boolean matches(ResourceLocation active) {
        ResourceLocation id = depositId();
        return id != null && id.equals(active);
    }

    @Override
    public RecipeConditionType<DepositRecipeCondition> getType() {
        return WFRecipeConditions.DEPOSIT;
    }

    @Override
    public Component getTooltips() {
        ResourceLocation id = depositId();
        DepositType type = id == null ? null : WFDeposits.get(id);
        Component name = type == null ? Component.literal(deposit) : Component.translatable(type.nameKey());
        return Component.translatable("wfcore.recipe.condition.deposit", name);
    }

    @Override
    public boolean testCondition(GTRecipe recipe, RecipeLogic recipeLogic) {
        if (recipeLogic.machine.self() instanceof DrillRigMachine drill) {
            return matches(drill.getActiveDepositTypeId());
        }
        return false;
    }

    @Override
    public DepositRecipeCondition createTemplate() {
        return new DepositRecipeCondition();
    }
}
