package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.api.registry.registrate.entry.GTRecipeTypeEntry;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;


import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.recipe.DrillingCustomRecipeLogic;
import com.norwood.wfcore.common.recipe.condition.DepositRecipeCondition;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import static com.norwood.wfcore.WFCore.WF_MACHINES;


/**
 * WFCore recipe types. {@code LARGE_BLAST_FURNACE} mirrors the 1.12.2 large (warfactory) blast furnace
 * recipe map: 2 item inputs, 1 item output, no fluids, primitive (no EU). Recipes are fully
 * programmable from coremods, KubeJS and GroovyScript.
 */
public class WFRecipeTypes {

    public static GTRecipeTypeEntry LARGE_BLAST_FURNACE;
    public static GTRecipeTypeEntry DRILLING;
    public static GTRecipeTypeEntry MISSILE_FACTORY;
    public static GTRecipeTypeEntry PRIMITIVE_ALLOYER;
    public static GTRecipeTypeEntry STRANDCASTER;
    public static GTRecipeTypeEntry GAS_EXTRACTOR;
    public static GTRecipeTypeEntry GREENHOUSE;
    public static GTRecipeTypeEntry MOB_FARMER;

    public static void init() {
        LARGE_BLAST_FURNACE = WF_MACHINES.recipeType("large_blast_furnace", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(2, 1, 0, 1)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.FURNACE)
                .register();

        DRILLING = WF_MACHINES.recipeType("drilling", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(0, 4, 2, 0)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.MINER)
                .addCustomRecipeLogic(new DrillingCustomRecipeLogic())
                .register();

        // Missile factory: assembles WFLib missile items (wflib:missile_<preset>) from
        // materials + fuel. Recipes are fully programmable from KubeJS/GroovyScript like the others.
        MISSILE_FACTORY = WF_MACHINES.recipeType("missile_factory", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(9, 1, 2, 0)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.ASSEMBLER)
                .register();

        PRIMITIVE_ALLOYER = WF_MACHINES.recipeType("primitive_alloyer", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(2, 0, 0, 1)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.FURNACE)
                .register();

        // Two fluid inputs are allowed so the runtime-added water coolant (see StrandcasterMachine) fits.
        STRANDCASTER = WF_MACHINES.recipeType("strandcaster", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(0, 1, 2, 0)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.COOLING)
                .register();

        // Gas Extractor: an air/gas collector. Which gas to collect is selected with a Programmed Circuit in
        // the Item Input Bus (recipes gate on it via `.circuit(n)`), which is a not-consumed item input — so
        // item inputs are sized for the circuit plus a couple of real inputs. It can also process items+fluids
        // and outputs both. Recipes are authored entirely in KubeJS on `wfcore:gas_extractor`.
        GAS_EXTRACTOR = WF_MACHINES.recipeType("gas_extractor", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(3, 3, 3, 3)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.COMPRESSOR)
                .register();


        GREENHOUSE = WF_MACHINES.recipeType("greenhouse", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(3, 3, 1, 0)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.BATH)
                .register();


        MOB_FARMER = WF_MACHINES.recipeType("mob_farmer", GTRecipeTypes.MULTIBLOCK)
                .setMaxIOSize(2, 6, 1, 0)
                .UI(builder -> builder.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
                .setSound(GTSoundEntries.MACERATOR)
                .register();
    }

    /**
     * A single example recipe so the furnace works out of the box. Packs/coremods/KubeJS/GroovyScript
     * can add their own recipes to {@code wfcore:large_blast_furnace}.
     */
    public static void addDefaultRecipes(RecipeOutput provider) {
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
                .chancedOutput(new ItemStack(Items.RAW_GOLD), 1000)
                .EUt(GTValues.VA[GTValues.LV])
                .duration(100)
                .addCondition(new DepositRecipeCondition(WFCore.id("iron_deposit").toString()))
                .save(provider);


        VanillaRecipeHelper.addShapedRecipe(provider, WFCore.id("unfired_fire_clay_fluid_pipe"),
                ChemicalHelper.get(TagPrefix.pipeNormalFluid, WFMaterials.UnfiredFireClay, 4),
                "CCC", "w r", 'C', Items.CLAY_BALL);

        VanillaRecipeHelper.addSmeltingRecipe(provider, WFCore.id("fire_clay_fluid_pipe"),
                ChemicalHelper.get(TagPrefix.pipeNormalFluid, WFMaterials.UnfiredFireClay),
                ChemicalHelper.get(TagPrefix.pipeNormalFluid, WFMaterials.FireClay), 0.0f);

        addFoundryRecipes(provider);
        addMoldRecipes(provider);

        // No hardcoded missile-factory recipes: the Missile Factory ships empty and its recipes are authored
        // entirely in KubeJS on wfcore:missile_factory. The dev/test set lives in
        // run/kubejs/server_scripts/wfcore_missile_factory.js (copy into the modpack for prod).

        addPrimitiveAlloyerRecipes(provider);
        addStrandcasterRecipes(provider);
    }

    /**
     * The foundry casting blocks. The molds themselves are GregTech's own {@code SHAPE_MOLD_*} items (see
     * {@link FoundryMolds}), so they keep their stock GT recipes — nothing to add here.
     */
    private static void addFoundryRecipes(RecipeOutput provider) {
        VanillaRecipeHelper.addShapedRecipe(provider, WFCore.id("foundry_basin"),
                WFBlocks.FOUNDRY_BASIN.asStack(),
                "B B", "B B", "BBB", 'B', new ItemStack(Items.BRICKS));
        VanillaRecipeHelper.addShapedRecipe(provider, WFCore.id("foundry_mold_caster"),
                WFBlocks.FOUNDRY_MOLD_CASTER.asStack(),
                "B B", "BBB", 'B', new ItemStack(Items.BRICKS));
    }

    /**
     * WFCore's fire clay molds (see {@link WFMolds}): each shape's clay-ball {@link WFMolds.Shape#pattern}
     * crafts the raw {@code clay_*_mold}, which is then smelted into the reusable {@code fireclay_*_mold} — a
     * clay-then-fire flow that needs no machines, so the foundry can be bootstrapped early. The per-shape
     * patterns are all distinct, so the ten shaped recipes never collide.
     */
    private static void addMoldRecipes(RecipeOutput provider) {
        for (WFMolds.Shape shape : WFMolds.Shape.values()) {
            // Shape clay balls into the raw mold: pattern rows, then the 'C' -> clay ball key.
            Object[] args = new Object[shape.pattern.length + 2];
            System.arraycopy(shape.pattern, 0, args, 0, shape.pattern.length);
            args[shape.pattern.length] = 'C';
            args[shape.pattern.length + 1] = new ItemStack(Items.CLAY_BALL);
            VanillaRecipeHelper.addShapedRecipe(provider, WFCore.id(shape.unfiredId()),
                    WFMolds.UNFIRED.get(shape).asStack(), args);

            // Fire it in a furnace: raw clay mold -> reusable fire clay mold.
            VanillaRecipeHelper.addSmeltingRecipe(provider, WFCore.id(shape.firedId()),
                    WFMolds.UNFIRED.get(shape).asStack(), WFMolds.FIRED.get(shape).asStack(), 0.0f);
        }
    }

    private static final int ALLOY_BATCH = 16;


    private static void addPrimitiveAlloyerRecipes(RecipeOutput provider) {
        PRIMITIVE_ALLOYER.recipeBuilder(WFCore.id("brass"))
                .inputItems(TagPrefix.ingot, GTMaterials.Copper, 3 * ALLOY_BATCH)
                .inputItems(TagPrefix.ingot, GTMaterials.Zinc, 1 * ALLOY_BATCH)
                .outputFluids(GTMaterials.Brass.getFluid(576 * ALLOY_BATCH))
                .duration(80)
                .save(provider);

        PRIMITIVE_ALLOYER.recipeBuilder(WFCore.id("bronze"))
                .inputItems(TagPrefix.ingot, GTMaterials.Tin, 3 * ALLOY_BATCH)
                .inputItems(TagPrefix.ingot, GTMaterials.Copper, 1 * ALLOY_BATCH)
                .outputFluids(GTMaterials.Bronze.getFluid(576 * ALLOY_BATCH))
                .duration(80)
                .save(provider);

        PRIMITIVE_ALLOYER.recipeBuilder(WFCore.id("red_alloy"))
                .inputItems(TagPrefix.ingot, GTMaterials.Copper, 1 * ALLOY_BATCH)
                .inputItems(TagPrefix.dust, GTMaterials.Redstone, 4 * ALLOY_BATCH)
                .outputFluids(GTMaterials.RedAlloy.getFluid(144 * ALLOY_BATCH))
                .duration(20)
                .save(provider);
    }


    private static void addStrandcasterRecipes(RecipeOutput provider) {
        cast(provider, "steel_ingot", GTMaterials.Steel);
        cast(provider, "brass_ingot", GTMaterials.Brass);
        cast(provider, "bronze_ingot", GTMaterials.Bronze);
        cast(provider, "red_alloy_ingot", GTMaterials.RedAlloy);
    }

    private static void cast(RecipeOutput provider, String id,
                            Material alloy) {
        STRANDCASTER.recipeBuilder(WFCore.id(id))
                .inputFluids(alloy.getFluid(144 * ALLOY_BATCH))
                .inputFluids(new FluidStack(Fluids.WATER,100 * ALLOY_BATCH))
                .outputItems(TagPrefix.ingot, alloy, ALLOY_BATCH)
                .duration(120)
                .save(provider);
    }

    private static void addDrillingRecipe(RecipeOutput provider, String recipeId, String deposit,
                                          Item output) {
        DRILLING.recipeBuilder(WFCore.id(recipeId))
                .outputItems(output)
                .EUt(GTValues.VA[GTValues.LV])
                .duration(200)
                .addCondition(new DepositRecipeCondition(WFCore.id(deposit).toString()))
                .save(provider);
    }
}
