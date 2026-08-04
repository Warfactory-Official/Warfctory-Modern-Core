package com.norwood.wfcore.common.deposit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Runtime registry of {@link DepositType}s. Populated by WFCore's built-in defaults (common setup) and by
 * KubeJS startup scripts ({@code WFDeposits.add(...)}). Read by worldgen (pick a type to place), the deposit
 * block-entity renderer (resolve a texture) and the drilling recipe condition (match by id). Not a Forge
 * registry: types are plain data and may differ between resource reloads.
 */
public final class WFDeposits {

    private static final Map<ResourceLocation, DepositType> REGISTRY = new LinkedHashMap<>();
    private static final List<DepositNode> NODES = new ArrayList<>();
    private static final List<DepositRegion> REGIONS = new ArrayList<>();

    private WFDeposits() {}

    public static DepositType register(DepositType type) {
        REGISTRY.put(type.id(), type);
        return type;
    }

    @Nullable
    public static DepositType get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    @Nullable
    public static DepositType get(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : REGISTRY.get(rl);
    }

    public static void unregister(ResourceLocation id) {
        REGISTRY.remove(id);
    }

    public static Collection<DepositType> all() {
        return REGISTRY.values();
    }

    //////////////////// explicit placements ////////////////////

    public static void registerNode(DepositNode node) {
        NODES.add(node);
    }

    public static void registerRegion(DepositRegion region) {
        REGIONS.add(region);
    }

    public static List<DepositNode> nodesIn(ResourceLocation dimension) {
        return NODES.stream().filter(n -> n.dimension().equals(dimension)).toList();
    }

    public static List<DepositRegion> regionsIn(ResourceLocation dimension) {
        return REGIONS.stream().filter(r -> r.dimension().equals(dimension)).toList();
    }

    public static boolean hasPlacements() {
        return !NODES.isEmpty() || !REGIONS.isEmpty();
    }

    public static int nodeCount() {
        return NODES.size();
    }

    public static int regionCount() {
        return REGIONS.size();
    }

    public static boolean isEmpty() {
        return REGISTRY.isEmpty();
    }

    /**
     * Weighted-random pick among the types that generate in {@code dimension}; {@code null} when none apply.
     */
    @Nullable
    public static DepositType weightedRandomFor(ResourceLocation dimension, RandomSource random) {
        List<DepositType> eligible = REGISTRY.values().stream().filter(t -> t.generatesIn(dimension)).toList();
        if (eligible.isEmpty()) {
            return null;
        }
        int total = 0;
        for (DepositType t : eligible) {
            total += t.weight();
        }
        int roll = random.nextInt(total);
        for (DepositType t : eligible) {
            roll -= t.weight();
            if (roll < 0) {
                return t;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    /**
     * Built-in deposit types so the feature and drill work out of the box. Recipes for these are added in
     * {@link com.norwood.wfcore.common.data.WFRecipeTypes#addDefaultRecipes}. Packs add more via KubeJS.
     */
    public static void registerDefaults() {
        DepositType.builder(new ResourceLocation("wfcore", "iron_deposit"))
                .name("wfcore.deposit.iron")
                .texture("minecraft:block/iron_ore")
                .weight(40)
                .dimension("minecraft:overworld")
                .clusterSize(2, 4)
                .register();

        DepositType.builder(new ResourceLocation("wfcore", "copper_deposit"))
                .name("wfcore.deposit.copper")
                .texture("minecraft:block/copper_ore")
                .weight(40)
                .dimension("minecraft:overworld")
                .clusterSize(2, 4)
                .register();

        DepositType.builder(new ResourceLocation("wfcore", "gold_deposit"))
                .name("wfcore.deposit.gold")
                .texture("minecraft:block/gold_ore")
                .weight(20)
                .dimension("minecraft:overworld")
                .clusterSize(2, 3)
                .register();
    }
}
