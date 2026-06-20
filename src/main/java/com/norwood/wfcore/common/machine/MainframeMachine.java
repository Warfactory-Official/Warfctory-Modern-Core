package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
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

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.norwood.wfcore.common.compute.CPURegistry;
import com.norwood.wfcore.common.machine.compute.ICooler;
import com.norwood.wfcore.common.machine.compute.ICpuSlot;
import com.norwood.wfcore.common.machine.compute.IRamSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Computation mainframe: a demand-driven CWU provider (HPCA-style). CPU components supply compute, RAM caps
 * the throughput, coolers fight the heat the CPUs make. It exposes GregTech's optical computation capability
 * (via a Computation Data Transmission hatch) so any consumer with a reception hatch can pull CWU.
 */
public class MainframeMachine extends MultiblockControllerMachine
                              implements IOpticalComputationProvider, IControllable, IFancyUIMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(MainframeMachine.class,
            MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    private static final double MAX_TEMP = 105.0;

    final GPCHandler gpcHandler = new GPCHandler(this);
    private IEnergyContainer energyContainer = new EnergyContainerList(new ArrayList<>());
    @Nullable
    IFluidHandler coolantIn;
    @Nullable
    IFluidHandler coolantOut;
    final List<ICpuSlot> cpuSlots = new ArrayList<>();
    final List<IRamSlot> ramSlots = new ArrayList<>();
    final List<ICooler> coolers = new ArrayList<>();

    @Persisted
    @DescSynced
    private boolean isActive;
    @Persisted
    @DescSynced
    private boolean isWorkingEnabled = true;
    private boolean hasNotEnoughEnergy;
    double AMBIENT = Double.NaN;
    double currentTemp = Double.NaN;
    private long currentCWU;
    @Nullable
    private TickableSubscription tickSub;

    public MainframeMachine(IMachineBlockEntity holder) {
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
        List<IEnergyContainer> energy = new ArrayList<>();
        List<IFluidHandler> fluidIn = new ArrayList<>();
        List<IFluidHandler> fluidOut = new ArrayList<>();
        cpuSlots.clear();
        ramSlots.clear();
        coolers.clear();

        for (IMultiPart part : getParts()) {
            if (part instanceof ICpuSlot cpu) cpuSlots.add(cpu);
            if (part instanceof IRamSlot ram) ramSlots.add(ram);
            if (part instanceof ICooler cooler) coolers.add(cooler);
            for (var handlerList : part.getRecipeHandlers()) {
                IO io = handlerList.getHandlerIO();
                if (io.support(IO.IN)) {
                    handlerList.getCapability(EURecipeCapability.CAP).stream()
                            .filter(IEnergyContainer.class::isInstance)
                            .map(IEnergyContainer.class::cast)
                            .forEach(energy::add);
                    handlerList.getCapability(FluidRecipeCapability.CAP).stream()
                            .filter(IFluidHandler.class::isInstance)
                            .map(IFluidHandler.class::cast)
                            .forEach(fluidIn::add);
                } else if (io.support(IO.OUT)) {
                    handlerList.getCapability(FluidRecipeCapability.CAP).stream()
                            .filter(IFluidHandler.class::isInstance)
                            .map(IFluidHandler.class::cast)
                            .forEach(fluidOut::add);
                }
            }
        }
        this.energyContainer = new EnergyContainerList(energy);
        this.coolantIn = new FluidHandlerList(fluidIn);
        this.coolantOut = new FluidHandlerList(fluidOut);
        this.gpcHandler.rebuild();
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickMainframe);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = new EnergyContainerList(new ArrayList<>());
        this.coolantIn = null;
        this.coolantOut = null;
        cpuSlots.clear();
        ramSlots.clear();
        coolers.clear();
        this.gpcHandler.reset();
        setActive(false);
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    //////////////////// tick ////////////////////

    protected void tickMainframe() {
        if (isRemote() || !isFormed()) return;
        if (Double.isNaN(AMBIENT)) {
            this.AMBIENT = computeAmbient();
            if (Double.isNaN(currentTemp)) this.currentTemp = AMBIENT;
        }
        // re-read component stats each tick so swapping CPUs/RAM/coolers takes effect
        gpcHandler.rebuild();

        if (!isWorkingEnabled) {
            setActive(false);
            currentTemp = Math.max(AMBIENT, currentTemp - 0.25);
            this.currentCWU = 0;
            gpcHandler.clearAllocation();
            return;
        }

        gpcHandler.tick();
        consumeEnergy();

        if (isActive) {
            double temperatureChange = gpcHandler.calculateTemperatureChange(currentTemp >= 70.0);
            if (currentTemp + temperatureChange <= AMBIENT) {
                currentTemp = AMBIENT;
            } else {
                currentTemp += temperatureChange;
            }
            if (currentTemp >= MAX_TEMP) {
                explode();
                return;
            }
            this.currentCWU = gpcHandler.getAllocatedCWUt();
        } else {
            currentTemp = Math.max(AMBIENT, currentTemp - 0.25);
            this.currentCWU = 0;
            gpcHandler.clearAllocation();
        }
    }

    private void consumeEnergy() {
        long energyToConsume = gpcHandler.getCurrentEUt();
        if (this.hasNotEnoughEnergy && energyContainer.getInputPerSec() > 19L * energyToConsume) {
            this.hasNotEnoughEnergy = false;
        }
        if (energyContainer.getEnergyStored() >= energyToConsume) {
            energyContainer.removeEnergy(energyToConsume);
            this.hasNotEnoughEnergy = false;
            setActive(true);
        } else {
            this.hasNotEnoughEnergy = true;
            setActive(false);
        }
    }

    private void explode() {
        setActive(false);
        if (getLevel() != null) {
            getLevel().explode(null, getPos().getX() + 0.5, getPos().getY() + 0.5, getPos().getZ() + 0.5,
                    10f, Level.ExplosionInteraction.BLOCK);
        }
    }

    private double computeAmbient() {
        if (getLevel() == null || getPos() == null) return 22.0;
        if (getLevel().dimension() == Level.NETHER) return 70.0;
        if (getLevel().dimension() == Level.END) return 5.0;
        float temp = getLevel().getBiome(getPos()).value().getBaseTemperature();
        return (temp * 30.0) - 5.0;
    }

    //////////////////// IOpticalComputationProvider ////////////////////

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        if (!isActive() || !isWorkingEnabled || hasNotEnoughEnergy) return 0;
        return gpcHandler.requestComputation(cwut, simulate);
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        if (!isActive() || !isWorkingEnabled || hasNotEnoughEnergy) return 0;
        return (int) gpcHandler.getProvidableCWUt();
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        return false;
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

    public boolean isActive() {
        return isFormed() && isActive;
    }

    private void setActive(boolean active) {
        if (this.isActive != active) {
            this.isActive = active;
        }
    }

    //////////////////// UI ////////////////////

    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(isWorkingEnabled, gpcHandler.getAllocatedCWUt() > 0)
                .setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.idling",
                        "gtceu.multiblock.data_bank.providing")
                .addCustom(tl -> {
                    if (isFormed()) {
                        tl.add(Component.translatable("wfcore.gui.mainframe.energy",
                                gpcHandler.cachedEUt, gpcHandler.getMaxEUt()).withStyle(ChatFormatting.GRAY));
                        tl.add(Component.translatable("wfcore.gui.mainframe.computation",
                                gpcHandler.getAllocatedCWUt(), gpcHandler.getMaxCWUt())
                                .withStyle(ChatFormatting.AQUA));
                        tl.add(Component.translatable("wfcore.gui.mainframe.heat",
                                String.format("%.1f", currentTemp)).withStyle(ChatFormatting.GOLD));
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

    //////////////////// GPC handler ////////////////////

    public static class GPCHandler {

        private final MainframeMachine mainframe;
        long totalThroughput;
        double totalThermalMass;
        int cpuCount;
        private CPURegistry.CPUEntry[] activeCPUs = new CPURegistry.CPUEntry[0];
        private ICooler[] passiveCoolers = new ICooler[0];
        private ICooler[] liquidCoolers = new ICooler[0];
        private long[] cpuLimits = new long[0];
        private long allocatedCWUt;
        private long requestedCWUtThisTick;
        long cachedEUt;
        private double currentSag;

        private GPCHandler(MainframeMachine mainframe) {
            this.mainframe = mainframe;
        }

        public long getAllocatedCWUt() {
            return allocatedCWUt;
        }

        public void tick() {
            this.currentSag = calculateSag();
            this.allocatedCWUt = Math.min(this.requestedCWUtThisTick, getProvidableCWUt());
            this.requestedCWUtThisTick = 0;
            this.cachedEUt = getCurrentEUt();
        }

        public long getProvidableCWUt() {
            long thermal = (long) (getMaxCWUt() * (1.0 - this.currentSag));
            return Math.max(0L, Math.min(thermal, this.totalThroughput));
        }

        public int requestComputation(int cwut, boolean simulate) {
            if (cwut <= 0) return 0;
            long remaining = getProvidableCWUt() - this.requestedCWUtThisTick;
            if (remaining <= 0) return 0;
            int granted = (int) Math.min((long) cwut, remaining);
            if (!simulate) this.requestedCWUtThisTick += granted;
            return granted;
        }

        private double calculateSag() {
            if (mainframe.currentTemp <= 90.0) return 0.0;
            double penalty = Math.pow((mainframe.currentTemp - 90.0) / 10.0, 2) * 0.5;
            return Math.min(1.0, penalty);
        }

        public void reset() {
            clearAllocation();
            this.activeCPUs = new CPURegistry.CPUEntry[0];
            this.passiveCoolers = new ICooler[0];
            this.liquidCoolers = new ICooler[0];
            this.cpuLimits = new long[0];
            this.totalThroughput = 0;
            this.cpuCount = 0;
        }

        public void clearAllocation() {
            this.allocatedCWUt = 0;
            this.requestedCWUtThisTick = 0;
        }

        public long getCurrentEUt() {
            long maximumCWUt = Math.max(1, getMaxCWUt());
            long maximumEUt = getMaxEUt();
            long upkeepEUt = getUpkeepEUt();
            if (maximumEUt == upkeepEUt) return maximumEUt;
            return upkeepEUt + ((maximumEUt - upkeepEUt) * allocatedCWUt / maximumCWUt);
        }

        private long getUpkeepEUt() {
            long upkeepEUt = 0;
            for (CPURegistry.CPUEntry component : activeCPUs) upkeepEUt += component.minPower();
            return upkeepEUt;
        }

        public long getMaxEUt() {
            long maximumEUt = 0;
            for (int i = 0; i < activeCPUs.length; i++) maximumEUt += cpuLimits[i];
            return maximumEUt;
        }

        public int getMaxCoolingDemand() {
            int maxCooling = 0;
            for (int i = 0; i < activeCPUs.length; i++) {
                maxCooling += (int) activeCPUs[i].getHeat(this.cpuLimits[i]);
            }
            return maxCooling;
        }

        public int getMaxCoolingAmount() {
            double maxCooling = 0;
            for (ICooler cooler : passiveCoolers) {
                maxCooling += cooler.getPassiveCoolingRate(mainframe.currentTemp, totalThermalMass, mainframe.AMBIENT);
            }
            for (ICooler cooler : liquidCoolers) {
                maxCooling += cooler.getMaxActiveCoolingRate(totalThermalMass, mainframe.coolantIn);
            }
            return (int) maxCooling;
        }

        public long getMaxCWUt() {
            long maxCWUt = 0;
            for (int i = 0; i < activeCPUs.length; i++) {
                maxCWUt += activeCPUs[i].getCWU(this.cpuLimits[i]);
            }
            return maxCWUt;
        }

        public double calculateTemperatureChange(boolean forceCoolWithActive) {
            long maxCWUt = Math.max(1, getMaxCWUt());
            int maxCoolingDemand = getMaxCoolingDemand();
            double temperatureIncrease = (double) maxCoolingDemand * allocatedCWUt / maxCWUt;

            double passiveCoolingDone = 0;
            for (ICooler cooler : passiveCoolers) {
                passiveCoolingDone += cooler.getPassiveCoolingRate(mainframe.currentTemp, totalThermalMass,
                        mainframe.AMBIENT);
            }

            double remainingHeat = temperatureIncrease - passiveCoolingDone;
            if (remainingHeat <= 0 && !forceCoolWithActive) {
                return remainingHeat;
            }

            double activePotential = 0;
            for (ICooler cooler : liquidCoolers) {
                activePotential += cooler.getMaxActiveCoolingRate(totalThermalMass, mainframe.coolantIn);
            }

            if (activePotential > 0) {
                double coolingNeeded = forceCoolWithActive ? activePotential : Math.min(remainingHeat, activePotential);
                double percentageToExecute = coolingNeeded / activePotential;
                double actualActiveCooling = 0;
                for (ICooler cooler : liquidCoolers) {
                    actualActiveCooling += cooler.executeActiveCooling(percentageToExecute, totalThermalMass,
                            mainframe.coolantIn, mainframe.coolantOut);
                }
                remainingHeat -= actualActiveCooling;
            }
            return remainingHeat;
        }

        public void rebuild() {
            this.totalThroughput = mainframe.ramSlots.stream().mapToLong(IRamSlot::getTotalThroughput).sum();

            List<ICooler> passive = new ArrayList<>();
            List<ICooler> active = new ArrayList<>();
            for (ICooler c : mainframe.coolers) {
                if (c.isLiquid()) active.add(c);
                else passive.add(c);
            }
            this.passiveCoolers = passive.toArray(new ICooler[0]);
            this.liquidCoolers = active.toArray(new ICooler[0]);

            List<CPURegistry.CPUEntry> hardware = new ArrayList<>();
            for (ICpuSlot slot : mainframe.cpuSlots) {
                CPURegistry.CPUEntry stats = slot.getStats();
                if (stats != null) hardware.add(stats);
            }
            this.cpuCount = hardware.size();
            this.activeCPUs = hardware.toArray(new CPURegistry.CPUEntry[0]);
            this.cpuLimits = new long[this.cpuCount];
            for (int i = 0; i < this.cpuCount; i++) {
                this.cpuLimits[i] = this.activeCPUs[i].maxPower();
            }

            double baseFrameMass = 500.0;
            int totalPhysicalHatches = mainframe.cpuSlots.size() + mainframe.coolers.size() + mainframe.ramSlots.size();
            this.totalThermalMass = baseFrameMass + totalPhysicalHatches * 50.0;
        }
    }
}
