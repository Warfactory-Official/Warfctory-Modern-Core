package com.norwood.wfcore.integration.kubejs;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.integration.kjs.recipe.GTRecipeSchema;

import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.util.KubeResourceLocation;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.Context;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.item.PackagedVehicleItem;
import com.norwood.wfcore.common.recipe.condition.ResearchRecipeCondition;
import com.norwood.wfcore.integration.combinedassault.CombinedAssaultVehicles;
import com.norwood.wfcore.integration.combinedassault.VehicleOverrides;

import java.util.ArrayList;
import java.util.List;

/**
 * KubeJS binding exposed as {@code WFVehicles}: vehicle-factory recipes ({@link #recipe}) and CombinedAssault
 * {@code energy_info} overrides ({@link #override}).
 *
 * <p>
 * GTCEu's generic KubeJS recipe builder is unusable for this recipe on this pack's Rhino: every
 * {@code inputItems}/{@code outputItems} call is reported "ambiguous" (Rhino can't rank the exact overload
 * above the permissive {@code ItemStack[]}/{@code InputItem[]} varargs), so the recipe serialized empty.
 *
 * <p>
 * So we build the recipe entirely in Java via GTCEu's own {@link GTRecipeBuilder} (overloads resolve at
 * compile time), serialize it to the exact recipe JSON GTCEu writes for data packs, and let the script feed
 * that to {@code event.custom(...)} — the one item-recipe entry point that is NOT overloaded.
 *
 * <pre>{@code
 * // server_scripts:
 * ServerEvents.recipes(event => {
 *     // 2-arg form defaults to the Light Ground Vehicle Factory map:
 *     event.custom(WFVehicles.recipe('wfcore:test_truck', 'combinedassault:lav150').build())
 *     // 3-arg form picks which factory map the recipe belongs to:
 *     WFVehicles.recipe('wfcore:test_tank', 'combinedassault:t72', 'tank_assembly')
 *         .item('gtceu:steel_plate', 8).EUt(120).duration(600).add(event)
 * })
 * }</pre>
 *
 * <p>
 * The second argument is a <b>CombinedAssault vehicle id</b>, not an entity type: CombinedAssault vehicles are
 * data-driven entries resolved through {@link CombinedAssaultVehicles}. The third is the factory's machine path —
 * one of {@code light_ground_vehicle_factory}, {@code tank_assembly}, {@code light_plane_assembler},
 * {@code heavy_plane_assembler}, {@code heavy_vehicle_depot}, {@code helicopter_assembler},
 * {@code naval_vehicle_deployer}.
 */
public class WFVehicleBindings {

    /** Whether {@code vehicleId} is a CombinedAssault vehicle that is currently loaded. */
    public boolean known(String vehicleId) {
        return CombinedAssaultVehicles.isKnown(ResourceLocation.tryParse(vehicleId));
    }

    /** Every currently-loaded CombinedAssault vehicle id, sorted — the roster a pack can write recipes against. */
    public List<String> roster() {
        return CombinedAssaultVehicles.roster();
    }

    /**
     * Open an {@code energy_info} override for CombinedAssault vehicle {@code vehicleId}, or {@code "*"} for every
     * fuel vehicle. startup_scripts only: both sides must apply it before content loads.
     *
     * <pre>{@code
     * WFVehicles.override('*').fuel('gtceu:diesel', 1.0).fuel('#combinedassault:fuel', 1.0).register()
     * WFVehicles.override('combinedassault:m1a2').tank(1600).register()
     * WFVehicles.override('combinedassault:mavic').electric(20, 2048).register()
     * }</pre>
     */
    public VehicleEnergy override(String vehicleId) {
        if (!vehicleId.equals(VehicleOverrides.ALL) && ResourceLocation.tryParse(vehicleId) == null) {
            throw new IllegalArgumentException("Invalid vehicle id: '" + vehicleId + "'");
        }
        return new VehicleEnergy(vehicleId);
    }

    /** {@code energy_info} fields set over the vehicle's def; unset fields keep the def's. */
    public static final class VehicleEnergy {

        private final String vehicle;
        private final JsonObject info = new JsonObject();
        private JsonArray fuels;

        private VehicleEnergy(String vehicle) {
            this.vehicle = vehicle;
        }

        /** Tank size, mB. */
        public VehicleEnergy tank(int mb) {
            info.addProperty("tank_capacity", mb);
            return this;
        }

