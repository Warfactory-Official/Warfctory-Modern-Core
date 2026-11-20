package com.norwood.wfcore.common.worldgen.resource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Optional;

/** Which generator a {@link ResourceAllocation.Rule} reweights; {@link #id} is its JSON name. */
public enum ResourceSystem {

    /** Ambient deposit scatter; target = deposit type id. */
    DEPOSIT("wfcore:deposit"),
    /** GT ore vein pick per grid cell and layer; target = {@code gtceu:ore_vein} registry key. */
    ORE_VEIN("gtceu:ore_vein"),
    /** GT bedrock fluid pick per 8x8-chunk vein; target = {@code gtceu:bedrock_fluid} registry key. */
    BEDROCK_FLUID("gtceu:bedrock_fluid"),
    /** GT bedrock ore pick per vein origin chunk; target = {@code gtceu:bedrock_ore} registry key. */
    BEDROCK_ORE("gtceu:bedrock_ore"),
    /** Any placed feature: multiplier = expected placements per chunk; target = placed feature id. */
    PLACED_FEATURE("minecraft:placed_feature"),
    /** WarForge claim vein roll; target = vein translation key. */
    WARFORGE_VEIN("warforge:vein"),
    /** WFLib anomaly sites: multiplier on a tier's stock site rate; target = {@code wflib:anomaly_site_tier_<1-4>}. */
    ANOMALY_SITE("wflib:anomaly_site");

    public static final Codec<ResourceSystem> CODEC = Codec.STRING.comapFlatMap(
            id -> byId(id).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "unknown resource system " + id)),
            s -> s.id);

    public final String id;

    ResourceSystem(String id) {
        this.id = id;
    }

    public static Optional<ResourceSystem> byId(String id) {
        return Arrays.stream(values()).filter(s -> s.id.equals(id)).findFirst();
    }

    /** One target per cell; placed features assign independently. */
    public boolean exclusive() {
        return this != PLACED_FEATURE;
    }
}
