package com.norwood.wfcore.common.data;

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

import com.norwood.wfcore.WFCore;

import java.util.function.Consumer;

/**
 * WFCore recipe types. {@code LARGE_BLAST_FURNACE} mirrors the 1.12.2 large (warfactory) blast furnace
 * recipe map: 2 item inputs, 1 item output, no fluids, primitive (no EU). Recipes are fully
 * programmable from coremods, KubeJS and GroovyScript.
 */
public class WFRecipeTypes {

    public static GTRecipeType LARGE_BLAST_FURNACE;

    public static void init() {
        var id = WFCore.id("large_blast_furnace");
        LARGE_BLAST_FURNACE = new GTRecipeType(id, "multiblock");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, id, LARGE_BLAST_FURNACE);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, id, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(id, LARGE_BLAST_FURNACE);
        LARGE_BLAST_FURNACE.setMaxIOSize(2, 1, 0, 0)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setMaxTooltips(1)
                .setSound(GTSoundEntries.FURNACE);
    }

    /**
     * A single example recipe so the furnace works out of the box. Packs/coremods/KubeJS/GroovyScript
     * can add their own recipes to {@code wfcore:large_blast_furnace}.
     */
    public static void addDefaultRecipes(Consumer<FinishedRecipe> provider) {
        LARGE_BLAST_FURNACE.recipeBuilder(WFCore.id("steel_from_iron"))
                .inputItems(TagPrefix.ingot, GTMaterials.Iron)
                .inputItems(TagPrefix.dust, GTMaterials.Coal, 2)
                .outputItems(TagPrefix.ingot, GTMaterials.Steel)
                .duration(1200)
                .save(provider);
    }
}
