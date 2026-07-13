package com.norwood.wfcore.common.machine.compute;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.norwood.wfcore.common.compute.WFComputeConfig;
import com.norwood.wfcore.common.cover.CoolingFanCover;
import com.norwood.wfcore.common.fluid.CoolantRegistry;

/**
 * A mainframe cooling component. A passive cooler bleeds heat to ambient; a liquid cooler drains the
 * mainframe's shared coolant for far stronger active cooling.
 *
 * <p>
 * The cooling coefficients ({@link WFComputeConfig#passiveCoolingBase()},
 * {@link WFComputeConfig#activeCoolingScale()}) and per-tick coolant draw
 * ({@link WFComputeConfig#liquidCoolantPerTick()}) are pack-tunable via the {@code WFCompute.config()} KubeJS
 * binding. Passive cooling coefficient is {@code base·(tier+1)}: a bare fan is tier 0, a Cooling Fan Cover
 * raises the effective tier to LV..EV (1..4), so a covered fan cools (tier+1)× a bare one.
 */
public class CoolingPartMachine extends MultiblockPartMachine implements ICooler {

    private final boolean isLiquid;

    public CoolingPartMachine(IMachineBlockEntity holder, boolean isLiquid) {
        super(holder);
        this.isLiquid = isLiquid;
    }

    @Override
    public boolean isLiquid() {
        return isLiquid;
    }

    @Override
    public double getPassiveCoolingRate(double currentTemp, double thermalMass, double ambient) {
        if (isLiquid || currentTemp <= ambient) return 0;
        // Effective fan tier: 0 for a bare hatch, or LV..EV (1..4) from a Cooling Fan Cover on the exposed
        // face. A covered fan therefore cools (tier + 1)x a bare one. Cooling scales with how far above
        // ambient the mainframe runs (Newton's law of cooling), so a hot mainframe sheds heat faster.
        double coolingCoefficient = WFComputeConfig.passiveCoolingBase() * (getFanTier() + 1);
        return (coolingCoefficient * (currentTemp - ambient)) / thermalMass;
    }

    private int getFanTier() {
        if (getLevel() == null) return 0;
        if (getCoverContainer().getCoverAtSide(getFrontFacing()) instanceof CoolingFanCover fan) {
            return fan.getTier();
        }
        return 0;
    }

    @Override
    public double getMaxActiveCoolingRate(double thermalMass, IFluidHandler coolantIn) {
        if (!isLiquid || coolantIn == null) return 0;
        // Mirror executeActiveCooling: the first registered-coolant tank, capped at what's actually available
        // (≤ 100mB/tick/hatch), so the reported potential matches what we can really drain this tick.
        for (int i = 0; i < coolantIn.getTanks(); i++) {
            FluidStack stack = coolantIn.getFluidInTank(i);
            if (stack.isEmpty()) continue;
            CoolantRegistry.CoolantSettings settings = CoolantRegistry.get(stack.getFluid());
            if (settings == null) continue;
            int available = Math.min(getFluidUsagePerTick(), stack.getAmount());
            return (available * settings.heatCapacity() * WFComputeConfig.activeCoolingScale()) / thermalMass;
        }
        return 0;
    }

    @Override
    public int getFluidUsagePerTick() {
        return isLiquid ? WFComputeConfig.liquidCoolantPerTick() : 0;
    }

    @Override
    public double executeActiveCooling(double percentage, double thermalMass, IFluidHandler in, IFluidHandler out) {
        if (!isLiquid || percentage <= 0 || in == null) return 0;

        for (int i = 0; i < in.getTanks(); i++) {
            FluidStack stack = in.getFluidInTank(i);
            if (stack.isEmpty()) continue;

            CoolantRegistry.CoolantSettings settings = CoolantRegistry.get(stack.getFluid());
            if (settings == null) continue;

            int amountToDrain = Math.min((int) Math.ceil(getFluidUsagePerTick() * percentage), stack.getAmount());
            if (amountToDrain <= 0) continue;

            double scale = WFComputeConfig.activeCoolingScale();
            FluidStack drainTarget = new FluidStack(stack.getFluid(), amountToDrain);

            if (settings.hotVariant() != null && out != null) {
                FluidStack hotStack = new FluidStack(settings.hotVariant(), amountToDrain);
                if (out.fill(hotStack, IFluidHandler.FluidAction.SIMULATE) == amountToDrain) {
                    FluidStack drained = in.drain(drainTarget, IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty() && drained.getAmount() > 0) {
                        hotStack.setAmount(drained.getAmount());
                        out.fill(hotStack, IFluidHandler.FluidAction.EXECUTE);
                        return (drained.getAmount() * settings.heatCapacity() * scale) / thermalMass;
                    }
                }
            } else {
                FluidStack drained = in.drain(drainTarget, IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty() && drained.getAmount() > 0) {
                    return (drained.getAmount() * settings.heatCapacity() * scale) / thermalMass;
                }
            }
        }
        return 0;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public boolean canShared() {
        return false;
    }
}
