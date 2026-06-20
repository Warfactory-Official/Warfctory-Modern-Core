package com.norwood.wfcore.api.capability;

import net.minecraft.core.Direction;

/**
 * WFCore's "AC EU" energy capability - a separate energy type from GregTech EU. It is carried only by AC
 * cables and the Large Transformer's AC converter hatches; machines do not expose it, so AC power cannot be
 * used directly. AC transfer is lossless, has no amperage, and is capped only by a flat per-cable throughput.
 */
public interface IACEnergyContainer {

    IACEnergyContainer DEFAULT = new IACEnergyContainer() {

        @Override
        public long acceptEnergy(Direction side, long amount) {
            return 0;
        }

        @Override
        public long getThroughput() {
            return 0;
        }
    };

    /** Push AC EU into this container/network from the given side. Returns the amount actually accepted. */
    long acceptEnergy(Direction side, long amount);

    /** Flat EU/t this endpoint (or cable run) can carry. */
    long getThroughput();

    default boolean inputsAC(Direction side) {
        return false;
    }

    default boolean outputsAC(Direction side) {
        return false;
    }
}
