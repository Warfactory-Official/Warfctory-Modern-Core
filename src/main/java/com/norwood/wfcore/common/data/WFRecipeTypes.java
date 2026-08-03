package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
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
    public static GTRecipeType MISSILE_FACTORY;
    public static GTRecipeType PRIMITIVE_ALLOYER;
    public static GTRecipeType STRANDCASTER;
    public static GTRecipeType GAS_EXTRACTOR;
    public static GTRecipeType GREENHOUSE;
    public static GTRecipeType MOB_FARMER;

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

        // Gas Extractor: an air/gas collector. Which gas to collect is selected with a Programmed Circuit in
        // the Item Input Bus (recipes gate on it via `.circuit(n)`), which is a not-consumed item input — so
        // item inputs are sized for the circuit plus a couple of real inputs. It can also process items+fluids
        // and outputs both. Recipes are authored entirely in KubeJS on `wfcore:gas_extractor`.
        var gasId = WFCore.id("gas_extractor");
        GAS_EXTRACTOR = new GTRecipeType(gasId, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, gasId, GAS_EXTRACTOR);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, gasId, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(gasId, GAS_EXTRACTOR);
        GAS_EXTRACTOR.setMaxIOSize(3, 3, 3, 3)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.COMPRESSOR);


        var greenhouseId = WFCore.id("greenhouse");
        GREENHOUSE = new GTRecipeType(greenhouseId, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, greenhouseId, GREENHOUSE);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, greenhouseId, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(greenhouseId, GREENHOUSE);
        GREENHOUSE.setMaxIOSize(3, 3, 1, 0)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.BATH);


        var mobFarmerId = WFCore.id("mob_farmer");
        MOB_FARMER = new GTRecipeType(mobFarmerId, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, mobFarmerId, MOB_FARMER);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, mobFarmerId, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(mobFarmerId, MOB_FARMER);
        MOB_FARMER.setMaxIOSize(2, 6, 1, 0)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.MACERATOR);
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


        VanillaRecipeHelper.addShapedRecipe(provider, WFCore.id("unfired_fire_clay_fluid_pipe"),
                ChemicalHelper.get(TagPrefix.pipeNormalFluid, WFMaterials.UnfiredFireClay, 4),
                "CCC", "w r", 'C', Items.CLAY_BALL);

        VanillaRecipeHelper.addSmeltingRecipe(provider, WFCore.id("fire_clay_fluid_pipe"),
                ChemicalHelper.get(TagPrefix.pipeNormalFluid, WFMaterials.UnfiredFireClay),
                ChemicalHelper.get(TagPrefix.pipeNormalFluid, WFMaterials.FireClay));

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
    private static void addFoundryRecipes(Consumer<FinishedRecipe> provider) {
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
    private static void addMoldRecipes(Consumer<FinishedRecipe> provider) {
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
                    WFMolds.UNFIRED.get(shape).asStack(), WFMolds.FIRED.get(shape).asStack());
        }
    }

    private static final int ALLOY_BATCH = 16;


    private static void addPrimitiveAlloyerRecipes(Consumer<FinishedRecipe> provider) {
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


    private static void addStrandcasterRecipes(Consumer<FinishedRecipe> provider) {
        cast(provider, "steel_ingot", GTMaterials.Steel);
        cast(provider, "brass_ingot", GTMaterials.Brass);
        cast(provider, "bronze_ingot", GTMaterials.Bronze);
        cast(provider, "red_alloy_ingot", GTMaterials.RedAlloy);
    }

    private static void cast(Consumer<FinishedRecipe> provider, String id,
                             com.gregtechceu.gtceu.api.data.chemical.material.Material alloy) {
        STRANDCASTER.recipeBuilder(WFCore.id(id))
                .inputFluids(alloy.getFluid(144 * ALLOY_BATCH))
                .outputItems(TagPrefix.ingot, alloy, ALLOY_BATCH)
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
