package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.recipe.DrillingCustomRecipeLogic;
import com.norwood.wfcore.common.recipe.condition.DepositRecipeCondition;

import java.util.function.Consumer;

/**
 * WFCore recipe types. {@code LARGE_BLAST_FURNACE} mirrors the 1.12.2 large (warfactory) blast furnace
 * recipe map: 2 item inputs, 1 item output, no fluids, primitive (no EU). Recipes are fully
 * programmable from coremods, KubeJS and GroovyScript.
 */
public class WFRecipeTypes {

    public static GTRecipeType LARGE_BLAST_FURNACE;
    public static GTRecipeType DRILLING;

    public static void init() {
        var id = WFCore.id("large_blast_furnace");
        LARGE_BLAST_FURNACE = new GTRecipeType(id, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, id, LARGE_BLAST_FURNACE);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, id, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(id, LARGE_BLAST_FURNACE);
        LARGE_BLAST_FURNACE.setMaxIOSize(2, 1, 0, 1)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.FURNACE);

        var drillId = WFCore.id("drilling");
        DRILLING = new GTRecipeType(drillId, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, drillId, DRILLING);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, drillId, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(drillId, DRILLING);
        DRILLING.setMaxIOSize(0, 4, 2, 0)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.MINER);
        DRILLING.addCustomRecipeLogic(new DrillingCustomRecipeLogic());
    }

    /**
     * A single example recipe so the furnace works out of the box. Packs/coremods/KubeJS/GroovyScript
     * can add their own recipes to {@code wfcore:large_blast_furnace}.
     */
    public static void addDefaultRecipes(Consumer<FinishedRecipe> provider) {
        LARGE_BLAST_FURNACE.recipeBuilder(WFCore.id("liquid_steel_from_iron"))
                .inputItems(TagPrefix.ingot, GTMaterials.Iron)
                .inputItems(TagPrefix.dust, GTMaterials.Coal, 2)
                .outputFluids(GTMaterials.Steel.getFluid(144))
                .outputItems(TagPrefix.dustTiny, GTMaterials.DarkAsh)
                .duration(1200)
                .save(provider);

        addDrillingRecipe(provider, "drill_iron_deposit", "iron_deposit", Items.RAW_IRON);
        addDrillingRecipe(provider, "drill_copper_deposit", "copper_deposit", Items.RAW_COPPER);
        addDrillingRecipe(provider, "drill_gold_deposit", "gold_deposit", Items.RAW_GOLD);

        DRILLING.recipeBuilder(WFCore.id("drill_iron_deposit_boosted"))
                .inputFluids(GTMaterials.DrillingFluid, 100)
                .outputItems(Items.RAW_IRON, 2)
                .chancedOutput(new ItemStack(Items.RAW_GOLD), 1000, 0)
                .EUt(GTValues.VA[GTValues.LV])
                .duration(100)
                .addCondition(new DepositRecipeCondition(WFCore.id("iron_deposit").toString()))
                .save(provider);
    }

    private static void addDrillingRecipe(Consumer<FinishedRecipe> provider, String recipeId, String deposit,
                                          net.minecraft.world.item.Item output) {
        DRILLING.recipeBuilder(WFCore.id(recipeId))
                .outputItems(output)
                .EUt(GTValues.VA[GTValues.LV])
                .duration(200)
                .addCondition(new DepositRecipeCondition(WFCore.id(deposit).toString()))
                .save(provider);
    }
}
