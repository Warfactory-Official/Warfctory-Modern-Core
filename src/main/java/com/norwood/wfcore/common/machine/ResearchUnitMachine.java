package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.DataBankMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DataAccessHatchMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalDataHatchMachine;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeNet;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import brachy.modularui.factory.BlockEntityUIFactory;
import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchDataItem;
import com.norwood.wfcore.api.research.ResearchRegistry;
import com.norwood.wfcore.api.research.ResearchState;
import com.norwood.wfcore.client.render.gltf.IAnimatedMachine;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 3x3x3 research multiblock. In CONTROL mode it presents the research tree and runs Factorio-style research:
 * each run consumes materials (from an Item Input Bus) + CWU (from a Computation Reception Hatch wired to a
 * Mainframe) at a constant power draw, advancing completion one run at a time. SLAVE units lend a CONTROL unit
 * one parallel research slot each when their Computation Reception Hatch is wired to the same optical-pipe
 * (data-cable) network as the CONTROL's — range-independent, not proximity-based (toggle the mode with a
 * screwdriver).
 *
 * <p>
 * The faction-wide blueprint sharing from the 1.12.2 build is ported in
 * {@code com.norwood.wfcore.integration.warforge} (FactionLibraryAccess + the Assembly Line research mixin): a
 * research unit holding any data here lets the owning faction's Assembly Lines run research-gated recipes
 * anywhere in their loaded claims. Completion is recorded in this controller's {@link ResearchState} (which
 * unlocks downstream nodes), and externally-completed research is imported by recognising research data sticks
 * in the input bus.
 *
 * <p>
 * A CONTROL also two-way syncs with any GTCEu Data Bank wired to its optical network (see
 * {@link #syncDataBanks}): it imports all research stored on the bank's data sticks/orbs and writes its own
 * completed research back onto them, so a team's Data Bank is a live shared library - a freshly-connected unit
 * inherits everything the team has researched instead of grinding it again.
 */
public class ResearchUnitMachine extends MultiblockControllerMachine
                                 implements IOpticalComputationReceiver, IControllable, IInteractedMachine,
                                 IMachineLife, IAnimatedMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ResearchUnitMachine.class,
            MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    public enum Mode {
        CONTROL,
        SLAVE
    }

    /**
     * The controller's live processing state, synced to the GUI status screen so it reflects reality instead of a
     * blanket "working": an active research can be stalled waiting on input materials, computation or energy.
     */
    public enum RunStatus {
        PAUSED,
        IDLE,
        WORKING,
        WAITING_MATERIALS,
        WAITING_COMPUTE,
        WAITING_ENERGY
    }

    public static final int QUEUE_SIZE = 3;
    private static final int MAX_SLAVES = 16;
    /** GT-style progress decay: a stalled run loses this many ticks (and a tick's worth of CWU) of the current
     * step per tick it can't be computed/powered, so the step bar bleeds back down instead of freezing. */
    private static final int STEP_DECAY = 2;
    public static final String STICK_KEY = ResearchDataItem.KEY;

    private final List<IItemHandlerModifiable> inputInventories = new ArrayList<>();
    /** Import fluid-hatch tanks the research draws its per-run fluid costs from. */
    private final List<IFluidHandler> inputFluidTanks = new ArrayList<>();
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
    /** Client-synced: whether the model's animation clock should advance this tick (actively researching). */
    @DescSynced
    private boolean animAdvancing = false;
    /** Client-synced processing state driving the GUI status screen (ordinal of {@link RunStatus}). */
    @DescSynced
    private int runStatus = RunStatus.IDLE.ordinal();
    /** Client snapshot of {@link #state} + queue (parsed lazily by the research-tree GUI). */
    @DescSynced
    private final ResearchSyncData researchSync = new ResearchSyncData();

    // server-side: which research the GUI side-panel buttons act on (last clicked node)
    private String selectedResearchId = "";
    // server-side: which obtained research the library window will write to the slotted data item
    private String librarySelectedId = "";
    /** Server-side: research ids currently stored on a connected Data Bank; synced to the GUI via researchSync. */
    private final Set<String> bankResearchIds = new HashSet<>();

    // client-only parse cache of researchSync
    private CompoundTag parsedFrom;
    private final ResearchState clientState = new ResearchState();
    private final List<String> clientQueue = new ArrayList<>();
    private final Map<String, Float> clientProgress = new HashMap<>();
    /** Client parse cache of each queued research's current-run (step) completion, keyed by research id. */
    private final Map<String, Float> clientStepProgress = new HashMap<>();
    /** Client parse cache of {@link #bankResearchIds} (research available in a connected Data Bank). */
    private final Set<String> clientBankResearch = new HashSet<>();

    /** Input energy, gathered from the parts. Null while unformed / not yet gathered (rebuilt lazily). */
    @Nullable
    private EnergyContainerList energyContainer;
    @Nullable
    private IOpticalComputationProvider computationProvider;
    /** Position of the Computation Reception Hatch part, used to find the optical-pipe net slaves link over. */
    @Nullable
    private BlockPos receptionHatchPos;
    /** Position of the optional Optical Data Transmission Hatch part, used to find the net a Data Bank is wired
     * to (may be a different optical line than the computation one). Null when the unit has no data hatch. */
    @Nullable
    private BlockPos opticalDataHatchPos;
    @Nullable
    private TickableSubscription tickSub;
    private long tickCounter;

    public ResearchUnitMachine(IMachineBlockEntity holder) {
        super(holder);
        this.libraryInv = new NotifiableItemStackHandler(this, 1, IO.IN).setFilter(ResearchDataItem::isDataItem);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    //////////////////// structure lifecycle ////////////////////

    /**
     * Subscribe the research tick on load rather than in {@link #onStructureFormed()}, mirroring how GTCEu's
     * {@link com.gregtechceu.gtceu.api.machine.trait.RecipeLogic} subscribes in {@code onMachineLoad}. The tick
     * body itself gates on {@link #isFormed()}, so the subscription is established once and survives structure
     * reform/invalidation and world reloads. Subscribing only in {@code onStructureFormed} was the bug: that
     * hook isn't re-invoked on a plain reload of an already-formed controller (its {@code isFormed} persists),
     * and any earlier throw in it skips the subscribe while {@code isFormed} is already {@code true} — leaving a
     * formed machine that never ticks.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && tickSub == null) {
            tickSub = subscribeServerTick(this::tickResearch);
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        // Gather the input energy + item handlers and the computation provider from the parts, as base GT's
        // ResearchStationMachine does: iterate every part and pull the capability off it. NB: we do NOT reset the
        // provider to null first nor invalidate the structure when it's momentarily absent — a plain controller
        // re-runs onStructureFormed on every structure refresh, so nulling+invalidating there tore the unit down.
        List<IEnergyContainer> energy = new ArrayList<>();
        this.inputInventories.clear();
        this.inputFluidTanks.clear();
        for (IMultiPart part : getParts()) {
            part.self().holder.self().getCapability(GTCapability.CAPABILITY_COMPUTATION_PROVIDER)
                    .ifPresent(p -> {
                        this.computationProvider = p;
                        this.receptionHatchPos = part.self().getPos();
                    });
            if (part.self() instanceof OpticalDataHatchMachine) {
                this.opticalDataHatchPos = part.self().getPos();
            }
            for (var handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(IO.IN)) continue;
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(energy::add);
                // An import bus exposes a NotifiableItemStackHandler whose extractItem() is gated by
                // canCapOutput() (false for an input-only cap), so calling it silently removes nothing - which
                // is why research never consumed its item costs. Grab the backing storage handler instead, whose
                // extractItem() really removes (this is what GT's own recipe I/O drains).
                for (Object cap : handlerList.getCapability(ItemRecipeCapability.CAP)) {
                    if (cap instanceof NotifiableItemStackHandler handler) {
                        inputInventories.add(handler.storage);
                    } else if (cap instanceof IItemHandlerModifiable handler) {
                        inputInventories.add(handler);
                    }
                }
                // Same for import fluid hatches: the NotifiableFluidTank's public drain() is output-gated, so
                // pull the backing CustomFluidTank storages, whose drain() actually removes fluid.
                for (Object cap : handlerList.getCapability(FluidRecipeCapability.CAP)) {
                    if (cap instanceof NotifiableFluidTank tank) {
                        for (IFluidHandler storage : tank.getStorages()) inputFluidTanks.add(storage);
                    } else if (cap instanceof IFluidHandler tank) {
                        inputFluidTanks.add(tank);
                    }
                }
            }
        }
        this.energyContainer = new EnergyContainerList(energy);
        pushResearchSync();
        // Note: the tick is subscribed in onLoad(), not here — see onLoad() for why.
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = null;
        this.inputInventories.clear();
        this.inputFluidTanks.clear();
        this.computationProvider = null;
        this.receptionHatchPos = null;
        this.opticalDataHatchPos = null;
        this.bankResearchIds.clear();
        this.animAdvancing = false;
        this.runStatus = RunStatus.IDLE.ordinal();
        // The tick subscription is intentionally left alive (see onLoad()); tickResearch() no-ops while unformed.
        // Jobs are NOT cleared: the queue is banked and resumes when the structure re-forms (e.g. after the
        // provider-null re-form above), so a transient invalidation never wipes the player's research queue.
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
     * Walks the optical-pipe net(s) this CONTROL is wired to, doing two things at once:
     * <ul>
     * <li>counts formed SLAVE research units whose Computation Reception Hatch shares the net (up to
     * {@value MAX_SLAVES}), so linking is range-independent (each lends one parallel slot); and</li>
     * <li>two-way research syncs with every GTCEu Data Bank on the net (see {@link #syncDataBanks}).</li>
     * </ul>
     * Both the Computation Reception Hatch and the optional Optical Data Hatch are scanned - they may be on the
     * same fibre or on separate ones, so their nets are walked and deduped. Only a CONTROL scans; a SLAVE
     * passively lends its slot.
     */
    private void scanOpticalNetwork() {
        Level level = getLevel();
        if (slaveMode || level == null) {
            this.slaveCount = 0;
            return;
        }
        Set<BlockPos> slaves = new HashSet<>();
        Set<DataBankMachine> banks = new HashSet<>();
        Set<OpticalPipeNet> scanned = new HashSet<>();
        // The computation fibre carries slave links; the data fibre carries Data Banks. They can be one and the
        // same line, so dedupe the nets. A slave has its own reception hatch on the shared computation net; a
        // Data Bank broadcasts through an Optical Data Hatch, reached via this unit's own Optical Data Hatch.
        for (BlockPos hatchPos : new BlockPos[] { receptionHatchPos, opticalDataHatchPos }) {
            if (hatchPos == null) continue;
            OpticalPipeNet net = findConnectedOpticalNet(hatchPos);
            if (net != null && scanned.add(net)) walkOpticalNet(level, net, slaves, banks);
        }
        this.slaveCount = Math.min(slaves.size(), MAX_SLAVES);
        if (!banks.isEmpty()) {
            syncDataBanks(banks);
        } else if (!bankResearchIds.isEmpty()) {
            bankResearchIds.clear();
            pushResearchSync();
        }
    }

    /** Collects slave research units + Data Banks touching {@code net} into the given sets. */
    private void walkOpticalNet(Level level, OpticalPipeNet net, Set<BlockPos> slaves, Set<DataBankMachine> banks) {
        for (BlockPos pipePos : net.getAllNodes().keySet()) {
            for (Direction dir : Direction.values()) {
                MetaMachine machine = MetaMachine.getMachine(level, pipePos.relative(dir));
                if (machine instanceof OpticalComputationHatchMachine hatch) {
                    for (IMultiController controller : hatch.getControllers()) {
                        if (controller instanceof ResearchUnitMachine unit && unit != this
                                && unit.slaveMode && unit.isFormed()) {
                            slaves.add(unit.getPos());
                        }
                    }
                } else if (machine instanceof OpticalDataHatchMachine dataHatch) {
                    for (IMultiController controller : dataHatch.getControllers()) {
                        if (controller instanceof DataBankMachine bank) banks.add(bank);
                    }
                }
            }
        }
    }

    /**
     * Two-way research sync with the given Data Banks (reached over the optical net). Imports every research id
     * stored on their data sticks/orbs into this unit's state (marking it complete), then writes this unit's own
     * completed research back onto any stick with spare capacity - so the bank stays a live shared library the
     * whole team reads from and a freshly-connected unit inherits everything instead of re-researching it.
     */
    private void syncDataBanks(Set<DataBankMachine> banks) {
        List<IItemHandlerModifiable> stickInvs = new ArrayList<>();
        for (DataBankMachine bank : banks) {
            for (IMultiPart part : bank.getParts()) {
                if (part.self() instanceof DataAccessHatchMachine hatch) {
                    stickInvs.add(hatch.importItems.storage);
                }
            }
        }

        // Current bank contents (research ids stored across every wired bank's data sticks/orbs).
        Set<String> banked = new HashSet<>();
        for (IItemHandlerModifiable inv : stickInvs) {
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                banked.addAll(ResearchDataItem.readAll(inv.getStackInSlot(slot)));
            }
        }

        boolean stateChanged = false;
        // import: bank -> this unit's state
        for (String id : banked) {
            Research research = ResearchRegistry.get(id);
            if (research != null && !state.isComplete(id)) {
                state.setCompletedRuns(id, research.getRunsRequired());
                stateChanged = true;
            }
        }
        // export: this unit's completed research -> bank sticks (skip ids already banked somewhere)
        for (Research research : ResearchRegistry.all()) {
            String id = research.getId();
            if (!state.isComplete(id) || banked.contains(id)) continue;
            if (writeResearchOntoStick(stickInvs, id)) {
                banked.add(id);
            }
        }

        if (stateChanged) markDirty();
        // Publish the (post-export) bank contents to the GUI so the library list can flag banked research.
        if (stateChanged || !bankResearchIds.equals(banked)) {
            bankResearchIds.clear();
            bankResearchIds.addAll(banked);
            pushResearchSync();
        }
    }

    /** Writes {@code id} onto the first bank data item that is one and has spare capacity; true if written. */
    private boolean writeResearchOntoStick(List<IItemHandlerModifiable> stickInvs, String id) {
        for (IItemHandlerModifiable inv : stickInvs) {
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stick = inv.getStackInSlot(slot);
                if (ResearchDataItem.isDataItem(stick) && !ResearchDataItem.isFull(stick)
                        && ResearchDataItem.write(stick, id)) {
                    inv.setStackInSlot(slot, stick);
                    return true;
                }
            }
        }
        return false;
    }

    /** The optical-pipe net adjacent to {@code pos} (the reception hatch), or null if no data cable touches it. */
    @Nullable
    private OpticalPipeNet findConnectedOpticalNet(BlockPos pos) {
        Level level = getLevel();
        if (level == null) return null;
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(pos.relative(dir)) instanceof OpticalPipeBlockEntity pipe) {
                OpticalPipeNet net = pipe.getOpticalPipeNet();
                if (net != null) return net;
            }
        }
        return null;
    }

    /** Screwdriver toggles CONTROL &lt;-&gt; SLAVE. Becoming a SLAVE clears any queued research. */
    @Override
    protected InteractionResult onScrewdriverClick(Player player, InteractionHand hand, Direction gridSide,
                                                   BlockHitResult hitResult) {
        if (isRemote()) return InteractionResult.SUCCESS;
        this.slaveMode = !slaveMode;
        if (slaveMode) jobs.clear();
        scanOpticalNetwork();
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
            scanOpticalNetwork();
            importCompletedFromSticks();
        }

        boolean changed = false;
        RunStatus status;
        if (slaveMode) {
            // SLAVE units only lend parallel slots to an adjacent CONTROL; they never process themselves.
            status = RunStatus.IDLE;
        } else if (!isWorkingEnabled) {
            status = RunStatus.PAUSED;
        } else if (jobs.isEmpty()) {
            status = RunStatus.IDLE;
        } else {
            // only the first `capacity` queued researches run concurrently; the rest wait their turn
            int activeCount = Math.min(jobs.size(), getJobCapacity());
            boolean anyProgressed = false;
            RunStatus stall = RunStatus.WAITING_MATERIALS;
            for (int i = activeCount - 1; i >= 0; i--) {
                Job job = jobs.get(i);
                Research research = ResearchRegistry.get(job.researchId);
                if (research == null) {
                    jobs.remove(i);
                    changed = true;
                    continue;
                }
                RunStatus outcome = processJob(job, research);
                if (outcome == RunStatus.WORKING) {
                    changed = true;
                    anyProgressed = true;
                } else {
                    // The loop finishes on the leading job (i == 0), so its stall reason is the one surfaced.
                    stall = outcome;
                    // Losing computation or power mid-run bleeds this step's progress back down (GT-recipe decay);
                    // a materials stall means the run hasn't started, so there's nothing to lose.
                    if ((outcome == RunStatus.WAITING_COMPUTE || outcome == RunStatus.WAITING_ENERGY)
                            && decayStep(job, research)) {
                        changed = true;
                    }
                }
                if (state.isComplete(job.researchId)) {
                    completeResearch(research);
                    jobs.remove(i);
                    changed = true;
                }
            }
            status = anyProgressed ? RunStatus.WORKING : jobs.isEmpty() ? RunStatus.IDLE : stall;
        }


        animAdvancing = !slaveMode && isWorkingEnabled && !jobs.isEmpty();
        this.runStatus = status.ordinal();
        if (changed || tickCounter % 10 == 0) pushResearchSync();
    }

    /**
     * Advances a job one tick. Returns {@link RunStatus#WORKING} when it made progress, otherwise the reason it is
     * stalled ({@link RunStatus#WAITING_COMPUTE}/{@link RunStatus#WAITING_MATERIALS}/{@link RunStatus#WAITING_ENERGY})
     * so the GUI can say exactly what the machine is waiting on instead of a blanket "working".
     */
    private RunStatus processJob(Job job, Research research) {
        boolean needsCompute = research.getCwuPerRun() > 0;
        if (needsCompute && (computationProvider == null || computationProvider.requestCWUt(
                (int) Math.min(research.getCwuPerRun(), Integer.MAX_VALUE), true) <= 0)) {
            return RunStatus.WAITING_COMPUTE; // no computation - stall without consuming items/energy
        }
        if (!job.materialsConsumed) {
            if (!hasMaterials(research)) return RunStatus.WAITING_MATERIALS;
            consumeMaterials(research);
            job.materialsConsumed = true;
        }
        if (!drawEnergy(research.getEut(), false)) return RunStatus.WAITING_ENERGY;

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
        return RunStatus.WORKING;
    }


    private boolean decayStep(Job job, Research research) {
        if (job.elapsedTicks <= 0 && job.accumulatedCWU <= 0) return false;
        job.elapsedTicks = Math.max(0, job.elapsedTicks - STEP_DECAY);
        if (research.getCwuPerRun() > 0) {
            long perTick = Math.max(1,
                    (research.getCwuPerRun() + research.getTicksPerRun() - 1) / research.getTicksPerRun());
            job.accumulatedCWU = Math.max(0, job.accumulatedCWU - perTick * STEP_DECAY);
        }
        return true;
    }

    /** Progress through a job's current run/step (0..1): the bottleneck of its time and computation demands. */
    private float stepProgress(Job job) {
        Research research = ResearchRegistry.get(job.researchId);
        if (research == null) return 0f;
        float timeFrac = research.getTicksPerRun() <= 0 ? 1f :
                Math.min(1f, job.elapsedTicks / (float) research.getTicksPerRun());
        float cwuFrac = research.getCwuPerRun() <= 0 ? 1f :
                Math.min(1f, job.accumulatedCWU / (float) research.getCwuPerRun());
        return Math.min(timeFrac, cwuFrac);
    }

    public RunStatus getRunStatus() {
        RunStatus[] values = RunStatus.values();
        return runStatus >= 0 && runStatus < values.length ? values[runStatus] : RunStatus.IDLE;
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

    /**
     * Reads every blueprint written on the data item/paper in the library slot into this unit's research
     * database, marking each recognised research complete. This is the on-demand counterpart to
     * {@link #writeLibrary()} (which exports the unit's knowledge onto an item) and to the automatic
     * {@link #importCompletedFromSticks()} (which does the same for the input bus every 40 ticks). The data item
     * is left intact — blueprints are reusable, so one stick can seed many units. Returns the number of newly
     * imported researches (0 if the item is blank, unrecognised, or everything on it was already known).
     */
    public int readLibrary() {
        ItemStack item = libraryInv.storage.getStackInSlot(0);
        int imported = 0;
        for (String id : ResearchDataItem.readAll(item)) {
            Research research = ResearchRegistry.get(id);
            if (research != null && !state.isComplete(id)) {
                state.setCompletedRuns(id, research.getRunsRequired());
                imported++;
            }
        }
        if (imported > 0) {
            markDirty();
            pushResearchSync();
        }
        return imported;
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

    //////////////////// material helpers (read items + fluids from the input buses) ////////////////////

    /** True if one run's full item and fluid cost is available right now across the input buses/hatches. */
    private boolean hasMaterials(Research research) {
        for (ItemStack cost : research.getItemsPerRun()) {
            if (countMaterial(cost) < cost.getCount()) return false;
        }
        for (FluidStack cost : research.getFluidsPerRun()) {
            if (countFluid(cost) < cost.getAmount()) return false;
        }
        return true;
    }

    /** Extracts one run's items and fluids. Only call once {@link #hasMaterials} has confirmed availability. */
    private void consumeMaterials(Research research) {
        for (ItemStack cost : research.getItemsPerRun()) {
            extractMaterial(cost, cost.getCount());
        }
        for (FluidStack cost : research.getFluidsPerRun()) {
            extractFluid(cost, cost.getAmount());
        }
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

    /** Total mB of a fluid available across the import hatches (matches fluid + NBT, ignores amount). */
    private int countFluid(FluidStack target) {
        int amount = 0;
        for (IFluidHandler tank : inputFluidTanks) {
            for (int i = 0; i < tank.getTanks(); i++) {
                FluidStack inTank = tank.getFluidInTank(i);
                if (!inTank.isEmpty() && inTank.isFluidEqual(target)) amount += inTank.getAmount();
            }
        }
        return amount;
    }

    private void extractFluid(FluidStack target, int amount) {
        for (IFluidHandler tank : inputFluidTanks) {
            if (amount <= 0) break;
            FluidStack drained = tank.drain(new FluidStack(target, amount), IFluidHandler.FluidAction.EXECUTE);
            amount -= drained.getAmount();
        }
    }

    private boolean drawEnergy(long eut, boolean simulate) {
        if (eut <= 0) return true;
        if (energyContainer != null && energyContainer.getEnergyStored() >= eut) {
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
            entry.putFloat("sp", stepProgress(job));
            queue.add(entry);
        }
        tag.put("queue", queue);
        ListTag bank = new ListTag();
        for (String id : bankResearchIds) bank.add(StringTag.valueOf(id));
        tag.put("bank", bank);
        this.researchSync.setTag(tag);
    }

    private void ensureParsed() {
        if (researchSync.tag == parsedFrom) return;
        parsedFrom = researchSync.tag;
        clientState.deserializeNBT(researchSync.tag.getCompound("state"));
        clientQueue.clear();
        clientProgress.clear();
        clientStepProgress.clear();
        clientBankResearch.clear();
        ListTag queue = researchSync.tag.getList("queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < queue.size(); i++) {
            CompoundTag entry = queue.getCompound(i);
            String id = entry.getString("id");
            clientQueue.add(id);
            clientProgress.put(id, entry.getFloat("p"));
            clientStepProgress.put(id, entry.getFloat("sp"));
        }
        ListTag bank = researchSync.tag.getList("bank", Tag.TAG_STRING);
        for (int i = 0; i < bank.size(); i++) {
            clientBankResearch.add(bank.getString(i));
        }
    }

    /** True if the given research is currently stored on a Data Bank wired to this unit (server + client). */
    public boolean isInDataBank(String researchId) {
        if (!isRemote()) return bankResearchIds.contains(researchId);
        ensureParsed();
        return clientBankResearch.contains(researchId);
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

    /** Current-run (step) completion 0..1 for a queued research, used by the status screen's step bar. */
    public float getClientStepProgress(String researchId) {
        ensureParsed();
        return clientStepProgress.getOrDefault(researchId, 0f);
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

    //////////////////// animated model (mcgltf) ////////////////////

    /**
     * The model has a single clip ({@code lasering_loop}, key {@code "lasering"}) rather than separate
     * idle/running poses, so there's only ever one state to target - the on/off distinction instead gates
     * the animation clock itself (see {@link #isAnimationRunning()}), like {@code RadarMachine} does for
     * power loss.
     */
    @Override
    public String getAnimState() {
        return "lasering";
    }

    @Override
    public boolean isAnimationRunning() {
        return animAdvancing;
    }

    @Override
    public boolean shouldRenderModel() {
        return isFormed();
    }

    @Override
    public Vec3 getModelTransform() {
        return switch (getFrontFacing()) {
            case WEST -> new Vec3(3.5, -1, -3.5);
            case EAST -> new Vec3(-2.5, -1, 4.5);
            case NORTH -> new Vec3(4.5, -1, 3.5);
            case SOUTH -> new Vec3(-3.5, -1, -2.5);
            default -> Vec3.ZERO;
        };
    }

    @Override
    public Vec3 getModelScale() {
        return new Vec3(1, 1, 1);
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
