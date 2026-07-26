package com.norwood.wfcore.capabilities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.registries.ForgeRegistries;

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
            // Force the sync (3rd arg) and pass a copy. FluidTank mutates `this.fluid` in place and
            // FluidStack#equals ignores the amount, so the plain SynchedEntityData#set(key, value) sees
            // every post-fill drain as "unchanged" and never re-broadcasts it — freezing the client gauge.
            entityData.set(fluidDataAccessor, this.fluid.copy(), true);
        }
        return filled;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        FluidStack drained = super.drain(maxDrain, action);
        if (action.execute() && !drained.isEmpty() && entityData != null && fluidDataAccessor != null) {
            entityData.set(fluidDataAccessor, this.fluid.copy(), true);
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

    // FluidTank#getFluidAmount and #getFluidInTank read the raw `fluid` field directly, bypassing the synced
    // getFluid() above. On the client that field is never updated (only the server drains), so the energy
    // gauge — which is getEnergy() -> getFluidAmount() * ratio — would stay full while the server is empty.
    // Delegate both to getFluid() so every read comes from the synced value.
    @Override
    public int getFluidAmount() {
        return getFluid().getAmount();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return getFluid();
    }

    @Override
    public void setFluid(FluidStack stack) {
        super.setFluid(stack);
        if (entityData != null && fluidDataAccessor != null) {
            entityData.set(fluidDataAccessor, stack.copy(), true);
        }
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        if (stack.isEmpty()) return false;

        var key = ForgeRegistries.ENTITY_TYPES.getKey(vehicleEntity.getType());
        if (key == null) {
            return false;
        }
        var override = SuperbOverrides.getOverride(key.toString());
        if (override == null) {
            return false;
        }
        // The override maps fuel by registry id (see OverrideData) — resolve the incoming fluid to its id.
        var fluidId = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        return fluidId != null && override.fluidConsumptionMap().containsKey(fluidId);
    }
}
