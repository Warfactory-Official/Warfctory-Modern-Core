package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
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
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.RadarClustering;
import com.norwood.wfcore.radar.data.RadarScanData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Radar multiblock controller. Snapshots online players + registered machines, runs DBSCAN to find bases,
 * and writes the scan UUID onto a data stick for the printer to read back. Scanning is gated on computation:
 * the radar needs a Computation Data Reception hatch fed (over an optical pipe) by a mainframe, and stalls
 * without burning energy when the CWU runs short.
 */
public class RadarMachine extends MultiblockControllerMachine implements IFancyUIMachine, IOpticalComputationReceiver {

    private static final int BASE_CWUT = 4;

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(RadarMachine.class,
            MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    protected final NotifiableItemStackHandler dataStickInv;
    @Persisted
    protected int scanProgress;
    @Persisted
    @DescSynced
    protected boolean isActive;
    @Persisted
    protected boolean finished;
    /** Client-synced: whether the dish should be spinning (false = stalled for power, freeze in place). */
    @DescSynced
    protected boolean animAdvancing = true;

    @Nullable
    protected EnergyContainerList energyContainer;
    @Nullable
    protected IOpticalComputationProvider computationProvider;
    @Nullable
    protected TickableSubscription tickSub;
    @Nullable
    protected volatile UUID lastScan;
    protected int voltageTier = -1;

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
        for (IMultiPart part : getParts()) {
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
        this.isActive = false;
        this.scanProgress = 0;
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
        if (!isActive) {
            animAdvancing = true; // idle loops freely
            return;
        }
        int targetTicks = getScanDurationTicks();
        if (!drainEnergy(true)) {
            animAdvancing = false; // power loss mid-scan: freeze the dish where it is
            return;
        }
        if (!hasComputation()) {
            animAdvancing = false; // not enough CWU: stall without burning energy
            return;
        }
        drainEnergy(false);
        requestCWU(false);
        animAdvancing = true; // powered and scanning: keep spinning

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

    protected boolean drainEnergy(boolean simulate) {
        if (energyContainer == null) {
            return false;
        }
        long energyToDrain = GTValues.VA[Math.max(GTValues.EV, voltageTier)];
        long result = energyContainer.getEnergyStored() - energyToDrain;
        if (result >= 0L && result <= energyContainer.getEnergyCapacity()) {
            if (!simulate) {
                energyContainer.removeEnergy(energyToDrain);
            }
            return true;
        }
        return false;
    }

    /** Required CWU/t rises with voltage tier (EV=4, IV=8, LuV=16, ...). */
    public int getRequiredCWUt() {
        int tier = Math.max(GTValues.EV, voltageTier);
        return BASE_CWUT << (tier - GTValues.EV);
    }

    private int requestCWU(boolean simulate) {
        return computationProvider == null ? 0 : computationProvider.requestCWUt(getRequiredCWUt(), simulate);
    }

    public boolean hasComputation() {
        return requestCWU(true) >= getRequiredCWUt();
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return computationProvider;
    }

    public void startScan() {
        if (!canScan() || !(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        isActive = true;
        finished = false;
        scanProgress = 0;
        lastScan = null;

        var snapshot = RadarClustering.collectTargets(serverLevel);
        RadarClustering.calculateDBSCAN(snapshot, RadarClustering.EPS, RadarClustering.MIN_PTS)
                .thenAccept(clusters -> serverLevel.getServer().execute(() -> {
                    UUID id = UUID.randomUUID();
                    RadarScanData.get(serverLevel).addScan(id, clusters);
                    this.lastScan = id;
                }))
                .exceptionally(ex -> {
                    WFCore.LOGGER.error("Radar DBSCAN failed", ex);
                    return null;
                });
    }

    protected void completeScan() {
        isActive = false;
        scanProgress = 0;
        ItemStack stick = dataStickInv.getStackInSlot(0);
        if (!stick.isEmpty() && lastScan != null && getLevel() instanceof ServerLevel serverLevel) {
            CompoundTag tag = stick.getOrCreateTag();
            if (tag.hasUUID("TargetUUID")) {
                RadarScanData.get(serverLevel).removeScan(tag.getUUID("TargetUUID"));
            }
            tag.putUUID("TargetUUID", lastScan);
            tag.putBoolean("is_analyzed", true);

            CompoundTag display = tag.getCompound("display");
            display.putString("Name", Component.Serializer.toJson(Component.literal("§bRecorded Radar Data")));
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.literal("§7Contains structure density coordinates."))));
            lore.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.literal("§5Ready for Printer analysis."))));
            display.put("Lore", lore);
            tag.put("display", display);
        }
        finished = true;
        lastScan = null;
    }

    public boolean canScan() {
        return isFormed() && !isActive && hasDataStick() && isCorrectY() && hasSkylightAccess() && drainEnergy(true) &&
                hasComputation();
    }

    public boolean hasDataStick() {
        return !dataStickInv.getStackInSlot(0).isEmpty();
    }

    /** Client-synced scanning state, used to pick the GeckoLib animation (running vs idle). */
    public boolean isScanning() {
        return isActive;
    }

    /** Client-synced: whether the spin clock advances; false freezes the dish in place on power loss. */
    public boolean isAnimAdvancing() {
        return animAdvancing;
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
        return switch (voltageTier) {
            case GTValues.EV -> 12000;
            case GTValues.IV -> 8000;
            case GTValues.LuV -> 6000;
            case GTValues.ZPM -> 3000;
            case GTValues.UV -> 2000;
            default -> Integer.MAX_VALUE;
        };
    }

    public double getProgressPercent() {
        int target = getScanDurationTicks();
        return target <= 0 ? 0 : ((double) scanProgress / target) * 100.0;
    }

    //////////////////// UI ////////////////////

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 170, 86);
        group.addWidget(new ImageWidget(4, 4, 162, 56, GuiTextures.DISPLAY));
        group.addWidget(new LabelWidget(8, 8, "wfcore.machine.radar.name"));
        group.addWidget(new LabelWidget(8, 22, this::getStatusText).setTextColor(-1).setDropShadow(true));
        group.addWidget(new LabelWidget(8, 34, this::getProgressText).setTextColor(-1).setDropShadow(true));
        group.addWidget(new SlotWidget(dataStickInv, 0, 142, 62).setBackgroundTexture(GuiTextures.SLOT));
        group.addWidget(new ButtonWidget(8, 62, 80, 18, GuiTextures.BUTTON, this::onScanClick)
                .setHoverTooltips("wfcore.gui.radar.start_scan"));
        group.addWidget(new LabelWidget(14, 67, "wfcore.gui.radar.start_scan"));
        return group;
    }

    private void onScanClick(ClickData data) {
        if (isRemote()) {
            return;
        }
        if (!isActive && canScan()) {
            startScan();
        }
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
        if (isActive) {
            return "§eScanning...";
        }
        if (finished) {
            return "§bScan saved to data stick";
        }
        return hasDataStick() ? "§aReady" : "§eInsert a data stick";
    }

    private String getProgressText() {
        if (!isActive) {
            return "";
        }
        return String.format("§e%.1f%%", getProgressPercent());
    }
}
