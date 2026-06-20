package com.norwood.wfcore.common.machine.compute;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import com.norwood.wfcore.common.compute.RAMRegistry;

/** Holds RAM items; their summed throughput caps how much CWU the mainframe can route. */
public class RAMSlotPartMachine extends MultiblockPartMachine implements IRamSlot, IUIMachine, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(RAMSlotPartMachine.class,
            MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    private static final int SLOTS = 4;

    @Persisted
    protected final NotifiableItemStackHandler inventory;

    public RAMSlotPartMachine(IMachineBlockEntity holder) {
        super(holder);
        this.inventory = new NotifiableItemStackHandler(this, SLOTS, IO.NONE).setFilter(RAMRegistry::isRAM);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public long getTotalThroughput() {
        long throughput = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            RAMRegistry.RAMEntry entry = RAMRegistry.getEntry(stack);
            if (entry != null) throughput += entry.throughput();
        }
        return throughput;
    }

    @Override
    public ModularUI createUI(Player player) {
        ModularUI ui = new ModularUI(176, 166, this, player)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(8, 8, getBlockState().getBlock().getDescriptionId()))
                .widget(UITemplate.bindPlayerInventory(player.getInventory(), GuiTextures.SLOT, 7, 84, true));
        for (int i = 0; i < SLOTS; i++) {
            ui.widget(new SlotWidget(inventory, i, 44 + i * 18, 30).setBackgroundTexture(GuiTextures.SLOT));
        }
        return ui;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return true;
    }

    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(inventory.storage);
    }
}
