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
        public long acceptEnergy(Direction side, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long getThroughput() {
            return 0;
        }
    };

    /**
     * Push AC EU into this container/network from the given side. Returns the amount actually accepted. When
     * {@code simulate} is true no state changes - used to size how much to convert (and therefore how much
     * coolant to spend) before committing, so coolant is never spent on energy the network can't carry.
     */
    long acceptEnergy(Direction side, long amount, boolean simulate);

    /** Convenience: commit a push (non-simulated). */
    default long acceptEnergy(Direction side, long amount) {
        return acceptEnergy(side, amount, false);
    }

    /** Flat EU/t this endpoint (or cable run) can carry. */
    long getThroughput();

    /**
     * Whether pushing AC from this side currently reaches a live AC input endpoint, <b>regardless of whether
     * that endpoint has any free space right now</b>. Lets a pusher tell "nothing is connected" apart from
     * "connected but the receiver's buffer is full" (which {@link #acceptEnergy} can't, since both give 0).
     */
    default boolean hasDestination(Direction side) {
        return false;
    }

    default boolean inputsAC(Direction side) {
        return false;
    }

    default boolean outputsAC(Direction side) {
        return false;
    }
}