        /** Burn at full power, energy units (mB at efficiency 1) per tick. */
        public VehicleEnergy consumption(double perTick) {
            info.addProperty("energy_consumption_per_tick", perTick);
            return this;
        }

        /** Accepted fluid id or {@code #tag}, matched in call order. First call replaces the def's list. */
        public VehicleEnergy fuel(String fluid, double efficiency) {
            if (fuels == null) {
                fuels = new JsonArray();
                info.add("fuels", fuels);
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("fluid", fluid);
            entry.addProperty("efficiency", efficiency);
            fuels.add(entry);
            return this;
        }

        /** Battery instead of tank: {@code fePerUnit} FE per energy unit, charge capped at {@code chargeRate} FE/t. */
        public VehicleEnergy electric(int fePerUnit, int chargeRate) {
            info.addProperty("energy_type", "electric");
            info.addProperty("fe_per_unit", fePerUnit);
            info.addProperty("charge_rate", chargeRate);
            info.add("fuels", JsonNull.INSTANCE);
            fuels = null;
            return this;
        }

        public void register() {
            VehicleOverrides.add(vehicle, info);
        }
    }

    /** Open a builder for a Light Ground Vehicle Factory recipe. */
    public VehicleRecipe recipe(String recipeId, String vehicleId) {
        return recipe(recipeId, vehicleId, "light_ground_vehicle_factory");
    }

    /**
     * Open a builder for a vehicle-factory recipe on {@code factory}'s map that outputs the packaged vehicle
     * for {@code vehicleId}.
     *
     * @param recipeId  a recipe id, e.g. {@code "wfcore:mv_truck"}
     * @param vehicleId the CombinedAssault vehicle to spawn, e.g. {@code "combinedassault:lav150"}
     * @param factory   which factory map the recipe belongs to
     */
    public VehicleRecipe recipe(String recipeId, String vehicleId, String factory) {
        ResourceLocation rid = ResourceLocation.tryParse(recipeId);
        if (rid == null) {
            throw new IllegalArgumentException("Invalid recipe id: '" + recipeId + "'");
        }
        ResourceLocation vid = ResourceLocation.tryParse(vehicleId);
        if (vid == null) {
            throw new IllegalArgumentException("Invalid vehicle id: '" + vehicleId + "'");
        }
        // An empty roster means CombinedAssault's reload listener has not run yet, not "no such vehicle" — it
        // usually loses the race with recipe scripts, so only warn once there is something to compare to.
        if (!CombinedAssaultVehicles.roster().isEmpty() && !CombinedAssaultVehicles.isKnown(vid)) {
            WFCore.LOGGER.warn("[WFVehicles] {}: no CombinedAssault vehicle '{}'; the factory will discard this "
                    + "recipe's output. Loaded vehicles: {}", rid, vehicleId, CombinedAssaultVehicles.roster());
        }
        // Resolve the factory now so a bad name fails fast with the valid-factories list.
        VehicleFactoryRecipes.byName(factory);
        return new VehicleRecipe(rid, vid, factory);
    }

    /**
     * Fluent, Rhino-safe builder for a vehicle-factory recipe. Inputs are accumulated as plain strings/ints,
     * then {@link #build(Context)} assembles the recipe in Java and serializes it to the JSON GTCEu emits.
     */
    public static final class VehicleRecipe {

        private final ResourceLocation rid;
        private final ResourceLocation vid;
        private final String factory;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<TagKey<Item>> itemTags = new ArrayList<>();
        private final List<Integer> itemTagCounts = new ArrayList<>();
        private final List<FluidStack> fluids = new ArrayList<>();
        private int circuit = -1;
        private long eut = GTValues.VA[GTValues.MV];
        private int duration = 100;
        private String research = null;

        private VehicleRecipe(ResourceLocation rid, ResourceLocation vid, String factory) {
            this.rid = rid;
            this.vid = vid;
            this.factory = factory;
        }

        /** Add {@code count} of item {@code id} as an input. */
        public VehicleRecipe item(String id, int count) {
            ResourceLocation r = ResourceLocation.tryParse(id);
            Item it = r == null ? null : BuiltInRegistries.ITEM.get(r);
            if (it == null || it == Items.AIR) {
                throw new IllegalArgumentException("Unknown item '" + id + "'");
            }
            items.add(new ItemStack(it, Math.max(1, count)));
            return this;
        }

