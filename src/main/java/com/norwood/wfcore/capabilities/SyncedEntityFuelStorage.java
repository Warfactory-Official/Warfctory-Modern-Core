package com.norwood.wfcore.capabilities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.SuperbOverrides;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SyncedEntityFuelStorage extends FluidTank {

    public SyncedEntityFuelStorage(int capacity, VehicleEntity vehicle,
                                   EntityDataAccessor<FluidStack> fluidDataAccessor) {
        super(capacity);
        this.vehicleEntity = vehicle;
        this.entityData = vehicle.getEntityData();
        this.fluidDataAccessor = fluidDataAccessor;
    }

    @Nullable
    protected SynchedEntityData entityData;
    protected final VehicleEntity vehicleEntity;
    @Nullable
    protected final EntityDataAccessor<FluidStack> fluidDataAccessor;

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        int filled = super.fill(resource, action);
        if (action.execute() && filled > 0 && entityData != null && fluidDataAccessor != null) {
            entityData.set(fluidDataAccessor, this.fluid);
        }
        return filled;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack drained = super.drain(maxDrain, action);
        if (action.execute() && !drained.isEmpty() && entityData != null && fluidDataAccessor != null) {
            entityData.set(fluidDataAccessor, this.fluid);
        }
        return drained;
    }

    @Override
    public @NotNull FluidStack getFluid() {
        if (entityData != null && fluidDataAccessor != null) {
            return entityData.get(fluidDataAccessor);
        }
        return this.fluid;
    }

    @Override
    public void setFluid(FluidStack stack) {
        super.setFluid(stack);
        if (entityData != null && fluidDataAccessor != null) {
            entityData.set(fluidDataAccessor, stack);
        }
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        if (stack.isEmpty()) return false;
        var data = vehicleEntity.computed();
        if (data == null) {
            return false;
        }
        String id = data.getId();
        var override = SuperbOverrides.getOverride(id);

        if (override != null) {
            return override.fluidConsumptionMap().containsKey(stack.getFluid());
        }

        return false;
    }
}
