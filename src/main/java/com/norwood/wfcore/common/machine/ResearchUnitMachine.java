package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchRegistry;
import com.norwood.wfcore.api.research.ResearchState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 3x3x3 research multiblock. Runs Factorio-style research: each run consumes materials (from the controller's
 * input slots) + CWU (from a Computation Reception Hatch wired to a Mainframe) at a constant power draw,
 * advancing completion one run at a time. Completed researches are written as research-id data sticks into the
 * output slot, and existing research data sticks placed in the input slot are recognised on import (plain
 * GregTech data IO).
 *
 * <p>
 * TODO(warforge): the 1.12.2 build also bridged research/data access to WarForge factions
 * (FactionLibraryAccess + an Assembly Line mixin), so any machine in a faction's claimed territory could share
 * the blueprint library. WarForge is not ported to 1.20.1 yet; rework this to honour faction library access
 * once it is. For now this falls back to plain GregTech data IO (local data sticks + Data Access Hatches).
 */
public class ResearchUnitMachine extends MultiblockControllerMachine
                                 implements IOpticalComputationReceiver, IControllable, IFancyUIMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ResearchUnitMachine.class,
            MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    private static final int QUEUE_SIZE = 3;
    private static final int CAPACITY = 1; // TODO(slaves): SLAVE units adding parallel slots not yet ported
    private static final String STICK_KEY = "wfcore_research";

    @Persisted
    protected final NotifiableItemStackHandler materialsInv;
    @Persisted
    protected final NotifiableItemStackHandler dataInv;

    private final ResearchState state = new ResearchState();
    private final List<Job> jobs = new ArrayList<>();

    @Persisted
    @DescSynced
    private boolean isWorkingEnabled = true;
    @DescSynced
    private String selectedResearchId = "";
    @DescSynced
    private String activeResearchId = "";
    @DescSynced
    private float activeProgress;
    @DescSynced
    private boolean selectedComplete;
    @DescSynced
    private boolean selectedUnlocked;

    private IEnergyContainer energyContainer = new EnergyContainerList(new ArrayList<>());
    @Nullable
    private IOpticalComputationProvider computationProvider;
    @Nullable
    private TickableSubscription tickSub;
    private long tickCounter;

    public ResearchUnitMachine(IMachineBlockEntity holder) {
        super(holder);
        this.materialsInv = new NotifiableItemStackHandler(this, 9, IO.IN);
        this.dataInv = new NotifiableItemStackHandler(this, 2, IO.NONE)
                .setFilter(s -> s.is(GTItems.TOOL_DATA_STICK.asItem()));
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
        this.computationProvider = null;
        for (IMultiPart part : getParts()) {
            part.self().holder.self().getCapability(GTCapability.CAPABILITY_COMPUTATION_PROVIDER)
                    .ifPresent(p -> this.computationProvider = p);
            for (var handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(IO.IN)) continue;
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(energy::add);
            }
        }
        this.energyContainer = new EnergyContainerList(energy);
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickResearch);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = new EnergyContainerList(new ArrayList<>());
        this.computationProvider = null;
        this.jobs.clear();
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    @Override
    @Nullable
    public IOpticalComputationProvider getComputationProvider() {
        return computationProvider;
    }

    //////////////////// ticking ////////////////////

    protected void tickResearch() {
        if (isRemote() || !isFormed()) return;
        if (++tickCounter % 40 == 0) {
            importCompletedFromSticks();
        }
        if (!isWorkingEnabled || jobs.isEmpty()) {
            activeResearchId = "";
            activeProgress = 0;
            return;
        }

        int activeCount = Math.min(jobs.size(), CAPACITY);
        for (int i = activeCount - 1; i >= 0; i--) {
            Job job = jobs.get(i);
            Research research = ResearchRegistry.get(job.researchId);
            if (research == null) {
                jobs.remove(i);
                continue;
            }
            processJob(job, research);
            if (state.isComplete(job.researchId)) {
                completeResearch(research);
                jobs.remove(i);
            }
        }
        Job head = jobs.isEmpty() ? null : jobs.get(0);
        activeResearchId = head == null ? "" : head.researchId;
        activeProgress = head == null ? 0 : state.getProgress(head.researchId);
    }

    private void processJob(Job job, Research research) {
        boolean needsCompute = research.getCwuPerRun() > 0;
        if (needsCompute && (computationProvider == null || computationProvider.requestCWUt(
                (int) Math.min(research.getCwuPerRun(), Integer.MAX_VALUE), true) <= 0)) {
            return; // no computation - stall without consuming items/energy
        }
        if (!job.materialsConsumed) {
            if (!consumeMaterials(research.getItemsPerRun())) return;
            job.materialsConsumed = true;
        }
        if (!drawEnergy(research.getEut(), false)) return;

        if (needsCompute) {
            long remaining = research.getCwuPerRun() - job.accumulatedCWU;
            long perTick = Math.max(1,
                    (research.getCwuPerRun() + research.getTicksPerRun() - 1) / research.getTicksPerRun());
            int request = (int) Math.min(Math.min(perTick, remaining), Integer.MAX_VALUE);
            job.accumulatedCWU += computationProvider.requestCWUt(request, false);
        }
        job.elapsedTicks++;

        if (job.accumulatedCWU >= research.getCwuPerRun() && job.elapsedTicks >= research.getTicksPerRun()) {
            state.setCompletedRuns(research.getId(), state.getCompletedRuns(research.getId()) + 1);
            state.setPartialCWU(research.getId(), 0);
            job.accumulatedCWU = 0;
            job.elapsedTicks = 0;
            job.materialsConsumed = false;
        }
    }

    private void completeResearch(Research research) {
        state.setCompletedRuns(research.getId(), research.getRunsRequired());
        state.setPartialCWU(research.getId(), 0);
        if (research.hasBlueprint()) {
            writeResearchDataStick(research.getId());
        }
    }

    //////////////////// data stick IO (plain GT data IO) ////////////////////

    private void writeResearchDataStick(String researchId) {
        if (!dataInv.getStackInSlot(1).isEmpty()) return;
        ItemStack stick = GTItems.TOOL_DATA_STICK.asStack();
        stick.getOrCreateTag().putString(STICK_KEY, researchId);
        dataInv.setStackInSlot(1, stick);
    }

    private void importCompletedFromSticks() {
        for (int slot = 0; slot < dataInv.getSlots(); slot++) {
            ItemStack stack = dataInv.getStackInSlot(slot);
            if (stack.isEmpty() || !stack.hasTag()) continue;
            String id = stack.getTag().getString(STICK_KEY);
            if (id.isEmpty()) continue;
            Research research = ResearchRegistry.get(id);
            if (research != null && !state.isComplete(id)) {
                state.setCompletedRuns(id, research.getRunsRequired());
            }
        }
    }

    //////////////////// player actions ////////////////////

    public void setSelected(String researchId) {
        this.selectedResearchId = researchId == null ? "" : researchId;
        this.selectedComplete = state.isComplete(selectedResearchId);
        this.selectedUnlocked = state.isUnlocked(selectedResearchId);
    }

    public void toggleSelected() {
        if (selectedResearchId.isEmpty()) return;
        if (isResearching(selectedResearchId)) {
            dequeue(selectedResearchId);
        } else {
            enqueue(selectedResearchId);
        }
    }

    public boolean isResearching(String researchId) {
        for (Job job : jobs) if (job.researchId.equals(researchId)) return true;
        return false;
    }

    public boolean enqueue(String researchId) {
        if (!isFormed() || researchId == null) return false;
        Research research = ResearchRegistry.get(researchId);
        if (research == null || state.isComplete(researchId) || !state.isUnlocked(researchId)) return false;
        if (isResearching(researchId) || jobs.size() >= QUEUE_SIZE) return false;
        Job job = new Job(researchId);
        job.accumulatedCWU = state.getPartialCWU(researchId);
        jobs.add(job);
        return true;
    }

    public boolean dequeue(String researchId) {
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).researchId.equals(researchId)) {
                state.setPartialCWU(researchId, jobs.get(i).accumulatedCWU);
                jobs.remove(i);
                return true;
            }
        }
        return false;
    }

    //////////////////// helpers ////////////////////

    private boolean consumeMaterials(List<ItemStack> costs) {
        if (costs.isEmpty()) return true;
        for (ItemStack cost : costs) {
            if (countMaterial(cost) < cost.getCount()) return false;
        }
        for (ItemStack cost : costs) {
            extractMaterial(cost, cost.getCount());
        }
        return true;
    }

    private int countMaterial(ItemStack target) {
        int count = 0;
        for (int i = 0; i < materialsInv.getSlots(); i++) {
            ItemStack slot = materialsInv.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, target)) count += slot.getCount();
        }
        return count;
    }

    private void extractMaterial(ItemStack target, int amount) {
        for (int i = 0; i < materialsInv.getSlots() && amount > 0; i++) {
            ItemStack slot = materialsInv.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, target)) {
                amount -= materialsInv.storage.extractItem(i, amount, false).getCount();
            }
        }
    }

    private boolean drawEnergy(long eut, boolean simulate) {
        if (eut <= 0) return true;
        if (energyContainer.getEnergyStored() >= eut) {
            if (!simulate) energyContainer.removeEnergy(eut);
            return true;
        }
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

    //////////////////// persistence ////////////////////

    @Override
    public void saveCustomPersistedData(@NotNull CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.put("ResearchState", state.serializeNBT());
        ListTag jobList = new ListTag();
        for (Job job : jobs) jobList.add(job.serializeNBT());
        tag.put("Jobs", jobList);
    }

    @Override
    public void loadCustomPersistedData(@NotNull CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        state.deserializeNBT(tag.getCompound("ResearchState"));
        jobs.clear();
        ListTag jobList = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobList.size(); i++) {
            jobs.add(Job.fromNBT(jobList.getCompound(i)));
        }
    }

    //////////////////// UI ////////////////////

    public void addDisplayText(List<net.minecraft.network.chat.Component> textList) {
        if (!isFormed()) {
            textList.add(net.minecraft.network.chat.Component.translatable("gtceu.multiblock.invalid_structure"));
            return;
        }
        if (!activeResearchId.isEmpty()) {
            Research r = ResearchRegistry.get(activeResearchId);
            String name = r == null ? activeResearchId : net.minecraft.network.chat.Component
                    .translatable(r.getNameKey()).getString();
            textList.add(net.minecraft.network.chat.Component.literal(
                    String.format("§bResearching %s (%.0f%%)", name, activeProgress * 100)));
        } else {
            textList.add(net.minecraft.network.chat.Component.translatable("gtceu.multiblock.idling"));
        }
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 200, 140);

        DraggableScrollableWidgetGroup list = new DraggableScrollableWidgetGroup(4, 4, 88, 100)
                .setBackground(GuiTextures.DISPLAY);
        int y = 2;
        for (Research research : ResearchRegistry.all()) {
            String id = research.getId();
            list.addWidget(new ButtonWidget(2, y, 82, 14, GuiTextures.BUTTON, d -> onSelect(d, id)));
            list.addWidget(new LabelWidget(5, y + 3, research.getNameKey()));
            y += 16;
        }
        group.addWidget(list);

        group.addWidget(new LabelWidget(96, 6, this::getDetailName));
        group.addWidget(new LabelWidget(96, 18, this::getDetailStats).setTextColor(-1));
        group.addWidget(new LabelWidget(96, 30, this::getDetailState).setTextColor(-1));
        group.addWidget(new LabelWidget(96, 42, this::getActiveText).setTextColor(-1));
        group.addWidget(new ButtonWidget(96, 56, 60, 18, GuiTextures.BUTTON, this::onStartCancel)
                .setHoverTooltips("wfcore.gui.research.start"));
        group.addWidget(new LabelWidget(102, 61, "wfcore.gui.research.start"));

        // material input grid (3x3) + data sticks
        for (int i = 0; i < 9; i++) {
            int sx = 4 + (i % 3) * 18;
            int sy = 110 + (i / 3) * 0; // single row to keep compact
            group.addWidget(new SlotWidget(materialsInv, i, 4 + i * 18, 110).setBackgroundTexture(GuiTextures.SLOT));
        }
        group.addWidget(new SlotWidget(dataInv, 0, 168, 92).setBackgroundTexture(GuiTextures.SLOT));
        group.addWidget(new SlotWidget(dataInv, 1, 168, 112).setBackgroundTexture(GuiTextures.SLOT));
        return group;
    }

    private void onSelect(ClickData data, String id) {
        if (isRemote()) return;
        setSelected(id);
    }

    private void onStartCancel(ClickData data) {
        if (isRemote()) return;
        toggleSelected();
    }

    private String getDetailName() {
        Research r = ResearchRegistry.get(selectedResearchId);
        return r == null ? "§7-" : net.minecraft.network.chat.Component.translatable(r.getNameKey()).getString();
    }

    private String getDetailStats() {
        Research r = ResearchRegistry.get(selectedResearchId);
        if (r == null) return "";
        return String.format("§7%d runs · %d CWU · %d EU/t", r.getRunsRequired(), r.getTotalCWU(), r.getEut());
    }

    private String getDetailState() {
        if (selectedResearchId.isEmpty()) return "";
        if (selectedComplete) return "§aComplete";
        if (!selectedUnlocked) return "§cLocked";
        return isResearching(selectedResearchId) ? "§eQueued" : "§bReady";
    }

    private String getActiveText() {
        if (activeResearchId.isEmpty()) return "";
        return String.format("§b%.0f%%", activeProgress * 100);
    }

    //////////////////// job ////////////////////

    public static final class Job {

        public final String researchId;
        public long accumulatedCWU;
        public int elapsedTicks;
        public boolean materialsConsumed;

        public Job(String researchId) {
            this.researchId = researchId;
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", researchId);
            tag.putLong("cwu", accumulatedCWU);
            tag.putInt("ticks", elapsedTicks);
            tag.putBoolean("mat", materialsConsumed);
            return tag;
        }

        public static Job fromNBT(CompoundTag tag) {
            Job job = new Job(tag.getString("id"));
            job.accumulatedCWU = tag.getLong("cwu");
            job.elapsedTicks = tag.getInt("ticks");
            job.materialsConsumed = tag.getBoolean("mat");
            return job;
        }
    }
}
