package com.norwood.wfcore.integration.kubejs;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonObject;
import com.norwood.wfcore.SuperbOverrides;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.item.PackagedVehicleItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * Open a builder for a Light Ground Vehicle Factory recipe ({@code wfcore:light_ground_vehicle_factory}).
     * 2-arg convenience form; see {@link #recipe(String, String, String)} to target another factory.
     *
     * @param recipeId a recipe id, e.g. {@code "wfcore:mv_truck"}
     * @param entityId the entity to spawn, e.g. {@code "superbwarfare:truck"}
     */
    public VehicleRecipe recipe(String recipeId, String entityId) {
        return recipe(recipeId, entityId, "light_ground_vehicle_factory");
    }

    /**
     * Open a builder for a vehicle-factory recipe on {@code factory}'s map that outputs the packaged vehicle for
     * {@code entityId}. Chain {@code .item/.tag/.fluid/.circuit/.EUt/.duration}, then {@code .build()} and feed
     * the result to {@code event.custom(...)}:
     *
     * <pre>{@code
     * event.custom(WFVehicles.recipe('wfcore:mv_truck', 'superbwarfare:truck')
     *     .item('kubejs:lv_vehicle_frame', 1).item('superbwarfare:wheel', 4)
     *     .tag('#gtceu:circuits/lv', 4).EUt(70).duration(4000).build())
     * }</pre>
     *
     * @param recipeId a recipe id, e.g. {@code "wfcore:mv_truck"}
     * @param entityId the entity to spawn, e.g. {@code "superbwarfare:truck"}
     * @param factory  which factory map: {@code light_ground_vehicle_factory}, {@code tank_assembly},
     *                 {@code light_plane_assembler}, {@code heavy_plane_assembler}, {@code heavy_vehicle_depot}
     */
    public VehicleRecipe recipe(String recipeId, String entityId, String factory) {
        ResourceLocation rid = ResourceLocation.tryParse(recipeId);
        if (rid == null) {
            throw new IllegalArgumentException("Invalid recipe id: '" + recipeId + "'");
        }
        ResourceLocation eid = ResourceLocation.tryParse(entityId);
        if (eid == null || !ForgeRegistries.ENTITY_TYPES.containsKey(eid)) {
            throw new IllegalArgumentException(
                    "Unknown entity '" + entityId + "' - is the mod that provides it (e.g. Superb Warfare) loaded?");
        }
        // Resolve the factory now so a bad name fails fast with the valid-factories list.
        VehicleFactoryRecipes.byName(factory);
        return new VehicleRecipe(rid, eid, factory);
    }

    /**
     * Fluent, Rhino-safe builder for a vehicle-factory recipe. Inputs are accumulated as plain strings/ints (so
     * the script never touches the ambiguous {@code ItemStack[]} vs {@code Ingredient[]} overloads that break
     * GTCEu's generic KubeJS builder on this Rhino); {@link #build()} assembles the recipe in Java via
     * {@link GTRecipeBuilder} and serializes it to the exact JSON GTCEu emits, for {@code event.custom(...)}.
     */
    public static final class VehicleRecipe {

        private final ResourceLocation rid;
        private final ResourceLocation eid;
        private final String factory;
        private final List<ItemStack> items = new ArrayList<>();
        private final List<TagKey<Item>> itemTags = new ArrayList<>();
        private final List<Integer> itemTagCounts = new ArrayList<>();
        private final List<FluidStack> fluids = new ArrayList<>();
        private int circuit = -1;
        private long eut = GTValues.VA[GTValues.MV];
        private int duration = 100;

        private VehicleRecipe(ResourceLocation rid, ResourceLocation eid, String factory) {
            this.rid = rid;
            this.eid = eid;
            this.factory = factory;
        }

        /** Add {@code count} of item {@code id} (e.g. {@code 'kubejs:lv_engine'}) as an input. */
        public VehicleRecipe item(String id, int count) {
            ResourceLocation r = ResourceLocation.tryParse(id);
            Item it = r == null ? null : ForgeRegistries.ITEMS.getValue(r);
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

        /** Add {@code count} of any item in tag {@code tag} (leading {@code #} optional), e.g. {@code '#gtceu:circuits/lv'}. */
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

        /** Add {@code millibuckets} of fluid {@code id}, e.g. {@code fluid('gtceu:lubricant', 500)}. */
        public VehicleRecipe fluid(String id, int millibuckets) {
            ResourceLocation r = ResourceLocation.tryParse(id);
            Fluid f = (r == null) ? null : ForgeRegistries.FLUIDS.getValue(r);
            if (f == null || millibuckets <= 0) {
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

        /** Assemble + serialize to the recipe JSON GTCEu emits; feed the result to {@code event.custom(...)}. */
        public JsonObject build() {
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
            b.outputItems(PackagedVehicleItem.of(eid)).EUt(eut).duration(duration);
            JsonObject[] json = new JsonObject[1];
            b.save(fr -> json[0] = fr.serializeRecipe());
            return json[0];
        }
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
        private TagKey<Item> storageFilter;
        private final Map<ResourceLocation, Float> fluids = new LinkedHashMap<>();

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

        /**
         * Accept {@code fluidId} as fuel at the given energy multiplier per mB. Ignored if the id is malformed or
         * ratio <= 0.
         *
         * <p>
         * The fluid id is stored as-is (not resolved to a {@link Fluid}); it is resolved at use-time during
         * gameplay. This binding is called from a KubeJS <em>startup</em> script, which runs before other mods'
         * fluids are registered — resolving here would return null and silently drop every fuel (e.g. all of
         * {@code gtceu:*}). We only sanity-check the id format, not that the fluid currently exists.
         */
        public OverrideBuilder fuel(String fluidId, double ratio) {
            ResourceLocation rl = fluidId == null ? null : ResourceLocation.tryParse(fluidId);
            if (rl == null) {
                WFCore.LOGGER.warn("WFVehicles.override({}): ignoring malformed fuel fluid id '{}'", vehicleId, fluidId);
            } else if (ratio > 0) {
                fluids.put(rl, (float) ratio);
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

        /**
         * Restrict the vehicle's storage to items in item tag {@code tagId} (leading {@code #} optional), e.g.
         * {@code '#wfcore:drone_upgrades'}. Only meaningful alongside {@link #storage}. The tag is stored as a
         * {@link TagKey} and resolved lazily at use-time, so it need not exist yet at startup.
         */
        public OverrideBuilder storageFilter(String tagId) {
            String t = (tagId != null && tagId.startsWith("#")) ? tagId.substring(1) : tagId;
            ResourceLocation r = t == null ? null : ResourceLocation.tryParse(t);
            if (r == null) {
                WFCore.LOGGER.warn("WFVehicles.override({}): ignoring malformed storage filter tag '{}'",
                        vehicleId, tagId);
            } else {
                this.storageFilter = TagKey.create(Registries.ITEM, r);
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
                    new SuperbOverrides.OverrideData(maxFuel, fluids, storageSize, storageColumns, storageFilter));
        }
    }
}
