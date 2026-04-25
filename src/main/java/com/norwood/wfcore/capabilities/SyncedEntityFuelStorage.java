package com.norwood.wfcore.capabilities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

public class SyncedEntityFuelStorage extends FluidTank {
    public SyncedEntityFuelStorage(int capacity) {
        super(capacity);
    }

    protected SynchedEntityData entityData;
    protected EntityDataAccessor<FluidStack> fluidDataAccessor;

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        int filled = super.fill(resource, action);
        if (action.execute() && filled > 0) {
            entityData.set(fluidDataAccessor, this.fluid);
        }
        return filled;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack drained = super.drain(maxDrain, action);
        if (action.execute() && !drained.isEmpty()) {
            entityData.set(fluidDataAccessor, this.fluid);
        }
        return drained;
    }

    @Override
    public @NotNull FluidStack getFluid() {
        return entityData.get(fluidDataAccessor);
    }

    @Override
    public void setFluid(FluidStack stack) {
        this.fluid = stack;
        entityData.set(fluidDataAccessor, stack);
    }



}
