package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;

/**
 * WFCore custom multiblock part abilities. Registered onto their blocks automatically by the machine
 * builder's {@code .abilities(...)} call, so {@code Predicates.abilities(WFPartAbility.X)} can match them.
 */
public final class WFPartAbility {

    // Large Transformer AC converter hatches (one of each per transformer)
    public static final PartAbility AC_INPUT = new PartAbility("ac_input");
    public static final PartAbility AC_OUTPUT = new PartAbility("ac_output");

    // Computation mainframe components
    public static final PartAbility GPC_CPU_SLOT = new PartAbility("gpc_cpu_slot");
    public static final PartAbility GPC_RAM_SLOT = new PartAbility("gpc_ram_slot");
    public static final PartAbility GPC_COOLER = new PartAbility("gpc_cooler");

    private WFPartAbility() {}
}
