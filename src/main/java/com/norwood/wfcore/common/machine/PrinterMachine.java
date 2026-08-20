package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import brachy.modularui.drawable.progress.ProgressDrawable;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.DoubleSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ProgressWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.radar.RadarBook;
import com.norwood.wfcore.radar.RadarDataStick;
import com.norwood.wfcore.radar.data.RadarScanData;
import com.norwood.wfcore.radar.math.ClusterData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;


public class PrinterMachine extends TieredEnergyMachine implements IMuiMachine {


    public static final int MAX_PROGRESS = 200;
    public static final int PAPER_PER_BOOK = 16;

    @SaveField
    protected final NotifiableItemStackHandler paperInv;
    @SaveField
    protected final NotifiableItemStackHandler dataInv;
    @SaveField
    protected final NotifiableItemStackHandler bookOut;
    @SaveField
    @SyncToClient
    protected int progress;
    @SaveField
    @SyncToClient
    protected boolean working;

    @Nullable
    protected TickableSubscription tickSub;

    public PrinterMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier, false);
        this.paperInv = attachTrait(new NotifiableItemStackHandler(1, IO.IN).setFilter(s -> s.is(Items.PAPER)));
        this.dataInv = attachTrait(new NotifiableItemStackHandler(1, IO.IN).setFilter(PrinterMachine::isDataItem));
        this.bookOut = attachTrait(new NotifiableItemStackHandler(1, IO.OUT));
    }

    private void setWorking(boolean w) {
        if (this.working != w) {
            this.working = w;
            scheduleRenderUpdate();
        }
    }

    @Override
    public void scheduleRenderUpdate() {
        if (!isRemote()) {
            setRenderState(getRenderState().setValue(GTMachineModelProperties.RECIPE_LOGIC_STATUS,
                    working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE));
        }
        super.scheduleRenderUpdate();
    }

    private long energyPerTick() {
        return GTValues.VA[getTier()];
    }

    private static boolean isDataItem(ItemStack stack) {
        return stack.is(GTItems.TOOL_DATA_STICK.asItem()) || stack.is(GTItems.TOOL_DATA_ORB.asItem()) ||
                stack.is(GTItems.TOOL_DATA_MODULE.asItem());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickPrinter);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    protected void tickPrinter() {
        if (isRemote()) {
            return;
        }
        ItemStack data = dataInv.getStackInSlot(0);
        if (isDataItem(data) && isStaleScan(data)) {
            RadarDataStick.wipeScan(data);
            dataInv.storage.setStackInSlot(0, data);
            progress = 0;
            setWorking(false);
            return;
        }
        ItemStack paper = paperInv.getStackInSlot(0);
        if (!isDataItem(data) || paper.getCount() < PAPER_PER_BOOK || !hasScanData(data)
                || !bookOut.getStackInSlot(0).isEmpty()) {
            progress = 0;
            setWorking(false);
            return;
        }
        if (!drainEnergy(true)) {
            setWorking(false);
            return;
        }
        drainEnergy(false);
        setWorking(true);

        if (++progress >= MAX_PROGRESS) {
            progress = 0;
            ItemStack book = buildBook(data);
            if (book != null) {
                bookOut.insertItemInternal(0, book, false);
                paper.shrink(PAPER_PER_BOOK);
                consumeScan(data);
            }
            setWorking(false);
        }
    }

    private void consumeScan(ItemStack data) {
        UUID scan = RadarDataStick.readScan(data);
        if (scan != null && getLevel() instanceof ServerLevel serverLevel) {
            RadarScanData.get(serverLevel).removeScan(scan);
        }
        RadarDataStick.wipeScan(data);
        dataInv.storage.setStackInSlot(0, data);
    }

    protected boolean drainEnergy(boolean simulate) {
        long perTick = energyPerTick();
        long result = energyContainer.getEnergyStored() - perTick;
        if (result >= 0L && result <= energyContainer.getEnergyCapacity()) {
            if (!simulate) {
                energyContainer.removeEnergy(perTick);
            }
            return true;
        }
        return false;
    }

    private boolean hasScanData(ItemStack data) {
        UUID scan = RadarDataStick.readScan(data);
        return scan != null && getLevel() instanceof ServerLevel serverLevel &&
                RadarScanData.get(serverLevel).hasScan(scan);
    }

    private boolean isStaleScan(ItemStack data) {
        UUID scan = RadarDataStick.readScan(data);
        return scan != null && getLevel() instanceof ServerLevel serverLevel &&
                !RadarScanData.get(serverLevel).hasScan(scan);
    }

    @Nullable
    private ItemStack buildBook(ItemStack data) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }
        UUID uuid = RadarDataStick.readScan(data);
        if (uuid == null) {
            return null;
        }
        List<ClusterData> clusters = RadarScanData.get(serverLevel).getScan(uuid);
        if (clusters == null) {
            return null;
        }
        return RadarBook.createReport(clusters);
    }

    private double getProgressPercent() {
        return MAX_PROGRESS == 0 ? 0.0 : progress / (double) MAX_PROGRESS;
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        DoubleSyncValue progressPercent = syncManager.getOrCreateSyncHandler("progressPercent", DoubleSyncValue.class,
                () -> new DoubleSyncValue(this::getProgressPercent));

        mainWidget.child(Flow.row()
                .coverChildren()
                .center()
                .childPadding(4)
                .child(Flow.column()
                        .coverChildren()
                        .childPadding(4)
                        .child(new ItemSlot()
                                .slot(new ModularSlot(paperInv.storage, 0).singletonSlotGroup(0))
                                .background(GTGuiTextures.SLOT, GTGuiTextures.PAPER_OVERLAY))
                        // The data stick is consumed at the end of a run, so taking it mid-print would
                        // desync the scan; the old LDLib slot refused pickup while working the same way.
                        .child(new ItemSlot()
                                .slot(new ModularSlot(dataInv.storage, 0) {

                                    @Override
                                    public boolean mayPickup(Player player) {
                                        return !working && super.mayPickup(player);
                                    }
                                }.singletonSlotGroup(0))
                                .background(GTGuiTextures.SLOT, GTGuiTextures.DATA_ORB_OVERLAY)))
                .child(new ProgressWidget()
                        .value(progressPercent)
                        .texture(GTGuiTextures.PROGRESS_ARROW.main(), ProgressDrawable.Direction.RIGHT)
                        .size(20, 20))
                .child(new ItemSlot()
                        .slot(new ModularSlot(bookOut.storage, 0).singletonSlotGroup(0).accessibility(false, true))
                        .background(GTGuiTextures.SLOT, GTGuiTextures.PRINTED_PAPER_OVERLAY)));
    }
}
