package com.norwood.wfcore.common.machine.compute;

import net.minecraftforge.fluids.capability.IFluidHandler;

public interface ICooler {

    boolean isLiquid();

    // Passive: how much can you cool right now?
    double getPassiveCoolingRate(double currentTemp, double thermalMass, double ambient);

    // Active: max potential cooling given the shared coolant supply.
    double getMaxActiveCoolingRate(double thermalMass, IFluidHandler coolantIn);

    int getFluidUsagePerTick();

    // The action: actually drain the coolant and return the cooling done.
    double executeActiveCooling(double percentage, double thermalMass, IFluidHandler fluidIn, IFluidHandler fluidOut);
}
