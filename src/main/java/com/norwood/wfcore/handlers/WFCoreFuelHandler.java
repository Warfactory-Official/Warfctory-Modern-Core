package com.norwood.wfcore.handlers;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.IVehicleFuelTank;
import com.norwood.wfcore.SuperbFuelOverride;
import com.norwood.wfcore.mixin.VehicleEntitySetEnergyInvoker;

public class WFCoreFuelHandler {

    public static void handleVehicleRefueling(VehicleEntity instance, String id) {
        var override = SuperbFuelOverride.getOverride(id);
        if (override != null) {
            var fuelTank = ((IVehicleFuelTank) instance).getFluidTank();
            if (fuelTank == null) {
                return;
            }

            for (int i = 0; i < instance.getContainerSize(); i++) {
                ItemStack stack = instance.getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }

                int needed = fuelTank.getCapacity() - fuelTank.getFluidAmount();
                if (needed <= 0) {
                    break;
                }

                var fluidHandlerLazy = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
                if (!fluidHandlerLazy.isPresent()) {
                    continue;
                }

                int slotIndex = i;
                fluidHandlerLazy.ifPresent(handler -> {
                    FluidStack drainedSim = handler.drain(needed, IFluidHandler.FluidAction.SIMULATE);
                    if (drainedSim.isEmpty()) {
                        return;
                    }

                    int filled = fuelTank.fill(drainedSim, IFluidHandler.FluidAction.EXECUTE);
                    if (filled <= 0) {
                        return;
                    }

                    handler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                    instance.setItem(slotIndex, handler.getContainer());
                });
            }
            return;
        }

        if (!instance.hasEnergyStorage()) {
            return;
        }

        for (var stack : instance.getItemStacks()) {
            int neededEnergy = instance.getMaxEnergy() - instance.getEnergy();
            if (neededEnergy <= 0) {
                break;
            }

            var energyCap = stack.getCapability(ForgeCapabilities.ENERGY).resolve();
            if (energyCap.isEmpty()) {
                continue;
            }

            var energyStorage = energyCap.get();
            var stored = energyStorage.getEnergyStored();
            if (stored <= 0) {
                continue;
            }

            int energyToExtract = java.lang.Math.min(stored, neededEnergy);
            energyStorage.extractEnergy(energyToExtract, false);
            ((VehicleEntitySetEnergyInvoker) instance).wfcore$invokeSetEnergy(instance.getEnergy() + energyToExtract);
        }
    }
}
