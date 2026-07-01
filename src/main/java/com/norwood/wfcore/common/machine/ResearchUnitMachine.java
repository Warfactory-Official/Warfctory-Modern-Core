package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.items.IItemHandlerModifiable;

import brachy.modularui.factory.BlockEntityUIFactory;
import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchDataItem;
import com.norwood.wfcore.api.research.ResearchRegistry;
import com.norwood.wfcore.api.research.ResearchState;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3x3x3 research multiblock. In CONTROL mode it presents the research tree and runs Factorio-style research:
 * each run consumes materials (from an Item Input Bus) + CWU (from a Computation Reception Hatch wired to a
 * Mainframe) at a constant power draw, advancing completion one run at a time. SLAVE units placed within a
 * short radius of a CONTROL unit lend it parallel research slots (toggle the mode with a screwdriver).
 *
 * <p>
 * The faction-wide blueprint sharing from the 1.12.2 build is ported in
 * {@code com.norwood.wfcore.integration.warforge} (FactionLibraryAccess + the Assembly Line research mixin): a
 * research unit holding any data here lets the owning faction's Assembly Lines run research-gated recipes
 * anywhere in their loaded claims. Completion is recorded in this controller's {@link ResearchState} (which
 * unlocks downstream nodes), and externally-completed research is imported by recognising research data sticks
 * in the input bus.
 */
