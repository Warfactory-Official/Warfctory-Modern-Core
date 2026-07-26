package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.item.PackagedVehicleItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Per-machine GT recipe maps for the vehicle factories. Every vehicle-assembly multiblock owns its own
 * recipe type, so each factory has a separate, independently-programmable recipe list and its own JEI
 * category (a tank recipe can't be built in a plane assembler, and vice versa). All maps share the same
 * IO layout and are fully programmable from coremods, KubeJS and GroovyScript. Recipes use ordinary
 * item/fluid/EU inputs; the single item output is a
 * {@link com.norwood.wfcore.common.item.PackagedVehicleItem} that encodes which vehicle to spawn on
 * completion.
 */
public class VehicleFactoryRecipes {

    /** {@code wfcore:light_ground_vehicle_factory} — the MV Light Ground Vehicle Factory. */
    public static GTRecipeType LIGHT_GROUND_VEHICLE_FACTORY;
    /** {@code wfcore:tank_assembly} — the Tank Assembly Line. */
    public static GTRecipeType TANK_ASSEMBLY;
    /** {@code wfcore:light_plane_assembler} — the Light Plane Assembler. */
    public static GTRecipeType LIGHT_PLANE_ASSEMBLER;
    /** {@code wfcore:heavy_plane_assembler} — the Heavy Plane Assembler. */
    public static GTRecipeType HEAVY_PLANE_ASSEMBLER;
    /** {@code wfcore:heavy_vehicle_depot} — the Heavy Vehicle Depot. */
    public static GTRecipeType HEAVY_VEHICLE_DEPOT;

    /** Every map keyed by its machine path, so KubeJS/GroovyScript can resolve a map by name. */
    private static final Map<String, GTRecipeType> BY_NAME = new LinkedHashMap<>();

    public static void init() {
        LIGHT_GROUND_VEHICLE_FACTORY = register("light_ground_vehicle_factory");
        TANK_ASSEMBLY = register("tank_assembly");
        LIGHT_PLANE_ASSEMBLER = register("light_plane_assembler");
        HEAVY_PLANE_ASSEMBLER = register("heavy_plane_assembler");
        HEAVY_VEHICLE_DEPOT = register("heavy_vehicle_depot");
    }

    /**
     * Register one vehicle-factory recipe map. Every map shares the same IO layout (9 item + 2 fluid inputs,
     * one packaged-vehicle output, EU in) and the assembler sound; only the id and JEI category differ, which
     * is exactly what gives each factory its own separate recipe list.
     */
    private static GTRecipeType register(String name) {
        var id = WFCore.id(name);
        GTRecipeType type = new GTRecipeType(id, "electric");
        GTRegistries.register(BuiltInRegistries.RECIPE_TYPE, id, type);
        GTRegistries.register(BuiltInRegistries.RECIPE_SERIALIZER, id, new GTRecipeSerializer());
        GTRegistries.RECIPE_TYPES.register(id, type);
        type.setMaxIOSize(9, 1, 2, 0).setEUIO(IO.IN).setMaxTooltips(3)
                .setSound(GTSoundEntries.ASSEMBLER);
        BY_NAME.put(name, type);
        return type;
    }

    /**
     * Look up a vehicle-factory recipe map by its machine path (e.g. {@code "tank_assembly"}). Accepts a bare
     * path or a {@code wfcore:}-prefixed id, and throws if no such map exists — used by the KubeJS binding so
     * scripts can pick which factory a recipe belongs to.
     */
    public static GTRecipeType byName(String name) {
        String path = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        GTRecipeType type = BY_NAME.get(path);
        if (type == null) {
            throw new IllegalArgumentException("Unknown vehicle factory '" + name + "'. Valid factories: "
                    + String.join(", ", BY_NAME.keySet()));
        }
        return type;
    }

    /**
     * No baked recipes. Every vehicle-factory recipe is supplied by the pack via KubeJS
     * (kubejs/server_scripts/vehicles/vehicle_factory.js, through the {@code WFVehicles} binding); baking
     * defaults here duplicated/overrode those pack recipes, so they were all removed. The {@link #vehicle}
     * helper below is kept for anyone who wants to re-add a built-in example.
     */
    public static void addDefaultRecipes(Consumer<FinishedRecipe> provider) {
        // intentionally empty — all vehicle recipes come from the pack (KubeJS).
    }

    /**
     * One vehicle-factory recipe: {@code customize} adds the material inputs, then the standard EU/duration
     * and the packaged-vehicle output are appended. The recipe id is {@code wfcore:<recipeId>}; the item
     * output encodes {@code entityId} (looked up at spawn time, so this carries no compile-time dependency
     * on the vehicle mod).
     */
    private static void vehicle(Consumer<FinishedRecipe> provider, GTRecipeType type, String recipeId,
                                String entityId, int voltageTier, int duration,
                                UnaryOperator<GTRecipeBuilder> customize) {
        customize.apply(type.recipeBuilder(WFCore.id(recipeId)))
                .EUt(GTValues.VA[voltageTier])
                .duration(duration)
                .outputItems(PackagedVehicleItem.of(new ResourceLocation(entityId)))
                .save(provider);
    }
}
