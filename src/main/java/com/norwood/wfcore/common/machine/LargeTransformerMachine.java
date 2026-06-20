package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
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
 * Large Transformer: a 3x3x3 coolant-cooled power converter, like the Active Transformer but without laser
 * tech. It converts normal (DC) EU to/from WFCore "AC EU" through a single AC output (DC-&gt;AC) and a single
 * AC input (AC-&gt;DC) converter hatch. Every EU converted must be cooled by draining coolant - cooler coolants
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
    private IFluidHandler coolantInput;
    @Nullable
    private ACHatchPartMachine acInputHatch;
    @Nullable
    private ACHatchPartMachine acOutputHatch;

    @Persisted
    @DescSynced
    private boolean isWorkingEnabled = true;
    @Persisted
    @DescSynced
    private boolean isActive;
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
            return;
        }
        boolean worked = false;

        // DC -> AC: pull DC, cool it, push AC out through the cable
        if (acOutputHatch != null) {
            long want = Math.min(powerInput.getEnergyStored(), MAX_TRANSFER_PER_TICK);
            long cooled = coolEnergy(want);
            if (cooled > 0) {
                long pushed = acOutputHatch.pushAC(cooled);
                if (pushed > 0) {
                    powerInput.removeEnergy(pushed);
                    worked = true;
                }
            }
        }

        // AC -> DC: pull buffered AC, cool it, output DC
        if (acInputHatch != null) {
            long want = Math.min(acInputHatch.getStored(), MAX_TRANSFER_PER_TICK);
            long cooled = coolEnergy(want);
            if (cooled > 0) {
                acInputHatch.drainBuffer(cooled);
                powerOutput.changeEnergy(cooled);
                worked = true;
            }
        }

        setActive(worked);
    }

    /** Drains coolant to cover up to {@code desiredEU}; returns the EU successfully cooled. */
    private long coolEnergy(long desiredEU) {
        if (desiredEU <= 0 || coolantInput == null) return 0;
        long cooled = 0;
        for (int i = 0; i < coolantInput.getTanks(); i++) {
            if (cooled >= desiredEU) break;
            FluidStack stack = coolantInput.getFluidInTank(i);
            if (stack.isEmpty()) continue;
            CoolantRegistry.CoolantSettings settings = CoolantRegistry.get(stack.getFluid());
            if (settings == null) continue;
            long euPerMb = Math.max(1, (long) (settings.heatCapacity() * EU_PER_MB_FACTOR));
            long remaining = desiredEU - cooled;
            int mbNeeded = (int) Math.min(stack.getAmount(), (remaining + euPerMb - 1) / euPerMb);
            if (mbNeeded <= 0) continue;
            FluidStack drained = coolantInput.drain(new FluidStack(stack.getFluid(), mbNeeded),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty() && drained.getAmount() > 0) {
                cooled += (long) drained.getAmount() * euPerMb;
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
                        tl.add(Component.translatable(acOutputHatch != null ? "wfcore.gui.transformer.dc_to_ac" :
                                "wfcore.gui.transformer.no_ac_out"));
                        tl.add(Component.translatable(acInputHatch != null ? "wfcore.gui.transformer.ac_to_dc" :
                                "wfcore.gui.transformer.no_ac_in"));
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
