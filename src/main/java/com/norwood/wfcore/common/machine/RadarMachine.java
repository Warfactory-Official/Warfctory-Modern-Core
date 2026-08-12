package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.render.gltf.AnimTransition;
import com.norwood.wfcore.client.render.gltf.IAnimatedMachine;
import com.norwood.wfcore.radar.RadarClustering;
import com.norwood.wfcore.radar.RadarConfig;
import com.norwood.wfcore.radar.RadarDataStick;
import com.norwood.wfcore.radar.RadarSession;
import com.norwood.wfcore.radar.data.RadarScanData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Radar multiblock controller. Snapshots online players + registered machines, runs DBSCAN to find bases,
 * and writes the scan UUID onto a data stick for the printer to read back. Scanning is gated on computation:
 * the radar needs a Computation Data Reception hatch fed (over an optical pipe) by a mainframe, and stalls
 * without burning energy when the CWU runs short.
 */
public class RadarMachine extends MultiblockControllerMachine
                          implements IFancyUIMachine, IOpticalComputationReceiver, IAnimatedMachine {

    private static final int BASE_CWUT = 128;
    /** Lowest energy-hatch tier the radar will run at. Below this it forms but refuses to scan. */
    private static final int MIN_TIER = GTValues.HV;
    private static final int BASE_SCAN_TICKS_EV = 12000;

    private static final double SCAN_TIER_FACTOR = 0.8;

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(RadarMachine.class,
            MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    protected final NotifiableItemStackHandler dataStickInv;
    @Persisted
    @DescSynced
    protected int scanProgress;
    @Persisted
    @DescSynced
    protected boolean isActive;
    @Persisted
    @DescSynced
    protected boolean finished;
    @DescSynced
    protected boolean animAdvancing = true;
    @DescSynced
    protected boolean computationReady;
    @DescSynced
    protected int availableCWUt;

    /** Packed (x, z) calibrator target points for the current scan session; empty when no session. */
    @Persisted
    @DescSynced
    protected long[] sessionTargets = new long[0];
    /** Authoritative session size (primitive int always syncs; drives client button/gate logic). */
    @Persisted
    @DescSynced
    protected int sessionTargetCount;
    /** Bit i set when a calibrator is in position for {@code sessionTargets[i]}. */
    @Persisted
    @DescSynced
    protected int sessionPlacedMask;
    /** 0 = none, 1 = last initiate refused (a target couldn't fit inside the world border). */
    @DescSynced
    protected int sessionError;

    @Nullable
    protected EnergyContainerList energyContainer;
    @Nullable
    protected IOpticalComputationProvider computationProvider;
    /** The structure's maintenance hatch part, gathered on form; scanning stalls while it has problems. */
    @Nullable
    protected IMaintenanceMachine maintenance;
    @Nullable
    protected TickableSubscription tickSub;
    @Nullable
    protected volatile UUID lastScan;
    @DescSynced
    protected int voltageTier = -1;
    private transient String lastTickLog = "";

    public RadarMachine(IMachineBlockEntity holder) {
        super(holder);
        this.dataStickInv = new NotifiableItemStackHandler(this, 1, IO.IN).setFilter(RadarMachine::isDataItem);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private static boolean isDataItem(ItemStack stack) {
        return stack.is(GTItems.TOOL_DATA_STICK.asItem()) || stack.is(GTItems.TOOL_DATA_ORB.asItem()) ||
                stack.is(GTItems.TOOL_DATA_MODULE.asItem());
    }

    //////////////////// structure lifecycle ////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        List<IEnergyContainer> containers = new ArrayList<>();
        this.computationProvider = null;
        this.maintenance = null;
        for (IMultiPart part : getParts()) {
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                this.maintenance = maintenanceMachine;
            }
            part.self().holder.self().getCapability(GTCapability.CAPABILITY_COMPUTATION_PROVIDER)
                    .ifPresent(p -> this.computationProvider = p);
            for (var handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(IO.IN)) {
                    continue;
                }
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(containers::add);
            }
        }
        this.energyContainer = new EnergyContainerList(containers);
        this.voltageTier = GTUtil.getTierByVoltage(energyContainer.getInputVoltage());
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickRadar);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = null;
        this.computationProvider = null;
        this.maintenance = null;
        this.isActive = false;
        this.scanProgress = 0;
        this.computationReady = false;
        this.availableCWUt = 0;
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    //////////////////// scan logic ////////////////////

    protected void tickRadar() {
        if (isRemote() || !isFormed()) {
            return;
        }
        if (getOffsetTimer() % 10 == 0) {
            int avail = requestCWU(true);
            this.availableCWUt = avail;
            this.computationReady = avail >= getRequiredCWUt();
        }
        if (getOffsetTimer() % 20 == 0 && hasSession()) {
            refreshPlacedMask();
        }
        if (!isActive) {
            animAdvancing = true; // idle loops freely
            return;
        }
        int targetTicks = getScanDurationTicks();
        if (hasMaintenanceProblems()) {
            animAdvancing = false; // maintenance problems freeze the dish until the hatch is serviced
            tickLog("STALLED: maintenance required");
            return;
        }
        if (!drainEnergy(true)) {
            animAdvancing = false; // power loss mid-scan: freeze the dish where it is
            tickLog("STALLED: not enough energy (need " + GTValues.VA[Math.max(MIN_TIER, voltageTier)]
                    + ", stored " + (energyContainer == null ? "n/a" : energyContainer.getEnergyStored()) + ")");
            return;
        }
        if (!hasComputation()) {
            animAdvancing = false; // not enough CWU: stall without burning energy
            tickLog("STALLED: not enough CWU (need " + getRequiredCWUt() + ", avail " + requestCWU(true) + ")");
            return;
        }
        drainEnergy(false);
        requestCWU(false);
        if (maintenance != null) {
            maintenance.calculateMaintenance(maintenance); // accrue active time -> random problems over a long scan
        }
        animAdvancing = true; // powered and scanning: keep spinning
        tickLog("scanning");

        if (scanProgress <= targetTicks) {
            scanProgress++;
        }
        if (scanProgress >= targetTicks) {
            if (lastScan != null) {
                completeScan();
            } else {
                scanProgress = targetTicks - 1; // wait for the async clustering to finish
            }
        }
    }

    private void tickLog(String msg) {
        if (!msg.equals(lastTickLog)) {
            lastTickLog = msg;
            WFCore.LOGGER.info("[Radar@{}] tick: {} (progress {}/{})", getPos(), msg, scanProgress,
                    getScanDurationTicks());
        }
    }

    protected boolean drainEnergy(boolean simulate) {
        if (energyContainer == null) {
            return false;
        }
        long energyToDrain = GTValues.VA[Math.max(MIN_TIER, voltageTier)];
        long result = energyContainer.getEnergyStored() - energyToDrain;
        if (result >= 0L && result <= energyContainer.getEnergyCapacity()) {
            if (!simulate) {
                energyContainer.removeEnergy(energyToDrain);
            }
            return true;
        }
        return false;
    }

    /** Required CWU/t quadruples per tier above EV (EV=16, IV=64, LuV=256, ZPM=1024, UV=4096). */
    public int getRequiredCWUt() {
        int tier = Math.max(GTValues.EV, voltageTier);
        return BASE_CWUT << (2 * (tier - GTValues.EV));
    }

    private int requestCWU(boolean simulate) {
        return computationProvider == null ? 0 : computationProvider.requestCWUt(getRequiredCWUt(), simulate);
    }

    public boolean hasComputation() {
        return requestCWU(true) >= getRequiredCWUt();
    }

    public boolean hasMaintenanceProblems() {
        return maintenance != null && maintenance.hasMaintenanceProblems();
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return computationProvider;
    }

    public void startScan() {
        if (!canScan() || !(getLevel() instanceof ServerLevel serverLevel)) {
            WFCore.LOGGER.info("[Radar@{}] startScan aborted: canScan={} isServerLevel={}",
                    getPos(), canScan(), getLevel() instanceof ServerLevel);
            return;
        }
        isActive = true;
        finished = false;
        scanProgress = 0;
        lastScan = null;
        WFCore.LOGGER.info("[Radar@{}] startScan OK: isActive=true, scanDuration={} ticks, kicking off DBSCAN",
                getPos(), getScanDurationTicks());

        RadarClustering.scan(serverLevel, RadarConfig.getEps(), RadarConfig.getMinPts())
                .thenAccept(clusters -> serverLevel.getServer().execute(() -> {
                    UUID id = UUID.randomUUID();
                    RadarScanData.get(serverLevel).addScan(id, clusters);
                    this.lastScan = id;
                }))
                .exceptionally(ex -> {
                    WFCore.LOGGER.error("Radar DBSCAN failed", ex);
                    serverLevel.getServer().execute(this::stopScan);
                    return null;
                });
    }

    /** Cancel a running scan and return the radar to idle without writing anything to the data stick. */
    public void stopScan() {
        WFCore.LOGGER.info("[Radar@{}] stopScan: isActive {}->false, progress {}", getPos(), isActive, scanProgress);
        isActive = false;
        scanProgress = 0;
        finished = false;
        lastScan = null;
        animAdvancing = true;
    }

    protected void completeScan() {
        isActive = false;
        scanProgress = 0;
        ItemStack stick = dataStickInv.getStackInSlot(0);
        if (!stick.isEmpty() && lastScan != null && getLevel() instanceof ServerLevel serverLevel) {
            CompoundTag tag = stick.getTag();
            if (tag != null && tag.hasUUID(RadarDataStick.KEY_TARGET_UUID)) {
                RadarScanData.get(serverLevel).removeScan(tag.getUUID(RadarDataStick.KEY_TARGET_UUID));
            }
            RadarDataStick.writeScan(stick, lastScan);
        }
        clearSession();
        finished = true;
        lastScan = null;
    }

    public boolean canScan() {
        return isFormed() && !isActive && voltageTier >= MIN_TIER && hasDataStick() && isCorrectY() &&
                hasSkylightAccess() && !hasMaintenanceProblems() && allCalibratorsPlaced() &&
                drainEnergy(true) && hasComputation();
    }

    public int getCalibratorCount() {
        return RadarConfig.getCalibratorBaseCount() + Math.max(0, voltageTier - MIN_TIER);
    }

    public boolean hasSession() {
        return sessionTargetCount > 0;
    }

    private static int fullMask(int n) {
        return n >= 32 ? -1 : (1 << n) - 1;
    }

    public boolean allCalibratorsPlaced() {
        return sessionTargetCount > 0 && (sessionPlacedMask & fullMask(sessionTargetCount)) == fullMask(sessionTargetCount);
    }

    public void initiateSession() {
        if (isActive || voltageTier < MIN_TIER || !(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        long[] targets = RadarSession.generate(serverLevel, getPos(), getCalibratorCount());
        if (targets == null) {
            sessionError = 1;
            clearSession();
            WFCore.LOGGER.info("[Radar@{}] session refused: a calibrator target can't fit inside the world border",
                    getPos());
            return;
        }
        sessionError = 0;
        finished = false;
        sessionTargets = targets;
        sessionTargetCount = targets.length;
        refreshPlacedMask();
        WFCore.LOGGER.info("[Radar@{}] session initiated: {} calibrator target(s)", getPos(), targets.length);
    }

    public void cancelSession() {
        clearSession();
        sessionError = 0;
    }

    private void clearSession() {
        sessionTargets = new long[0];
        sessionTargetCount = 0;
        sessionPlacedMask = 0;
    }

    private void refreshPlacedMask() {
        if (hasSession() && getLevel() instanceof ServerLevel serverLevel) {
            sessionPlacedMask = RadarSession.placedMask(serverLevel, sessionTargets,
                    RadarConfig.getCalibratorTolerance());
        }
    }

    public boolean hasDataStick() {
        return !dataStickInv.getStackInSlot(0).isEmpty();
    }

    public boolean isScanning() {
        return isActive;
    }

    public boolean isAnimAdvancing() {
        return animAdvancing;
    }

    //////////////////// animated model (mcgltf) ////////////////////

    @Override
    public String getAnimState() {
        return isScanning() ? "running" : "idle";
    }

    @Override
    public boolean isAnimationRunning() {
        return isAnimAdvancing();
    }

    @Override
    public AnimTransition getAnimTransition(String from, String to) {
        // spin up instantly, but play the dish's rotation out to its loop end before settling to idle
        return "running".equals(to) ? AnimTransition.SNAP : AnimTransition.FINISH_LOOP;
    }

    @Override
    public Vec3 getModelTransform() {
        return switch (getFrontFacing()) {
            case WEST -> new Vec3(-4.5, 10, 0.5);
            case EAST -> new Vec3(5.5, 10, 0.5);
            case NORTH -> new Vec3(0.5, 10, -4.5);
            case SOUTH -> new Vec3(0.5, 10, 5.5);
            default -> Vec3.ZERO;
        };
    }

    @Override
    public Vec3 getModelScale() {
        return new Vec3(1.25, 1.25, 1.25);
    }

    @Override
    public boolean shouldRenderModel() {
        return isFormed();
    }

    /**
     * The upper dish/tower blocks (structure layers >= 22) the GLTF model visually replaces. Computed
     * client-side from the controller position + facing and {@link RadarStructure#AISLES}; fully
     * deterministic, so no server sync is needed. Offsets are laid out along the same relative directions
     * the pattern is built with ({@code start(FRONT, UP, RIGHT)}).
     */
    @Override
    public Collection<BlockPos> getHiddenBlocks() {
        final int animatedLayer = 22;
        String[][] aisles = RadarStructure.AISLES;

        int cChar = -1, cString = -1, cAisle = -1;
        outer:
        for (int a = 0; a < aisles.length; a++) {
            for (int s = 0; s < aisles[a].length; s++) {
                int idx = aisles[a][s].indexOf('A');
                if (idx >= 0) {
                    cAisle = a;
                    cString = s;
                    cChar = idx;
                    break outer;
                }
            }
        }
        if (cChar < 0) {
            return List.of();
        }

        Direction front = getFrontFacing();
        Direction up = getUpwardsFacing();
        boolean flipped = isFlipped();
        Direction charDir = RelativeDirection.FRONT.getRelative(front, up, flipped);
        Direction stringDir = RelativeDirection.UP.getRelative(front, up, flipped);
        Direction aisleDir = RelativeDirection.RIGHT.getRelative(front, up, flipped);

        BlockPos controller = getPos();
        List<BlockPos> hidden = new ArrayList<>();
        for (int a = 0; a < aisles.length; a++) {
            for (int s = animatedLayer; s < aisles[a].length; s++) {
                String layer = aisles[a][s];
                for (int c = 0; c < layer.length(); c++) {
                    char ch = layer.charAt(c);
                    if (ch == ' ' || ch == 'A') {
                        continue;
                    }
                    hidden.add(controller
                            .relative(charDir, c - cChar)
                            .relative(stringDir, s - cString)
                            .relative(aisleDir, a - cAisle));
                }
            }
        }
        return hidden;
    }

    private boolean isCorrectY() {
        return getPos().getY() >= 100;
    }

    private boolean hasSkylightAccess() {
        if (getLevel() == null) {
            return false;
        }
        BlockPos check = getPos().above(35);
        int top = getLevel().getHeight(Heightmap.Types.WORLD_SURFACE, check.getX(), check.getZ());
        return top <= check.getY() + 35;
    }

    public int getScanDurationTicks() {
        if (voltageTier < MIN_TIER) {
            return Integer.MAX_VALUE; // below HV the radar won't scan (see canScan)
        }
        // 600 s (12000 t) at EV; ×0.8 per tier above EV (and ÷0.8 for HV, one tier below EV). Unlike GT's
        // usual ×0.5-per-tier overclock, the 0.8 factor gives steep diminishing returns so high-voltage
        // scans stay long.
        double ticks = BASE_SCAN_TICKS_EV * Math.pow(SCAN_TIER_FACTOR, voltageTier - GTValues.EV);
        return (int) Math.round(ticks);
    }

    public double getProgressPercent() {
        int target = getScanDurationTicks();
        return target <= 0 ? 0 : ((double) scanProgress / target) * 100.0;
    }


    public long getEnergyDrawPerTick() {
        return voltageTier < MIN_TIER ? 0 : GTValues.VA[Math.max(MIN_TIER, voltageTier)];
    }


    public long getTotalEnergyCost() {
        return getEnergyDrawPerTick() * getScanDurationTicks();
    }

    public long getTotalComputationCost() {
        return (long) getRequiredCWUt() * getScanDurationTicks();
    }

    private boolean canPreviewCost() {
        return isFormed() && voltageTier >= MIN_TIER && getScanDurationTicks() != Integer.MAX_VALUE;
    }
    private static String compact(long v) {
        if (v < 1_000L) {
            return Long.toString(v);
        }
        if (v < 1_000_000L) {
            return String.format("%.1fk", v / 1_000.0);
        }
        if (v < 1_000_000_000L) {
            return String.format("%.1fM", v / 1_000_000.0);
        }
        return String.format("%.1fG", v / 1_000_000_000.0);
    }

    private String getScanHeaderText() {
        if (!canPreviewCost()) {
            return "";
        }
        int secs = getScanDurationTicks() / 20;
        return String.format("§8Full scan ~%dm %02ds:", secs / 60, secs % 60);
    }

    private String getPowerText() {
        if (!canPreviewCost()) {
            return "";
        }
        return String.format("§7Power: §f%,d EU/t §8(%s)", getEnergyDrawPerTick(), compact(getTotalEnergyCost()));
    }

    private String getComputeText() {
        if (!canPreviewCost()) {
            return "";
        }
        return String.format("§7Compute: §f%d CWU/t §8(%s)", getRequiredCWUt(), compact(getTotalComputationCost()));
    }

    //////////////////// UI ////////////////////

    /**
     * Attach each part's fancy tooltips to the controller's tooltip panel, so the maintenance hatch's red
     * "needs servicing" icon and its tool checklist show in the FancyUI like GT's own multiblock controllers.
     * The {@link IFancyUIMachine} default only attaches the controller's own tooltips, not its parts'.
     */
    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 190, 130);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 100).setBackground(GuiTextures.DISPLAY)
                .addWidget(new LabelWidget(4, 5, "wfcore.machine.radar.name"))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText).setMaxWidthLimit(170)));

        group.addWidget(new SlotWidget(dataStickInv.storage, 0, 168, 106, true, true) {
            @Override
            public boolean canTakeStack(Player player) {
                return super.canTakeStack(player) && !isActive;
            }
        }.setBackgroundTexture(GuiTextures.SLOT));
        group.addWidget(new ButtonWidget(4, 106, 78, 18, GuiTextures.BUTTON, this::onPrimaryClick));
        group.addWidget(new LabelWidget(10, 111, this::getPrimaryButtonText));
        group.addWidget(new ButtonWidget(86, 106, 76, 18, GuiTextures.BUTTON, this::onSecondaryClick));
        group.addWidget(new LabelWidget(92, 111, this::getSecondaryButtonText));
        return group;
    }

    /** Builds the scrollable status readout: status line, scan progress, and the session's target list. */
    public void addDisplayText(List<Component> textList) {
        textList.add(Component.literal(getStatusText()));
        if (isActive) {
            textList.add(Component.literal(getProgressText()));
        }
        if (hasSession()) {
            int placed = Integer.bitCount(sessionPlacedMask & fullMask(sessionTargetCount));
            textList.add(Component.translatable("wfcore.gui.radar.calibrators_progress", placed, sessionTargetCount));
            for (int i = 0; i < sessionTargetCount; i++) {
                textList.add(targetLine(i));
            }
        } else if (!isActive && !finished && canPreviewCost()) {
            textList.add(Component.literal(getScanHeaderText()));
            textList.add(Component.literal(getPowerText()));
            textList.add(Component.literal(getComputeText()));
        }
    }

    /** One session line: check mark + target coords + distance/bearing from the radar (computed client-side). */
    private Component targetLine(int i) {
        boolean placed = (sessionPlacedMask & (1 << i)) != 0;
        String mark = placed ? "§a✔" : "§c✘";
        if (i >= sessionTargets.length) {
            return Component.literal(String.format("%s §7#%d", mark, i + 1));
        }
        int tx = RadarSession.unpackX(sessionTargets[i]);
        int tz = RadarSession.unpackZ(sessionTargets[i]);
        double dist = Math.hypot((double) tx - getPos().getX(), (double) tz - getPos().getZ());
        String brg = bearing(tx - getPos().getX(), tz - getPos().getZ());
        return Component.literal(String.format("%s §fX %d, Z %d §7(%.1fk %s)", mark, tx, tz, dist / 1000.0, brg));
    }

    /** 8-point compass bearing from a (dx, dz) offset (+X = East, +Z = South). */
    private static String bearing(double dx, double dz) {
        double ang = (Math.toDegrees(Math.atan2(dz, dx)) + 360.0) % 360.0;
        String[] dirs = { "E", "SE", "S", "SW", "W", "NW", "N", "NE" };
        return dirs[(int) Math.round(ang / 45.0) % 8];
    }

    private void onPrimaryClick(ClickData data) {
        if (isRemote()) {
            return;
        }
        if (isActive) {
            stopScan();
            return;
        }
        if (!hasSession()) {
            initiateSession();
            return;
        }
        logScanGates();
        if (canScan()) {
            WFCore.LOGGER.info("[Radar@{}] gates passed -> startScan()", getPos());
            startScan();
        } else {
            WFCore.LOGGER.info("[Radar@{}] NOT scanning (isActive={}, canScan={})", getPos(), isActive, canScan());
        }
    }

    private void onSecondaryClick(ClickData data) {
        if (isRemote()) {
            return;
        }
        if (hasSession() && !isActive) {
            cancelSession();
        }
    }

    private String getPrimaryButtonText() {
        String key = isActive ? "wfcore.gui.radar.stop_scan"
                : !hasSession() ? "wfcore.gui.radar.initiate_session"
                        : "wfcore.gui.radar.start_scan";
        return Component.translatable(key).getString();
    }

    private String getSecondaryButtonText() {
        return hasSession() && !isActive
                ? Component.translatable("wfcore.gui.radar.cancel_session").getString()
                : "";
    }

    private void logScanGates() {
        WFCore.LOGGER.info("[Radar@{}] gates: formed={} !active={} tier={}(min {})->{} dataStick={} y>=100={}({}) "
                        + "skylight={} energyOK={} computationOK={}",
                getPos(), isFormed(), !isActive, voltageTier, MIN_TIER, voltageTier >= MIN_TIER, hasDataStick(),
                isCorrectY(), getPos().getY(), hasSkylightAccess(), drainEnergy(true), hasComputation());
        WFCore.LOGGER.info("[Radar@{}]   detail: energyContainer={} computationProvider={} requiredCWUt={} availCWUt={}",
                getPos(), energyContainer == null ? "null" : "present",
                computationProvider == null ? "null" : computationProvider.getClass().getSimpleName(),
                getRequiredCWUt(), requestCWU(true));
    }

    private String getStatusText() {
        if (!isFormed()) {
            return "§cIncomplete structure";
        }
        if (!isCorrectY()) {
            return "§cMust be built at Y >= 100";
        }
        if (!hasSkylightAccess()) {
            return "§cNeeds sky access";
        }
        if (voltageTier < MIN_TIER) {
            return "§cRequires an HV+ energy hatch";
        }
        if (hasMaintenanceProblems()) {
            return "§cNeeds maintenance";
        }
        if (isActive) {
            return animAdvancing ? "§eScanning..." : "§6Stalled - no power/computation";
        }
        if (finished) {
            return "§bScan saved to data stick";
        }
        if (!hasDataStick()) {
            return "§eInsert a data stick";
        }
        if (sessionError == 1) {
            return "§cCan't fit calibrators (world border)";
        }
        if (!hasSession()) {
            return "§eInitiate a scan session";
        }
        if (!allCalibratorsPlaced()) {
            return "§ePlace the satellite calibrators";
        }
        if (!computationReady) {
            return String.format("§cNo computation (needs %d CWU/t)", getRequiredCWUt());
        }
        return "§aReady to scan";
    }

    private String getProgressText() {
        if (!isActive) {
            return "";
        }
        return String.format("§e%.1f%%", getProgressPercent());
    }
}