public class ResearchUnitMachine extends MultiblockControllerMachine
                                 implements IOpticalComputationReceiver, IControllable, IInteractedMachine,
                                 IMachineLife {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ResearchUnitMachine.class,
            MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    public enum Mode {
        CONTROL,
        SLAVE
    }

    public static final int QUEUE_SIZE = 3;
    private static final int CLUSTER_SCAN_RADIUS = 4;
    private static final int MAX_SLAVES = 16;
    public static final String STICK_KEY = ResearchDataItem.KEY;

    private final List<IItemHandlerModifiable> inputInventories = new ArrayList<>();
    private final ResearchState state = new ResearchState();
    private final List<Job> jobs = new ArrayList<>();
    /** Single "library" slot: a data item/paper the player writes obtained blueprints onto from the GUI. */
    @Persisted
    private final NotifiableItemStackHandler libraryInv;

    @Persisted
    @DescSynced
    private boolean isWorkingEnabled = true;
    @Persisted
    @DescSynced
    private boolean slaveMode = false;
    @DescSynced
    private int slaveCount;
    /** Client snapshot of {@link #state} + queue (parsed lazily by the research-tree GUI). */
    @DescSynced
    private final ResearchSyncData researchSync = new ResearchSyncData();

    // server-side: which research the GUI side-panel buttons act on (last clicked node)
    private String selectedResearchId = "";
    // server-side: which obtained research the library window will write to the slotted data item
    private String librarySelectedId = "";

    // client-only parse cache of researchSync
    private CompoundTag parsedFrom;
    private final ResearchState clientState = new ResearchState();
    private final List<String> clientQueue = new ArrayList<>();
    private final Map<String, Float> clientProgress = new HashMap<>();

    private IEnergyContainer energyContainer = new EnergyContainerList(new ArrayList<>());
    @Nullable
    private IOpticalComputationProvider computationProvider;
    @Nullable
    private TickableSubscription tickSub;
    private long tickCounter;

    /** Lowest energy-hatch tier the research unit will run at; below this it forms but won't process. */
    private static final int MIN_TIER = GTValues.LV;
    private int voltageTier = -1;

    public ResearchUnitMachine(IMachineBlockEntity holder) {
        super(holder);
        this.libraryInv = new NotifiableItemStackHandler(this, 1, IO.IN).setFilter(ResearchDataItem::isDataItem);
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
        this.inputInventories.clear();
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
                handlerList.getCapability(ItemRecipeCapability.CAP).stream()
                        .filter(IItemHandlerModifiable.class::isInstance)
                        .map(IItemHandlerModifiable.class::cast)
                        .forEach(inputInventories::add);
            }
        }
        this.energyContainer = new EnergyContainerList(energy);
        this.voltageTier = GTUtil.getTierByVoltage(energyContainer.getInputVoltage());
        pushResearchSync();
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickResearch);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = new EnergyContainerList(new ArrayList<>());
        this.voltageTier = -1;
        this.inputInventories.clear();
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

    //////////////////// slave/control clustering ////////////////////

    public Mode getMode() {
        return slaveMode ? Mode.SLAVE : Mode.CONTROL;
    }

    /** Parallel research slots = 1 (the CONTROL itself) + one per adjacent formed SLAVE unit. */
    public int getJobCapacity() {
        return 1 + slaveCount;
    }

    public int getSlaveCount() {
        return slaveCount;
    }

    /**
     * Scans a cubic {@value CLUSTER_SCAN_RADIUS}-block radius for formed SLAVE research units and counts them
     * (up to {@value MAX_SLAVES}). Only a CONTROL unit scans; a SLAVE just passively lends its slot.
     */
    private void recomputeSlaveCount() {
        if (slaveMode || getLevel() == null) {
            this.slaveCount = 0;
            return;
        }
        int slaves = 0;
        BlockPos origin = getPos();
        for (int dx = -CLUSTER_SCAN_RADIUS; dx <= CLUSTER_SCAN_RADIUS && slaves < MAX_SLAVES; dx++) {
            for (int dy = -CLUSTER_SCAN_RADIUS; dy <= CLUSTER_SCAN_RADIUS && slaves < MAX_SLAVES; dy++) {
                for (int dz = -CLUSTER_SCAN_RADIUS; dz <= CLUSTER_SCAN_RADIUS && slaves < MAX_SLAVES; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (MetaMachine.getMachine(getLevel(),
                            origin.offset(dx, dy, dz)) instanceof ResearchUnitMachine unit && unit != this &&
                            unit.slaveMode && unit.isFormed()) {
                        slaves++;
                    }
                }
            }
        }
        this.slaveCount = slaves;
    }

    /** Screwdriver toggles CONTROL &lt;-&gt; SLAVE. Becoming a SLAVE clears any queued research. */
    @Override
    protected InteractionResult onScrewdriverClick(Player player, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (isRemote()) return InteractionResult.SUCCESS;
        this.slaveMode = !slaveMode;
        if (slaveMode) jobs.clear();
        recomputeSlaveCount();
        pushResearchSync();
        markDirty();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                slaveMode ? "wfcore.gui.research.slave_mode" : "wfcore.gui.research.control_mode"), true);
        return InteractionResult.CONSUME;
    }

    //////////////////// ticking ////////////////////

    protected void tickResearch() {
        if (isRemote() || !isFormed()) return;
        if (++tickCounter % 40 == 0) {
            recomputeSlaveCount();
            importCompletedFromSticks();
        }
        // SLAVE units only lend parallel slots to an adjacent CONTROL; they never process themselves.
        if (slaveMode || !isWorkingEnabled || jobs.isEmpty()) {
            return;
        }
        // Formed but under-powered: an energy hatch below LV won't drive the research logic.
        if (voltageTier < MIN_TIER) {
            return;
        }

        // only the first `capacity` queued researches run concurrently; the rest wait their turn
        int activeCount = Math.min(jobs.size(), getJobCapacity());
        boolean changed = false;
        for (int i = activeCount - 1; i >= 0; i--) {
            Job job = jobs.get(i);
            Research research = ResearchRegistry.get(job.researchId);
            if (research == null) {
                jobs.remove(i);
                changed = true;
                continue;
            }
            if (processJob(job, research)) changed = true;
            if (state.isComplete(job.researchId)) {
                completeResearch(research);
                jobs.remove(i);
                changed = true;
            }
        }
        if (changed || tickCounter % 10 == 0) pushResearchSync();
    }

    /** Advances a job one tick; returns true if it made progress (so the client snapshot is refreshed). */
    private boolean processJob(Job job, Research research) {
        boolean needsCompute = research.getCwuPerRun() > 0;
        if (needsCompute && (computationProvider == null || computationProvider.requestCWUt(
                (int) Math.min(research.getCwuPerRun(), Integer.MAX_VALUE), true) <= 0)) {
            return false; // no computation - stall without consuming items/energy
        }
        if (!job.materialsConsumed) {
            if (!consumeMaterials(research.getItemsPerRun())) return false;
            job.materialsConsumed = true;
        }
        if (!drawEnergy(research.getEut(), false)) return false;

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
        return true;
    }

    private void completeResearch(Research research) {
        state.setCompletedRuns(research.getId(), research.getRunsRequired());
        state.setPartialCWU(research.getId(), 0);
        if (WarforgeIntegration.isLoaded()) {
            WarforgeNotifications.researchCompleted(getLevel(), getPos(), research);
        }
    }

    /** Marks any research whose data item is present in the input bus as complete (external import). */
    private void importCompletedFromSticks() {
        for (IItemHandlerModifiable inv : inputInventories) {
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                for (String id : ResearchDataItem.readAll(inv.getStackInSlot(slot))) {
                    Research research = ResearchRegistry.get(id);
                    if (research != null && !state.isComplete(id)) {
                        state.setCompletedRuns(id, research.getRunsRequired());
                        pushResearchSync();
                    }
                }
            }
        }
    }

    //////////////////// library (blueprint write) ////////////////////

    public NotifiableItemStackHandler getLibraryInv() {
        return libraryInv;
    }

    /** Picks which obtained research the library window's write button will imprint (set from the GUI list). */
    public void selectLibrary(String researchId) {
        this.librarySelectedId = researchId == null ? "" : researchId;
    }

    /**
     * Writes the selected obtained research onto the data item/paper in the library slot, appending to the next
     * free entry if it already holds blueprints. The research must have its full path complete. Returns true
     * when something was written.
     */
    public boolean writeLibrary() {
        if (librarySelectedId.isEmpty() || !state.isPathComplete(librarySelectedId)) return false;
        if (ResearchRegistry.get(librarySelectedId) == null) return false;
        ItemStack item = libraryInv.storage.getStackInSlot(0);
        if (!ResearchDataItem.write(item, librarySelectedId)) return false;
        libraryInv.storage.setStackInSlot(0, item);
        markDirty();
        return true;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(libraryInv.storage);
    }

    //////////////////// player actions ////////////////////

    public void setSelected(String researchId) {
        this.selectedResearchId = researchId == null ? "" : researchId;
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
        if (slaveMode || !isFormed() || researchId == null) return false;
        Research research = ResearchRegistry.get(researchId);
        if (research == null || state.isComplete(researchId) || !state.isUnlocked(researchId)) return false;
        if (isResearching(researchId) || jobs.size() >= QUEUE_SIZE) return false;
        Job job = new Job(researchId);
        job.accumulatedCWU = state.getPartialCWU(researchId);
        jobs.add(job);
        pushResearchSync();
        return true;
    }

    public boolean dequeue(String researchId) {
        for (int i = 0; i < jobs.size(); i++) {
            if (jobs.get(i).researchId.equals(researchId)) {
                return dequeueAt(i);
            }
        }
        return false;
    }

    /** Removes the job at the given queue index (clicked queue slot), banking its partial progress. */
    public boolean dequeueAt(int index) {
        if (index < 0 || index >= jobs.size()) return false;
        Job job = jobs.get(index);
        state.setPartialCWU(job.researchId, job.accumulatedCWU);
        jobs.remove(index);
        pushResearchSync();
        return true;
    }

    //////////////////// material helpers (read from the input bus) ////////////////////

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
        for (IItemHandlerModifiable inv : inputInventories) {
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slot = inv.getStackInSlot(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, target)) count += slot.getCount();
            }
        }
        return count;
    }

    private void extractMaterial(ItemStack target, int amount) {
        for (IItemHandlerModifiable inv : inputInventories) {
            for (int i = 0; i < inv.getSlots() && amount > 0; i++) {
                ItemStack slot = inv.getStackInSlot(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, target)) {
                    amount -= inv.extractItem(i, amount, false).getCount();
                }
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

    /** GUI working-enabled toggle (server-side); persists and re-syncs the new state. */
    public void toggleWorkingEnabled() {
        this.isWorkingEnabled = !isWorkingEnabled;
        markDirty();
    }

    //////////////////// client snapshot for the research-tree GUI ////////////////////

    /** Rebuilds the {@link #researchSync} snapshot (state + queue) sent to clients for the tree GUI. */
    private void pushResearchSync() {
        CompoundTag tag = new CompoundTag();
        tag.put("state", state.serializeNBT());
        ListTag queue = new ListTag();
        for (Job job : jobs) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", job.researchId);
            entry.putFloat("p", state.getProgress(job.researchId));
            queue.add(entry);
        }
        tag.put("queue", queue);
        this.researchSync.setTag(tag);
    }

    private void ensureParsed() {
        if (researchSync.tag == parsedFrom) return;
        parsedFrom = researchSync.tag;
        clientState.deserializeNBT(researchSync.tag.getCompound("state"));
        clientQueue.clear();
        clientProgress.clear();
        ListTag queue = researchSync.tag.getList("queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < queue.size(); i++) {
            CompoundTag entry = queue.getCompound(i);
            String id = entry.getString("id");
            clientQueue.add(id);
            clientProgress.put(id, entry.getFloat("p"));
        }
    }

    /** Research progress for the GUI: the live state on the server, the synced snapshot on the client. */
    public ResearchState getResearchState() {
        if (!isRemote()) return state;
        ensureParsed();
        return clientState;
    }

    public List<String> getClientQueue() {
        ensureParsed();
        return clientQueue;
    }

    public float getClientProgress(String researchId) {
        ensureParsed();
        return clientProgress.getOrDefault(researchId, 0f);
    }

    public boolean isQueuedClient(String researchId) {
        ensureParsed();
        return clientQueue.contains(researchId);
    }

    public boolean isActiveClient(String researchId) {
        ensureParsed();
        int idx = clientQueue.indexOf(researchId);
        return idx >= 0 && idx < getJobCapacity();
    }

    //////////////////// interaction (open the brachy research-tree GUI) ////////////////////

    @Override
    public InteractionResult onUse(BlockState blockState, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!isRemote() && getHolder().self() instanceof ResearchUnitBlockEntity) {
            pushResearchSync();
            // open(player, BlockPos) — NOT open(player, blockEntity): the latter verifies against the
            // client player (MCHelper.getPlayer()) and throws a dimension mismatch when called server-side.
            BlockEntityUIFactory.INSTANCE.open(player, pos);
        }
        return InteractionResult.SUCCESS;
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
        pushResearchSync();
    }

    /** LDLib-syncable wrapper around the client research snapshot tag (state + queue). */
    public static final class ResearchSyncData implements ITagSerializable<CompoundTag>, IContentChangeAware {

        private CompoundTag tag = new CompoundTag();
        private Runnable onContentsChanged = () -> {};

        public void setTag(CompoundTag tag) {
            this.tag = tag;
            onContentsChanged.run();
        }

        @Override
        public CompoundTag serializeNBT() {
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            this.tag = nbt;
        }

        @Override
        public void setOnContentsChanged(Runnable onContentsChanged) {
            this.onContentsChanged = onContentsChanged;
        }

        @Override
        public Runnable getOnContentsChanged() {
            return onContentsChanged;
        }
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