        /** Add one of item {@code id}. */
        public VehicleRecipe item(String id) {
            return item(id, 1);
        }

        /** Add {@code count} of any item in tag {@code tag} (leading {@code #} optional). */
        public VehicleRecipe tag(String tag, int count) {
            String t = (tag != null && tag.startsWith("#")) ? tag.substring(1) : tag;
            ResourceLocation r = (t == null) ? null : ResourceLocation.tryParse(t);
            if (r == null) {
                throw new IllegalArgumentException("Invalid item tag '" + tag + "'");
            }
            itemTags.add(TagKey.create(Registries.ITEM, r));
            itemTagCounts.add(Math.max(1, count));
            return this;
        }

        /** Add {@code millibuckets} of fluid {@code id}. */
        public VehicleRecipe fluid(String id, int millibuckets) {
            ResourceLocation r = ResourceLocation.tryParse(id);
            Fluid f = (r == null) ? null : BuiltInRegistries.FLUID.get(r);
            if (f == null || f == Fluids.EMPTY || millibuckets <= 0) {
                throw new IllegalArgumentException("Unknown fluid or non-positive amount: '" + id + "' x" + millibuckets);
            }
            fluids.add(new FluidStack(f, millibuckets));
            return this;
        }

        /** Set the programmed (ghost) selector circuit. NB: it occupies one of the machine's item-input slots. */
        public VehicleRecipe circuit(int n) {
            this.circuit = n;
            return this;
        }

        /** Recipe EU/t (raw, e.g. {@code 70}); determines the required machine tier. */
        public VehicleRecipe EUt(long v) {
            if (v > 0) {
                this.eut = v;
            }
            return this;
        }

        /** Recipe duration in ticks. */
        public VehicleRecipe duration(int ticks) {
            if (ticks > 0) {
                this.duration = ticks;
            }
            return this;
        }

        /** Gate the recipe behind a research entry. */
        public VehicleRecipe research(String id) {
            this.research = id;
            return this;
        }

        /** Populate a fresh GTRecipeBuilder for this factory with all the accumulated inputs/output/EU/duration. */
        private GTRecipeBuilder toBuilder() {
            GTRecipeBuilder b = VehicleFactoryRecipes.byName(factory).recipeBuilder(rid);
            for (ItemStack s : items) {
                b.inputItems(s);
            }
            for (int i = 0; i < itemTags.size(); i++) {
                b.inputItems(itemTags.get(i), itemTagCounts.get(i));
            }
            for (FluidStack fs : fluids) {
                b.inputFluids(fs);
            }
            if (circuit >= 0) {
                b.circuitMeta(circuit);
            }
            if (research != null && !research.isBlank()) {
                b.addCondition(new ResearchRecipeCondition(research));
            }
            b.outputItems(PackagedVehicleItem.of(vid)).EUt(eut).duration(duration);
            return b;
        }

        /**
         * The recipe as GTCEu's data-pack JSON, for {@code event.custom(...)}. Rhino injects the
         * {@link Context}, so scripts call this as a no-arg {@code .build()}.
         */
        public JsonObject build(Context cx) {
            JsonObject json = new JsonObject();
            toBuilder().toJson(RegistryAccessContainer.of(cx).access(), json);
            return json;
        }

        /** Build the recipe and add it to {@code event} in one step. */
        public KubeRecipe add(Context cx, RecipesKubeEvent event) {
            KubeRecipe added = event.custom(cx, build(cx));
            if (added instanceof GTRecipeSchema.GTKubeRecipe gt) {
                // GTRecipeSerializer.CODEC carries no id (a datapack recipe's id is its file path), so
                // without this the GTRecipe reaches GT's lookup null-id and the reload dies in hashCode().
                gt.id(KubeResourceLocation.wrap(rid));
                // Re-apply the tick-input EU (voltage=eut, amperage=1) that the schema parse drops.
                gt.EUt(eut, 1L);
                WFCore.LOGGER.info("[WFVehicles] {} @ {} (vehicle {}): registered EUt={} dur={}",
                        rid, factory, vid, eut, duration);
            } else {
                WFCore.LOGGER.warn("[WFVehicles] {} @ {} (vehicle {}): event.custom returned {} (not a "
                        + "GTKubeRecipe) - could NOT re-apply EU; this recipe will run with no power",
                        rid, factory, vid, added == null ? "null" : added.getClass().getName());
            }
            return added;
        }
    }
}
