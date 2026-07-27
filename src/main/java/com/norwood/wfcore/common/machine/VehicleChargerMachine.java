package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.common.machine.electric.ChargerMachine;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.energy.IEnergyStorage;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.IVehicleFuelTank;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class VehicleChargerMachine extends TieredEnergyMachine implements IControllable {

    /** Input amperage; per-tick EU budget is {@code V[tier] * AMPS}. */
    public static final long AMPS = 4L;
    /** Block radius around the machine within which vehicles are charged. */
    public static final int RANGE = 4;

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            VehicleChargerMachine.class, TieredEnergyMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    private boolean isWorkingEnabled = true;

    @Nullable
    private TickableSubscription tickSub;

    public VehicleChargerMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public boolean isWorkingEnabled() {
        return isWorkingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        this.isWorkingEnabled = isWorkingAllowed;
    }

    @Override
    protected NotifiableEnergyContainer createEnergyContainer(Object... args) {
        long tierVoltage = GTValues.V[getTier()];
        NotifiableEnergyContainer container = NotifiableEnergyContainer.receiverContainer(
                this, tierVoltage * 64L, tierVoltage, AMPS);
        container.setSideInputCondition(side -> isWorkingEnabled());
        return container;
    }

    @Override
    public int tintColor(int index) {
        // Tint the hull layer with the tier colour, matching GT's charger.
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickCharge);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    protected void tickCharge() {
        if (isRemote()) {
            return;
        }
        Level level = getLevel();
        if (level == null || !isWorkingEnabled) {
            setState(ChargerMachine.State.IDLE);
            return;
        }

        long stored = energyContainer.getEnergyStored();
        if (stored <= 0) {
            setState(ChargerMachine.State.IDLE);
            return;
        }

        AABB area = new AABB(getPos()).inflate(RANGE);
        List<VehicleEntity> vehicles = level.getEntitiesOfClass(VehicleEntity.class, area,
                VehicleChargerMachine::isEnergyChargeableVehicle);
        if (vehicles.isEmpty()) {
            setState(ChargerMachine.State.IDLE);
            return;
        }

        long budget = Math.min(stored, GTValues.V[getTier()] * AMPS);
        long euRemaining = budget;
        boolean anyCharged = false;

        for (VehicleEntity vehicle : vehicles) {
            if (euRemaining <= 0) {
                break;
            }
            if (vehicle.getEnergy() >= vehicle.getMaxEnergy()) {
                continue;
            }
            IEnergyStorage storage = vehicle.getEnergyStorage();
            if (storage == null) {
                continue;
            }
            long inserted = FeCompat.insertEu(storage, euRemaining, false);
            if (inserted > 0) {
                euRemaining -= inserted;
                anyCharged = true;
            }
        }

        long euUsed = budget - euRemaining;
        if (euUsed > 0) {
            energyContainer.removeEnergy(euUsed);
        }

        // RUNNING while we actually pushed energy; FINISHED when vehicles are present but all full.
        setState(anyCharged ? ChargerMachine.State.RUNNING : ChargerMachine.State.FINISHED);
    }

    private void setState(ChargerMachine.State newState) {
        var renderState = getRenderState();
        if (renderState.getValue(GTMachineModelProperties.CHARGER_STATE) != newState) {
            setRenderState(renderState.setValue(GTMachineModelProperties.CHARGER_STATE, newState));
        }
    }

    /** A Superb Warfare vehicle that uses native energy and is NOT under the WFCore fluid-fuel override. */
    private static boolean isEnergyChargeableVehicle(VehicleEntity vehicle) {
        if (vehicle.isRemoved()) {
            return false;
        }
        // Fluid-fuel-override vehicles run on a fluid tank, not EU/FE — leave them to the fluid refuelling path.
        if (vehicle instanceof IVehicleFuelTank fuelTank && fuelTank.getFluidTank() != null) {
            return false;
        }
        return vehicle.hasEnergyStorage();
    }
}
