package com.norwood.wfcore.integration.kubejs;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonObject;
import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.item.PackagedVehicleItem;

/**
 * KubeJS binding exposed as {@code WFVehicles}: a custom handler for authoring vehicle-factory recipes.
 *
 * <p>
 * GTCEu's generic KubeJS recipe builder is unusable for this recipe on this pack's Rhino: every
 * {@code GTRecipeJS.inputItems}/{@code outputItems} call is reported "ambiguous" (Rhino can't rank the
 * exact overload above the permissive {@code ItemStack[]}/{@code InputItem[]} varargs — it does this even
 * for a genuine {@code ItemStack[]}), so the recipe serialized empty. And the output carries an entity id in
 * NBT, which the JS/NBT round-trip mangles.
 *
 * <p>
 * So we build the recipe entirely in Java via GTCEu's own
 * {@link com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder}
 * (overloads resolve at compile time; NBT is native), serialize it to the exact recipe JSON GTCEu writes for
 * data packs, and let the script feed that to {@code event.custom(...)} — the one item-recipe entry point
 * that is NOT overloaded.
 *
 * <pre>{@code
 * // server_scripts:
 * ServerEvents.recipes(event => {
 *     event.custom(WFVehicles.recipe('wfcore:test_truck', 'superbwarfare:truck'))
 * })
 * }</pre>
 */
public class WFVehicleBindings {

    /**
     * Build a {@code wfcore:vehicle_assembler} recipe (as the JSON GTCEu would emit) that outputs a packaged
     * vehicle for {@code entityId}. Feed the result to {@code event.custom(...)}. Uses cheap fixed test inputs
     * (4 iron + 1 redstone); copy the body in Java if you want real inputs.
     *
     * @param recipeId a recipe id, e.g. {@code "wfcore:test_truck"}
     * @param entityId the entity to spawn, e.g. {@code "superbwarfare:truck"}
     */
    public JsonObject recipe(String recipeId, String entityId) {
        ResourceLocation rid = ResourceLocation.tryParse(recipeId);
        if (rid == null) {
            throw new IllegalArgumentException("Invalid recipe id: '" + recipeId + "'");
        }
        ResourceLocation eid = ResourceLocation.tryParse(entityId);
        if (eid == null || !ForgeRegistries.ENTITY_TYPES.containsKey(eid)) {
            throw new IllegalArgumentException(
                    "Unknown entity '" + entityId + "' - is the mod that provides it (e.g. Superb Warfare) loaded?");
        }

        JsonObject[] json = new JsonObject[1];
        VehicleFactoryRecipes.VEHICLE_ASSEMBLER.recipeBuilder(rid)
                .inputItems(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.REDSTONE))
                .outputItems(PackagedVehicleItem.of(eid))
                .EUt(GTValues.VA[GTValues.MV])
                .duration(100)
                .save(fr -> json[0] = fr.serializeRecipe());
        return json[0];
    }
}
