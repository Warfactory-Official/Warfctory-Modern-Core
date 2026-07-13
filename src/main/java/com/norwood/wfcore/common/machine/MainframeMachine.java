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
import com.norwood.wfcore.common.compute.WFComputeConfig;
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
    @Persisted
    double currentTemp = Double.NaN;
    // Set when a CPU/RAM part's contents change (or the structure (re)forms), so the GPC handler rebuilds its
    // cached component stats on the next tick instead of every tick.
    private boolean computeDirty = true;
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
        this.computeDirty = false;
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickMainframe);
        }
    }

    /** Called by CPU/RAM parts when their contents change; the handler rebuilds on the next tick. */
    public void markComputeDirty() {
        this.computeDirty = true;
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
        // rebuild cached component stats only when a part changed, so swapping CPUs/RAM still takes effect
        if (computeDirty) {
            gpcHandler.rebuild();
            computeDirty = false;
        }

        if (!isWorkingEnabled) {
            setActive(false);
            currentTemp = Math.max(AMBIENT, currentTemp - WFComputeConfig.idleCooldownRate());
            gpcHandler.clearAllocation();
            return;
        }

        gpcHandler.tick();
        consumeEnergy();

        if (isActive) {
            double temperatureChange = gpcHandler
                    .calculateTemperatureChange(currentTemp >= WFComputeConfig.forceActiveCoolTemp());
            if (currentTemp + temperatureChange <= AMBIENT) {
                currentTemp = AMBIENT;
            } else {
                currentTemp += temperatureChange;
            }
            if (currentTemp >= WFComputeConfig.maxTemperature()) {
                explode();
                return;
            }
        } else {
            currentTemp = Math.max(AMBIENT, currentTemp - WFComputeConfig.idleCooldownRate());
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
                    (float) WFComputeConfig.explosionStrength(), Level.ExplosionInteraction.BLOCK);
        }
    }

    private double computeAmbient() {
        if (getLevel() == null || getPos() == null) return WFComputeConfig.ambientDefault();
        if (getLevel().dimension() == Level.NETHER) return WFComputeConfig.ambientNether();
        if (getLevel().dimension() == Level.END) return WFComputeConfig.ambientEnd();
        float temp = getLevel().getBiome(getPos()).value().getBaseTemperature();
        return (temp * WFComputeConfig.ambientBiomeScale()) + WFComputeConfig.ambientBiomeOffset();
    }

    //////////////////// IOpticalComputationProvider ////////////////////

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (!isActive() || !isWorkingEnabled || hasNotEnoughEnergy) return 0;
        return gpcHandler.requestComputation(cwut, simulate);
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        if (!isActive() || !isWorkingEnabled || hasNotEnoughEnergy) return 0;
        return (int) Math.min(Integer.MAX_VALUE, gpcHandler.getProvidableCWUt());
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
        // CPU-set aggregates, recomputed only in rebuild() (the CPUs are constant between rebuilds) so the
        // per-tick / per-consumer hot path reads them in O(1) instead of iterating + Math.pow-ing every call.
        private long cachedMaxCWUt;
        private long cachedMaxEUt;
        private long cachedUpkeepEUt;
        private int cachedMaxCoolingDemand;

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
            double sagStart = WFComputeConfig.sagStartTemp();
            if (mainframe.currentTemp <= sagStart) return 0.0;
            double penalty = Math.pow((mainframe.currentTemp - sagStart) / WFComputeConfig.sagTempSpan(), 2)
                    * WFComputeConfig.sagPenaltyScale();
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
            this.cachedMaxCWUt = 0;
            this.cachedMaxEUt = 0;
            this.cachedUpkeepEUt = 0;
            this.cachedMaxCoolingDemand = 0;
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
            return cachedUpkeepEUt;
        }

        public long getMaxEUt() {
            return cachedMaxEUt;
        }

        public int getMaxCoolingDemand() {
            return cachedMaxCoolingDemand;
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
            return cachedMaxCWUt;
        }

        public double calculateTemperatureChange(boolean forceCoolWithActive) {
            long maxCWUt = Math.max(1, getMaxCWUt());
            int maxCoolingDemand = getMaxCoolingDemand();
            // ΔT = net heat / thermal mass. Heat generation is divided by the same thermalMass the coolers use,
            // so the two sides share units: thermalMass cancels at equilibrium (it only sets how fast temp moves),
            // and the machine actually reaches a steady state instead of running away in a handful of ticks.
            double temperatureIncrease = (double) maxCoolingDemand * allocatedCWUt / maxCWUt / totalThermalMass;

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
            long maxCWUt = 0, maxEUt = 0, upkeepEUt = 0;
            int coolingDemand = 0;
            for (int i = 0; i < this.cpuCount; i++) {
                CPURegistry.CPUEntry cpu = this.activeCPUs[i];
                long limit = cpu.maxPower();
                this.cpuLimits[i] = limit;
                maxCWUt += cpu.getCWU(limit);
                maxEUt += limit;
                upkeepEUt += cpu.minPower();
                coolingDemand += (int) cpu.getHeat(limit);
            }
            this.cachedMaxCWUt = maxCWUt;
            this.cachedMaxEUt = maxEUt;
            this.cachedUpkeepEUt = upkeepEUt;
            this.cachedMaxCoolingDemand = coolingDemand;

            int totalPhysicalHatches = mainframe.cpuSlots.size() + mainframe.coolers.size() + mainframe.ramSlots.size();
            this.totalThermalMass = WFComputeConfig.baseFrameMass()
                    + totalPhysicalHatches * WFComputeConfig.hatchThermalMass();
        }
    }
}
