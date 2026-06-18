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
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.radar.RadarBook;
import com.norwood.wfcore.radar.data.RadarScanData;
import com.norwood.wfcore.radar.math.ClusterData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Single-block printer: consumes paper + a radar data stick and EU, printing a written book that
 * lists the bases recorded on the data stick (ranked by richness and player count, with estimated
 * center coordinates).
 */
public class PrinterMachine extends TieredEnergyMachine implements IUIMachine, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(PrinterMachine.class,
            TieredEnergyMachine.MANAGED_FIELD_HOLDER);

    public static final int MAX_PROGRESS = 200;

    @Persisted
    protected final NotifiableItemStackHandler paperInv;
    @Persisted
    protected final NotifiableItemStackHandler dataInv;
    @Persisted
    protected final NotifiableItemStackHandler bookOut;
    @Persisted
    protected int progress;

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
        ItemStack paper = paperInv.getStackInSlot(0);
        ItemStack data = dataInv.getStackInSlot(0);
        if (paper.isEmpty() || !isDataItem(data) || !hasScan(data) || !bookOut.getStackInSlot(0).isEmpty()) {
            progress = 0;
            return;
        }
        if (!drainEnergy(true)) {
            return;
        }
        drainEnergy(false);

        if (++progress >= MAX_PROGRESS) {
            progress = 0;
            ItemStack book = buildBook(data);
            if (book != null) {
                bookOut.insertItemInternal(0, book, false);
                paper.shrink(1);
            }
        }
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

    private boolean hasScan(ItemStack data) {
        CompoundTag tag = data.getTag();
        return tag != null && tag.hasUUID("TargetUUID");
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

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(176, 166, this, entityPlayer)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(5, 5, getBlockState().getBlock().getDescriptionId()))
                .widget(new SlotWidget(paperInv, 0, 34, 30).setBackgroundTexture(GuiTextures.SLOT))
                .widget(new SlotWidget(dataInv, 0, 34, 52).setBackgroundTexture(GuiTextures.SLOT))
                .widget(new SlotWidget(bookOut, 0, 120, 40).setBackgroundTexture(GuiTextures.SLOT))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 7, 82, true));
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(paperInv.storage);
        clearInventory(dataInv.storage);
        clearInventory(bookOut.storage);
    }
}
