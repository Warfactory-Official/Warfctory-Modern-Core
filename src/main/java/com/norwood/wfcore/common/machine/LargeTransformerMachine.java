package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;

import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.norwood.wfcore.common.fluid.CoolantRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Large Transformer: a 3x3x3 power distributor, like the Active Transformer but without laser tech. Its core
 * job is to take DC EU from its input energy hatches and redistribute it through its output energy hatches
 * (dynamos), which re-emit at their own voltage - a plain voltage transformer that needs no coolant.
 * <p>
 * On top of that it can optionally convert to/from WFCore "AC EU" for lossless long-distance transmission,
 * through a single AC output (DC-&gt;AC) and/or AC input (AC-&gt;DC) converter hatch. That AC conversion is the
 * coolant-cooled part: every EU converted to/from AC must be cooled by draining coolant - cooler coolants
 * (helium, liquid nitrogen) carry more EU per millibucket than water.
 */
public class LargeTransformerMachine extends MultiblockControllerMachine
                                     implements IControllable, IFancyUIMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(LargeTransformerMachine.class,
            MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    private static final long MAX_TRANSFER_PER_TICK = Integer.MAX_VALUE;
    private static final double EU_PER_MB_FACTOR = 2.0; // water (heatCapacity 1.0) => 2 EU per mB

    private IEnergyContainer powerInput = new EnergyContainerList(new ArrayList<>());
    private IEnergyContainer powerOutput = new EnergyContainerList(new ArrayList<>());
    @Nullable
    private FluidHandlerList coolantInput;
    @Nullable
    private ACHatchPartMachine acInputHatch;
    @Nullable
    private ACHatchPartMachine acOutputHatch;

    // Why the machine isn't converting, for the display. Synced because onStructureFormed and the tick run
    // server-only, so the client can't derive it (acInputHatch/acOutputHatch are always null client-side).
    public static final byte STATUS_OK = 0;          // converting, or nothing to do
    public static final byte STATUS_NO_COOLANT = 1;  // has energy to move but no/too little coolant
    public static final byte STATUS_NO_SINK = 2;     // AC output cable routes to no AC input hatch at all
    public static final byte STATUS_AC_FULL = 3;     // AC destination found, but its buffer is full (backed up)

    @Persisted
    @DescSynced
    private boolean isWorkingEnabled = true;
    @Persisted
    @DescSynced
    private boolean isActive;
    @DescSynced
    private long convertedEUt;
    @DescSynced
    private byte status = STATUS_OK;
    @Nullable
    private TickableSubscription tickSub;

    public LargeTransformerMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////// structure lifecycle ////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        List<IEnergyContainer> inputs = new ArrayList<>();
        List<IEnergyContainer> outputs = new ArrayList<>();
        List<IFluidHandler> coolant = new ArrayList<>();

        for (IMultiPart part : getParts()) {
            if (part instanceof ACHatchPartMachine ac) {
                if (ac.isOutput()) {
                    acOutputHatch = ac;
                } else {
                    acInputHatch = ac;
                }
                continue;
            }
            for (var handlerList : part.getRecipeHandlers()) {
                IO io = handlerList.getHandlerIO();
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(c -> {
                            if (io.support(IO.IN)) {
                                inputs.add(c);
                            } else if (io.support(IO.OUT)) {
                                outputs.add(c);
                            }
                        });
                if (io.support(IO.IN)) {
                    handlerList.getCapability(FluidRecipeCapability.CAP).stream()
                            .filter(IFluidHandler.class::isInstance)
                            .map(IFluidHandler.class::cast)
                            .forEach(coolant::add);
                }
            }
        }

        this.powerInput = new EnergyContainerList(inputs);
        this.powerOutput = new EnergyContainerList(outputs);
        this.coolantInput = new FluidHandlerList(coolant);
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickTransformer);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.powerInput = new EnergyContainerList(new ArrayList<>());
        this.powerOutput = new EnergyContainerList(new ArrayList<>());
        this.coolantInput = null;
        this.acInputHatch = null;
        this.acOutputHatch = null;
        setActive(false);
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    //////////////////// conversion ////////////////////

    protected void tickTransformer() {
        if (isRemote() || !isFormed() || !isWorkingEnabled) {
            setActive(false);
            setStatus(STATUS_OK, 0);
            return;
        }
        long converted = 0;
        byte reason = STATUS_OK;

        // DC -> DC: the core transformer function. Move input-hatch energy into the output dynamos, which
        // then re-emit it onto their cables at their OWN voltage/amperage. No coolant - this is a plain
        // voltage transformer (like GT's Active Transformer). changeEnergy() bounds this by the dynamos'
        // free buffer space, and removeEnergy() only drains what the dynamos actually took.
        long redistributed = powerOutput.changeEnergy(powerInput.getEnergyStored());
        if (redistributed > 0) {
            powerInput.removeEnergy(redistributed);
            converted += redistributed;
        }

        // DC -> AC: push any leftover input energy onto an AC cable for lossless long-distance transmission.
        // Coolant-costed; only cool what the AC network will actually carry this tick (a simulated push tells
        // us) - otherwise coolant is spent converting the whole input buffer while the cable carries only its
        // throughput, draining coolant in a single tick and leaving the machine stuck waiting for coolant.
        if (acOutputHatch != null) {
            long available = Math.min(powerInput.getEnergyStored(), MAX_TRANSFER_PER_TICK);
            long transferable = acOutputHatch.pushAC(available, true);
            if (available > 0 && transferable <= 0 && converted == 0) {
                // Distinguish "nothing connected" from "connected but the receiver's buffer is full", since
                // a simulated push returns 0 for both. hasDestination() walks the cable to a live AC input.
                reason = acOutputHatch.hasDestination() ? STATUS_AC_FULL : STATUS_NO_SINK;
            }
            long cooled = coolEnergy(transferable);
            if (transferable > 0 && cooled < transferable) reason = STATUS_NO_COOLANT;
            if (cooled > 0) {
                long pushed = acOutputHatch.pushAC(cooled, false);
                if (pushed > 0) {
                    powerInput.removeEnergy(pushed);
                    converted += pushed;
                }
            }
        }

        // AC -> DC: pull buffered AC, cool it, output DC. Bound by the DC output's free buffer space so we
        // don't cool more than the output can accept (same coolant-waste trap as above).
        if (acInputHatch != null) {
            long available = Math.min(acInputHatch.getStored(), MAX_TRANSFER_PER_TICK);
            long space = Math.max(0, powerOutput.getEnergyCapacity() - powerOutput.getEnergyStored());
            long transferable = Math.min(available, space);
            long cooled = coolEnergy(transferable);
            if (transferable > 0 && cooled < transferable) reason = STATUS_NO_COOLANT;
            if (cooled > 0) {
                long accepted = powerOutput.changeEnergy(cooled);
                if (accepted > 0) {
                    acInputHatch.drainBuffer(accepted);
                    converted += accepted;
                }
            }
        }

        setActive(converted > 0);
        setStatus(converted > 0 ? STATUS_OK : reason, converted);
    }

    private void setStatus(byte reason, long converted) {
        if (this.status != reason) this.status = reason;
        if (this.convertedEUt != converted) this.convertedEUt = converted;
    }

    /** Drains coolant to cover up to {@code desiredEU}; returns the EU successfully cooled. */
    private long coolEnergy(long desiredEU) {
        if (desiredEU <= 0 || coolantInput == null) return 0;
        long cooled = 0;
        // Iterate the raw hatch tanks: these are INPUT (IO.IN) NotifiableFluidTanks, whose public drain() is
        // gated by canCapOutput() and always returns EMPTY - so we must call drainInternal() (what recipe
        // logic uses) to actually pull coolant out. Draining the aggregate handler would silently no-op.
        for (IFluidHandler handler : coolantInput.handlers) {
            for (int i = 0; i < handler.getTanks(); i++) {
                if (cooled >= desiredEU) return desiredEU;
                FluidStack stack = handler.getFluidInTank(i);
                if (stack.isEmpty()) continue;
                CoolantRegistry.CoolantSettings settings = CoolantRegistry.get(stack.getFluid());
                if (settings == null) continue;
                long euPerMb = Math.max(1, (long) (settings.heatCapacity() * EU_PER_MB_FACTOR));
                long remaining = desiredEU - cooled;
                int mbNeeded = (int) Math.min(stack.getAmount(), (remaining + euPerMb - 1) / euPerMb);
                if (mbNeeded <= 0) continue;
                FluidStack request = new FluidStack(stack.getFluid(), mbNeeded);
                FluidStack drained = handler instanceof NotifiableFluidTank tank
                        ? tank.drainInternal(request, IFluidHandler.FluidAction.EXECUTE)
                        : handler.drain(request, IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty() && drained.getAmount() > 0) {
                    cooled += (long) drained.getAmount() * euPerMb;
                }
            }
        }
        return Math.min(cooled, desiredEU);
    }

    private void setActive(boolean active) {
        if (this.isActive != active) {
            this.isActive = active;
        }
    }

    //////////////////// IControllable ////////////////////

    @Override
    public boolean isWorkingEnabled() {
        return isWorkingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.isWorkingEnabled = workingEnabled;
    }

    //////////////////// UI ////////////////////

    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(isWorkingEnabled, isActive)
                .addEnergyUsageLine(powerInput)
                .addCustom(tl -> {
                    if (isFormed()) {
                        if (isActive) {
                            tl.add(Component.translatable("wfcore.gui.transformer.converting", convertedEUt));
                        } else if (status == STATUS_NO_COOLANT) {
                            tl.add(Component.translatable("wfcore.gui.transformer.no_coolant"));
                        } else if (status == STATUS_NO_SINK) {
                            tl.add(Component.translatable("wfcore.gui.transformer.no_sink"));
                        } else if (status == STATUS_AC_FULL) {
                            tl.add(Component.translatable("wfcore.gui.transformer.ac_full"));
                        }
                    }
                })
                .addWorkingStatusLine();
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 190, 125);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(GuiTextures.DISPLAY)
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText).setMaxWidthLimit(150)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }
}
