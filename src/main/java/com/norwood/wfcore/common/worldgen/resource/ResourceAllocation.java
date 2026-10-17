package com.norwood.wfcore.common.worldgen.resource;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.norwood.wfcore.WFCore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where resources belong, as authored in {@code worldgen-workspace} and written to
 * {@code data/<ns>/wfcore/resource_allocation/<id>.json}. Server-only datapack registry: frozen with the level's
 * registry access => safe from generator threads.
 *
 * <p>Rule density at a point: {@code m = base}; per layer in order {@code m = lerp(m, weight, zone(x, z))}; richness
 * likewise from {@code richness} over layers that set one. Weight rules on one target multiply; assign rules and
 * paint decide system cells before the stock roll (paint > assign > weight).
 */
public record ResourceAllocation(ResourceKey<Level> dimension, Map<String, Zone> zones, List<Rule> rules,
        List<DepositNodeSpec> depositNodes, List<DepositRegionSpec> depositRegions, List<Paint> paint,
        Map<String, Integer> grids) {

    public static final ResourceKey<Registry<ResourceAllocation>> REGISTRY =
            ResourceKey.createRegistryKey(WFCore.id("resource_allocation"));

    public static final Codec<ResourceAllocation> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension", Level.OVERWORLD)
                    .forGetter(ResourceAllocation::dimension),
            Zone.TABLE.optionalFieldOf("zones", Map.of()).forGetter(ResourceAllocation::zones),
            Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(ResourceAllocation::rules),
            DepositNodeSpec.CODEC.listOf().optionalFieldOf("deposit_nodes", List.of())
                    .forGetter(ResourceAllocation::depositNodes),
            DepositRegionSpec.CODEC.listOf().optionalFieldOf("deposit_regions", List.of())
                    .forGetter(ResourceAllocation::depositRegions),
            Paint.CODEC.listOf().optionalFieldOf("paint", List.of()).forGetter(ResourceAllocation::paint),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("grids", Map.of())
                    .forGetter(ResourceAllocation::grids))
            .apply(i, ResourceAllocation::new));

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY, CODEC);
    }

    /** {@code richness} absent => layer leaves richness alone. */
    public record Layer(String zone, double weight, Optional<Double> richness) {

        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("zone").forGetter(Layer::zone),
                Codec.DOUBLE.fieldOf("weight").forGetter(Layer::weight),
                Codec.DOUBLE.optionalFieldOf("richness").forGetter(Layer::richness))
                .apply(i, Layer::new));
    }

    /**
     * {@code assign}: density = probability a system cell is held by this rule (earlier rules first); target by
     * cumulative {@code shares} against the {@code select} zone's value, or a hash when absent. Target {@code *}
     * (weight only) = every target of the system.
     */
    public record Rule(ResourceSystem system, List<String> targets, boolean assign, double base, double richness,
            List<Layer> layers, Optional<List<Double>> shares, Optional<String> select) {

        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceSystem.CODEC.fieldOf("system").forGetter(Rule::system),
                Codec.STRING.listOf().fieldOf("targets").forGetter(Rule::targets),
                Codec.STRING.validate(m -> m.equals("weight") || m.equals("assign") ? DataResult.success(m)
                        : DataResult.error(() -> "mode " + m + ", expected weight or assign"))
                        .optionalFieldOf("mode", "weight").forGetter(r -> r.assign() ? "assign" : "weight"),
                Codec.DOUBLE.optionalFieldOf("base", 1.0).forGetter(Rule::base),
                Codec.DOUBLE.optionalFieldOf("richness", 1.0).forGetter(Rule::richness),
                Layer.CODEC.listOf().optionalFieldOf("layers", List.of()).forGetter(Rule::layers),
                Codec.DOUBLE.listOf().optionalFieldOf("shares").forGetter(Rule::shares),
                Codec.STRING.optionalFieldOf("select").forGetter(Rule::select))
                .apply(i, (system, targets, mode, base, richness, layers, shares, select) -> new Rule(system,
                        targets, mode.equals("assign"), base, richness, layers, shares, select)));
    }

    /** Hand-assigned chunks; target "" => cell forced empty. Exclusive systems: one paint per cell, last wins. */
    public record Paint(ResourceSystem system, String target, double richness, List<List<Integer>> chunks) {

        public static final Codec<Paint> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceSystem.CODEC.fieldOf("system").forGetter(Paint::system),
                Codec.STRING.optionalFieldOf("target", "").forGetter(Paint::target),
                Codec.DOUBLE.optionalFieldOf("richness", 1.0).forGetter(Paint::richness),
                Codec.INT.listOf(2, 2).listOf().fieldOf("chunks").forGetter(Paint::chunks))
                .apply(i, Paint::new));
    }

    /** Forced cluster; {@code size}/{@code yield} absent => rolled from the deposit type. */
    public record DepositNodeSpec(ResourceLocation type, int x, int z, Optional<Integer> size,
            Optional<Integer> yield) {

        public static final Codec<DepositNodeSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("type").forGetter(DepositNodeSpec::type),
                Codec.INT.fieldOf("x").forGetter(DepositNodeSpec::x),
                Codec.INT.fieldOf("z").forGetter(DepositNodeSpec::z),
                Codec.INT.optionalFieldOf("size").forGetter(DepositNodeSpec::size),
                Codec.INT.optionalFieldOf("yield").forGetter(DepositNodeSpec::yield))
                .apply(i, DepositNodeSpec::new));
    }

    /** Exactly {@code count} clusters spread over the rectangle. */
    public record DepositRegionSpec(ResourceLocation type, int minX, int minZ, int maxX, int maxZ, int count,
            Optional<Integer> minSize, Optional<Integer> maxSize, Optional<Integer> yield) {

        public static final Codec<DepositRegionSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("type").forGetter(DepositRegionSpec::type),
                Codec.INT.fieldOf("min_x").forGetter(DepositRegionSpec::minX),
                Codec.INT.fieldOf("min_z").forGetter(DepositRegionSpec::minZ),
                Codec.INT.fieldOf("max_x").forGetter(DepositRegionSpec::maxX),
                Codec.INT.fieldOf("max_z").forGetter(DepositRegionSpec::maxZ),
                Codec.INT.fieldOf("count").forGetter(DepositRegionSpec::count),
                Codec.INT.optionalFieldOf("min_size").forGetter(DepositRegionSpec::minSize),
                Codec.INT.optionalFieldOf("max_size").forGetter(DepositRegionSpec::maxSize),
                Codec.INT.optionalFieldOf("yield").forGetter(DepositRegionSpec::yield))
                .apply(i, DepositRegionSpec::new));
    }
}
