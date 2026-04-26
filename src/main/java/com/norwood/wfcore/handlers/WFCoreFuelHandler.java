package com.norwood.wfcore.handlers;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.IVehicleFuelTank;
import com.norwood.wfcore.SuperbFuelOverride;
import com.norwood.wfcore.mixin.VehicleEntitySetEnergyInvoker;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.joml.Math;

public class WFCoreFuelHandler {
    public static void handleVehicleRefueling(VehicleEntity instance, String id) {

        if (instance.tickCount % 20 == 0) {
            if (SuperbFuelOverride.overrideDataMap.containsKey(id)) {
                for (int i = 0; i < instance.getContainerSize(); i++) {
                    ItemStack stack = instance.getItem(i);
                    if (stack.isEmpty()) continue;

                    var tank = ((IVehicleFuelTank) instance).getFluidTank();
                    int needed = tank.getCapacity() - tank.getFluidAmount();
                    if (needed <= 0) break;

                    var fluidHandlerLazy = stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
                    if (fluidHandlerLazy.isPresent()) {
                        int finalI = i;
                        fluidHandlerLazy.ifPresent(handler -> {
                            FluidStack drainedSim = handler.drain(needed, IFluidHandler.FluidAction.SIMULATE);
                            if (drainedSim.isEmpty()) return;

                            int filled = tank.fill(drainedSim, IFluidHandler.FluidAction.EXECUTE);

                            handler.drain(filled, IFluidHandler.FluidAction.EXECUTE);

                            ItemStack resultStack = handler.getContainer();

                            instance.setItem(finalI, resultStack);
                        });
                    }
                }
            } else if (instance.hasEnergyStorage())
                for (var stack : instance.getItemStacks()) {
                    int neededEnergy = instance.getMaxEnergy() - instance.getEnergy();
                    if (neededEnergy <= 0) break;

                    var energyCap = stack.getCapability(ForgeCapabilities.ENERGY).resolve();
                    if (energyCap.isEmpty()) continue;

                    var energyStorage = energyCap.get();
                    var stored = energyStorage.getEnergyStored();
                    if (stored <= 0) continue;

                    int energyToExtract = Math.min(stored, neededEnergy);
                    energyStorage.extractEnergy(energyToExtract, false);
                    ((VehicleEntitySetEnergyInvoker) instance).wfcore$invokeSetEnergy(instance.getEnergy() + energyToExtract);
                }
        }

    }

}
