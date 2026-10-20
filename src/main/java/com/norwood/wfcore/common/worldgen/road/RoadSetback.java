package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;

import com.norwood.wfcore.WFCore;

/**
 * Neighbour chunks decorate after this chunk's road and write up to 16 blocks into it => features in these tags
 * are refused near a road. Worldgen only: saplings and bone meal grow anywhere.
 */
public final class RoadSetback {

    /** Trees, huge mushrooms / fungi: canopy radius, trunk height. */
    public static final TagKey<Feature<?>> CANOPY = TagKey.create(Registries.FEATURE, WFCore.id("road_setback/canopy"));
    /** {@code LakeFeature}: 16 x 8 x 16 box centred on origin, lowered 4. */
    public static final TagKey<Feature<?>> POOL = TagKey.create(Registries.FEATURE, WFCore.id("road_setback/pool"));

    private static final double CANOPY_REACH = 5.0;
    private static final int CANOPY_HEIGHT = 32;
    /** Box corner: 8 * sqrt(2). */
    private static final double POOL_REACH = 11.5;
    private static final int POOL_DEPTH = 4;

    private RoadSetback() {}

    public static boolean refuses(WorldGenLevel level, Feature<?> feature, BlockPos origin) {
        if (!(level instanceof WorldGenRegion)) {
            return false;
        }
        Holder<Feature<?>> holder = BuiltInRegistries.FEATURE.wrapAsHolder(feature);
        int y = origin.getY();
        double reach;
        int minY;
        int maxY;
        if (holder.is(CANOPY)) {
            reach = CANOPY_REACH;
            // Origin up to sweep height over the deck still hangs leaves into it.
            minY = y - RoadRasterizer.CANOPY_REACH;
            maxY = y + CANOPY_HEIGHT;
        } else if (holder.is(POOL)) {
            reach = POOL_REACH;
            minY = y - POOL_DEPTH;
            maxY = y + POOL_DEPTH;
        } else {
            return false;
        }
        RoadIndex index = RoadIndexManager.get(level.getLevel());
        return !index.isEmpty() && index.near(origin.getX(), origin.getZ(), reach, minY, maxY);
    }
}
