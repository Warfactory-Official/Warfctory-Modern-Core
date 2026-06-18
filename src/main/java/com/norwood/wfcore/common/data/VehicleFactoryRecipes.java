package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.item.PackagedVehicleItem;

import java.util.function.Consumer;

/**
 * Standard GT recipe type for the vehicle factories, so recipes are fully programmable from
 * coremods, KubeJS and GroovyScript. Recipes use ordinary item/fluid/EU inputs; the single item
 * output is a {@link com.norwood.wfcore.common.item.PackagedVehicleItem} that encodes which vehicle
 * to spawn on completion.
 */
public class VehicleFactoryRecipes {

    public static GTRecipeType VEHICLE_ASSEMBLER;

    public static void init() {
        var id = WFCore.id("vehicle_assembler");
        VEHICLE_ASSEMBLER = new GTRecipeType(id, "electric");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, id, VEHICLE_ASSEMBLER);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, id, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(id, VEHICLE_ASSEMBLER);
        VEHICLE_ASSEMBLER.setMaxIOSize(9, 1, 2, 0).setEUIO(IO.IN).setMaxTooltips(3)
                .setSound(GTSoundEntries.ASSEMBLER);
    }

    /**
     * A single example recipe so the MV factory works out of the box. Packs/coremods/KubeJS/GroovyScript
     * can add their own recipes to {@code wfcore:vehicle_assembler}; the vehicle is just the item output.
     */
    public static void addDefaultRecipes(Consumer<FinishedRecipe> provider) {
        VEHICLE_ASSEMBLER.recipeBuilder(WFCore.id("lav_150"))
                .inputItems(TagPrefix.plate, GTMaterials.Steel, 16)
                .inputItems(TagPrefix.gear, GTMaterials.Steel, 4)
                .inputItems(TagPrefix.plate, GTMaterials.Aluminium, 8)
                .EUt(GTValues.VA[GTValues.MV])
                .duration(600)
                .outputItems(PackagedVehicleItem.of(new ResourceLocation("superbwarfare", "lav_150")))
                .save(provider);
    }
}
