package com.norwood.wfcore.common.machine;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleFactoryOverlayTracker {

    private static final Set<BlockPos> POSITIONS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private VehicleFactoryOverlayTracker() {}

    public static void add(BlockPos pos) {
        POSITIONS.add(pos.immutable());
    }

    public static void remove(BlockPos pos) {
        POSITIONS.remove(pos);
    }

    public static Set<BlockPos> positions() {
        return POSITIONS;
    }
}
