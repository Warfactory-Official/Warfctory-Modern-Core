package com.norwood.wfcore.integration.kubejs;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonObject;
import com.norwood.wfcore.SuperbOverrides;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.item.PackagedVehicleItem;

import java.util.LinkedHashMap;
import java.util.Map;

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
 *     // 2-arg form defaults to the Light Ground Vehicle Factory map:
 *     event.custom(WFVehicles.recipe('wfcore:test_truck', 'superbwarfare:truck'))
 *     // 3-arg form picks which factory map the recipe belongs to:
 *     event.custom(WFVehicles.recipe('wfcore:test_tank', 'superbwarfare:bmp_2', 'tank_assembly'))
 * })
 * }</pre>
 *
 * <p>
 * Each vehicle factory now has its own recipe map (see {@link VehicleFactoryRecipes}); the third argument
 * is the factory's machine path — one of {@code light_ground_vehicle_factory}, {@code tank_assembly},
 * {@code light_plane_assembler}, {@code heavy_plane_assembler}, {@code heavy_vehicle_depot}.
 */
public class WFVehicleBindings {

    /**
     * Build a recipe for the Light Ground Vehicle Factory map ({@code wfcore:light_ground_vehicle_factory}).
     * Convenience 2-arg form; see {@link #recipe(String, String, String)} to target another factory.
     *
     * @param recipeId a recipe id, e.g. {@code "wfcore:test_truck"}
     * @param entityId the entity to spawn, e.g. {@code "superbwarfare:truck"}
     */
    public JsonObject recipe(String recipeId, String entityId) {
        return recipe(recipeId, entityId, "light_ground_vehicle_factory");
    }

    /**
     * Build a vehicle-factory recipe (as the JSON GTCEu would emit) on {@code factory}'s map that outputs a
     * packaged vehicle for {@code entityId}. Feed the result to {@code event.custom(...)}. Uses cheap fixed
     * test inputs (4 iron + 1 redstone); copy the body in Java if you want real inputs.
     *
     * @param recipeId a recipe id, e.g. {@code "wfcore:test_truck"}
     * @param entityId the entity to spawn, e.g. {@code "superbwarfare:truck"}
     * @param factory  which factory map the recipe belongs to, e.g. {@code "tank_assembly"}
     */
    public JsonObject recipe(String recipeId, String entityId, String factory) {
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
        VehicleFactoryRecipes.byName(factory).recipeBuilder(rid)
                .inputItems(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.REDSTONE))
                .outputItems(PackagedVehicleItem.of(eid))
                .EUt(GTValues.VA[GTValues.MV])
                .duration(100)
                .save(fr -> json[0] = fr.serializeRecipe());
        return json[0];
    }

    // --- Vehicle fuel/storage overrides (moved here from the wfcore.toml `vehicles` config) ---------------

    /** Open a builder to register a per-vehicle fuel/storage override. Call {@code .register()} to apply it. */
    public OverrideBuilder override(String vehicleId) {
        return new OverrideBuilder(vehicleId);
    }

    /** Mark a vehicle id as ploughing through cacti/logs/leaves as it drives (was config {@code foliageBreakers}). */
    public void foliageBreaker(String vehicleId) {
        if (vehicleId != null && !vehicleId.isBlank()) {
            SuperbOverrides.registerFoliageBreaker(vehicleId);
        }
    }

    /**
     * Builds a {@link SuperbOverrides.OverrideData} for one vehicle. {@code fuel(...)} is a whitelist (the vehicle
     * accepts only the listed fluids), each ratio the energy multiplier per mB; {@code storage(...)} switches the
     * vehicle to WFCore's resizable ModularUI storage.
     *
     * <pre>{@code
     * WFVehicles.override('superbwarfare:truck')
     *     .maxFuel(4000)
     *     .fuel('gtceu:diesel', 1.0).fuel('gtceu:gasoline', 1.5)
     *     .storage(50, 10)
     *     .register()
     * }</pre>
     */
    public static final class OverrideBuilder {

        private final String vehicleId;
        private int maxFuel = 4000;
        private Integer storageSize;
        private int storageColumns = 9;
        private final Map<Fluid, Float> fluids = new LinkedHashMap<>();

        private OverrideBuilder(String vehicleId) {
            this.vehicleId = vehicleId;
        }

        /** Fuel-tank capacity in mB (default 4000). */
        public OverrideBuilder maxFuel(int millibuckets) {
            if (millibuckets > 0) {
                this.maxFuel = millibuckets;
            }
            return this;
        }

        /** Accept {@code fluidId} as fuel at the given energy multiplier per mB. Ignored if unknown or ratio <= 0. */
        public OverrideBuilder fuel(String fluidId, double ratio) {
            ResourceLocation rl = fluidId == null ? null : ResourceLocation.tryParse(fluidId);
            Fluid fluid = rl == null ? null : ForgeRegistries.FLUIDS.getValue(rl);
            if (fluid == null || fluid == Fluids.EMPTY) {
                WFCore.LOGGER.warn("WFVehicles.override({}): ignoring unknown fuel fluid '{}'", vehicleId, fluidId);
            } else if (ratio > 0) {
                fluids.put(fluid, (float) ratio);
            }
            return this;
        }

        /** Give the vehicle a WFCore resizable storage of {@code size} slots (9-wide grid). */
        public OverrideBuilder storage(int size) {
            return storage(size, 9);
        }

        /** Give the vehicle a WFCore resizable storage of {@code size} slots laid out {@code columns} wide. */
        public OverrideBuilder storage(int size, int columns) {
            this.storageSize = size > 0 ? size : null;
            if (columns > 0) {
                this.storageColumns = columns;
            }
            return this;
        }

        /** Build + register the override; ignored (with a warning) if it sets neither fuel nor storage. */
        public void register() {
            if (vehicleId == null || vehicleId.isBlank()) {
                return;
            }
            if (fluids.isEmpty() && storageSize == null) {
                WFCore.LOGGER.warn("WFVehicles.override({}) sets neither fuel nor storage; ignored", vehicleId);
                return;
            }
            SuperbOverrides.registerOverride(vehicleId,
                    new SuperbOverrides.OverrideData(maxFuel, fluids, storageSize, storageColumns));
        }
    }
}
