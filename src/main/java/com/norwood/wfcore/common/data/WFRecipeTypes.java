package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.recipe.DrillingCustomRecipeLogic;
import com.norwood.wfcore.common.recipe.condition.DepositRecipeCondition;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * WFCore recipe types. {@code LARGE_BLAST_FURNACE} mirrors the 1.12.2 large (warfactory) blast furnace
 * recipe map: 2 item inputs, 1 item output, no fluids, primitive (no EU). Recipes are fully
 * programmable from coremods, KubeJS and GroovyScript.
 */
public class WFRecipeTypes {

    public static GTRecipeType LARGE_BLAST_FURNACE;
    public static GTRecipeType DRILLING;
    public static GTRecipeType MISSILE_FACTORY;
    public static GTRecipeType PRIMITIVE_ALLOYER;
    public static GTRecipeType STRANDCASTER;

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

        // Missile factory: assembles WF-Ballistics missile items (wfballistics:missile_<preset>) from
        // materials + fuel. Recipes are fully programmable from KubeJS/GroovyScript like the others.
        var missileId = WFCore.id("missile_factory");
        MISSILE_FACTORY = new GTRecipeType(missileId, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, missileId, MISSILE_FACTORY);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, missileId, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(missileId, MISSILE_FACTORY);
        MISSILE_FACTORY.setMaxIOSize(6, 1, 2, 0)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.ASSEMBLER);

        var alloyerId = WFCore.id("primitive_alloyer");
        PRIMITIVE_ALLOYER = new GTRecipeType(alloyerId, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, alloyerId, PRIMITIVE_ALLOYER);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, alloyerId, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(alloyerId, PRIMITIVE_ALLOYER);
        PRIMITIVE_ALLOYER.setMaxIOSize(2, 0, 0, 1)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.FURNACE);

        var casterId = WFCore.id("strandcaster");
        STRANDCASTER = new GTRecipeType(casterId, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, casterId, STRANDCASTER);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, casterId, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(casterId, STRANDCASTER);
        // Two fluid inputs are allowed so the runtime-added water coolant (see StrandcasterMachine) fits.
        STRANDCASTER.setMaxIOSize(0, 1, 2, 0)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.COOLING);
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

        VanillaRecipeHelper.addShapedRecipe(provider, WFCore.id("fire_clay_fluid_pipe"),
                ChemicalHelper.get(TagPrefix.pipeNormalFluid, WFMaterials.FireClay, 4),
                "CCC", 'C', Items.CLAY_BALL);

        // Starter missile recipes (liquid-fuel airframes take Diesel, solid-fuel take RocketFuel);
        // packs override/extend via KubeJS on wfcore:missile_factory.
        addMissileRecipe(provider, "missile_cruise", 800, GTMaterials.Diesel, 500,
                b -> b.inputItems(TagPrefix.plate, GTMaterials.Steel, 8)
                        .inputItems(TagPrefix.plate, GTMaterials.Aluminium, 4)
                        .inputItems(TagPrefix.dust, GTMaterials.Gunpowder, 8)
                        .inputItems(new ItemStack(Items.TNT, 2)));
        addMissileRecipe(provider, "missile_ballistic", 1200, GTMaterials.RocketFuel, 1000,
                b -> b.inputItems(TagPrefix.plate, GTMaterials.Steel, 12)
                        .inputItems(TagPrefix.plate, GTMaterials.Titanium, 4)
                        .inputItems(TagPrefix.dust, GTMaterials.Gunpowder, 16)
                        .inputItems(new ItemStack(Items.TNT, 4)));
        addMissileRecipe(provider, "missile_fragmentation", 1000, GTMaterials.RocketFuel, 750,
                b -> b.inputItems(TagPrefix.plate, GTMaterials.Steel, 8)
                        .inputItems(TagPrefix.round, GTMaterials.Iron, 32)
                        .inputItems(TagPrefix.dust, GTMaterials.Gunpowder, 12)
                        .inputItems(new ItemStack(Items.TNT, 2)));
        // Interceptor rounds for the Interceptor Battery (drawn from linked factories, never carried). Light,
        // fast airframes: mostly aluminium with a small warhead.
        addMissileRecipe(provider, "missile_interceptor", 800, GTMaterials.RocketFuel, 500,
                b -> b.inputItems(TagPrefix.plate, GTMaterials.Aluminium, 8)
                        .inputItems(TagPrefix.plate, GTMaterials.Steel, 4)
                        .inputItems(TagPrefix.dust, GTMaterials.Gunpowder, 6)
                        .inputItems(new ItemStack(Items.TNT, 1)));
        addMissileRecipe(provider, "missile_interceptor_supersonic", 1100, GTMaterials.RocketFuel, 800,
                b -> b.inputItems(TagPrefix.plate, GTMaterials.Titanium, 8)
                        .inputItems(TagPrefix.plate, GTMaterials.Aluminium, 6)
                        .inputItems(TagPrefix.dust, GTMaterials.Gunpowder, 8)
                        .inputItems(new ItemStack(Items.TNT, 2)));

        addPrimitiveAlloyerRecipes(provider);
        addStrandcasterRecipes(provider);
    }

    /**
     * One missile-factory recipe: {@code customize} adds the material inputs, the airframe fuel goes in as
     * a fluid, and the output is the wfballistics missile item of that registry name. The item is looked up
     * by id so this class carries no compile-time wfballistics dependency; the missing-item case cannot
     * happen in practice (wfballistics is a mandatory dep) but is skipped defensively for datagen safety.
     */
    private static void addMissileRecipe(Consumer<FinishedRecipe> provider, String itemName, int duration,
                                         Material fuel, int fuelAmount,
                                         UnaryOperator<GTRecipeBuilder> customize) {
        var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation("wfballistics", itemName))
                .orElse(null);
        if (item == null) {
            return;
        }
        customize.apply(MISSILE_FACTORY.recipeBuilder(WFCore.id(itemName)))
                .inputFluids(fuel.getFluid(fuelAmount))
                .outputItems(item)
                .EUt(GTValues.VA[GTValues.HV])
                .duration(duration)
                .save(provider);
    }

    /**
     * Primitive Alloyer melt-and-alloy recipes. Fuel (lava or solid) is the machine's business, so it never
     * appears here. Steel is deliberately absent — that is the Large Blast Furnace's job.
     */
    private static void addPrimitiveAlloyerRecipes(Consumer<FinishedRecipe> provider) {
        PRIMITIVE_ALLOYER.recipeBuilder(WFCore.id("brass"))
                .inputItems(TagPrefix.ingot, GTMaterials.Copper, 3)
                .inputItems(TagPrefix.ingot, GTMaterials.Zinc, 1)
                .outputFluids(GTMaterials.Brass.getFluid(576))
                .duration(80)
                .save(provider);

        PRIMITIVE_ALLOYER.recipeBuilder(WFCore.id("bronze"))
                .inputItems(TagPrefix.ingot, GTMaterials.Tin, 3)
                .inputItems(TagPrefix.ingot, GTMaterials.Copper, 1)
                .outputFluids(GTMaterials.Bronze.getFluid(576))
                .duration(80)
                .save(provider);

        PRIMITIVE_ALLOYER.recipeBuilder(WFCore.id("red_alloy"))
                .inputItems(TagPrefix.ingot, GTMaterials.Copper, 1)
                .inputItems(TagPrefix.dust, GTMaterials.Redstone, 4)
                .outputFluids(GTMaterials.RedAlloy.getFluid(144))
                .duration(20)
                .save(provider);
    }

    /**
     * Strandcaster casting recipes: 144 mb molten alloy in, 1 ingot out, 120-tick base duration. Supplying
     * water to a coolant hatch halves this to 60 ticks at runtime (see {@code StrandcasterMachine}). Steel is
     * kept here so the caster can solidify the Large Blast Furnace's liquid steel into ingots.
     */
    private static void addStrandcasterRecipes(Consumer<FinishedRecipe> provider) {
        cast(provider, "steel_ingot", GTMaterials.Steel);
        cast(provider, "brass_ingot", GTMaterials.Brass);
        cast(provider, "bronze_ingot", GTMaterials.Bronze);
        cast(provider, "red_alloy_ingot", GTMaterials.RedAlloy);
    }

    private static void cast(Consumer<FinishedRecipe> provider, String id,
                             com.gregtechceu.gtceu.api.data.chemical.material.Material alloy) {
        STRANDCASTER.recipeBuilder(WFCore.id(id))
                .inputFluids(alloy.getFluid(144))
                .outputItems(TagPrefix.ingot, alloy)
                .duration(120)
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
