package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.TieredEnergyMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
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


public class PrinterMachine extends TieredEnergyMachine implements IUIMachine, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(PrinterMachine.class,
            TieredEnergyMachine.MANAGED_FIELD_HOLDER);

    public static final int MAX_PROGRESS = 200;
    public static final int PAPER_PER_BOOK = 16;

    @Persisted
    protected final NotifiableItemStackHandler paperInv;
    @Persisted
    protected final NotifiableItemStackHandler dataInv;
    @Persisted
    protected final NotifiableItemStackHandler bookOut;
    @Persisted
    @DescSynced
    protected int progress;
    @Persisted
    @DescSynced
    protected boolean working;

    @Nullable
    protected TickableSubscription tickSub;

    public PrinterMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, args);
        this.paperInv = new NotifiableItemStackHandler(this, 1, IO.IN).setFilter(s -> s.is(Items.PAPER));
        this.dataInv = new NotifiableItemStackHandler(this, 1, IO.IN).setFilter(PrinterMachine::isDataItem);
        this.bookOut = new NotifiableItemStackHandler(this, 1, IO.OUT);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
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
        CompoundTag tag = data.getTag();
        if (tag != null && tag.hasUUID("TargetUUID") && getLevel() instanceof ServerLevel serverLevel) {
            RadarScanData.get(serverLevel).removeScan(tag.getUUID("TargetUUID"));
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
        CompoundTag tag = data.getTag();
        if (tag == null || !tag.hasUUID("TargetUUID")) {
            return false;
        }
        return getLevel() instanceof ServerLevel serverLevel &&
                RadarScanData.get(serverLevel).hasScan(tag.getUUID("TargetUUID"));
    }

    private boolean isStaleScan(ItemStack data) {
        CompoundTag tag = data.getTag();
        if (tag == null || !tag.hasUUID("TargetUUID")) {
            return false;
        }
        return getLevel() instanceof ServerLevel serverLevel &&
                !RadarScanData.get(serverLevel).hasScan(tag.getUUID("TargetUUID"));
    }

    @Nullable
    private ItemStack buildBook(ItemStack data) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return null;
        }
        CompoundTag tag = data.getTag();
        if (tag == null || !tag.hasUUID("TargetUUID")) {
            return null;
        }
        UUID uuid = tag.getUUID("TargetUUID");
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
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId()))

                .widget(new SlotWidget(paperInv.storage, 0, 52, 25, true, true)
                        .setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.PAPER_OVERLAY)))
                .widget(new SlotWidget(dataInv.storage, 0, 52, 47, true, true) {

                    @Override
                    public boolean canTakeStack(Player player) {
                        return super.canTakeStack(player) && !working;
                    }
                }.setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.DATA_ORB_OVERLAY)))
                .widget(new ProgressWidget(this::getProgressPercent, 79, 36, 20, 20, GuiTextures.PROGRESS_BAR_ARROW))
                .widget(new SlotWidget(bookOut.storage, 0, 107, 36, true, false)
                        .setBackgroundTexture(new GuiTextureGroup(GuiTextures.SLOT, GuiTextures.PRINTED_PAPER_OVERLAY)))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 7, 84, true));
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(paperInv.storage);
        clearInventory(dataInv.storage);
        clearInventory(bookOut.storage);
    }
}
