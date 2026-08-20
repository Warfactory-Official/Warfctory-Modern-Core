package com.norwood.wfcore.common.machine.crafting;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import com.norwood.wfcore.gui.CraftingStationUI;
import org.jetbrains.annotations.Nullable;


public class CraftingStationMachine extends MultiblockControllerMachine implements IMuiMachine {


    public static final int GRID_SIZE = 9;
    public static final int STORAGE_SIZE = BigStackItemHandler.SLOTS;
    public static final int BAY_SIZE = 15;

    private static final int PULL_INTERVAL = 20;

    // Deliberately plain handlers rather than machine traits: the store is not a recipe inventory, and a
    // NotifiableItemStackHandler would both expose it to recipe logic and clamp its counts to a stack on
    // save. @SaveField persists them through GregTech 8.0.0's sync system instead — both are final, which
    // is what NBTSerializableTransformer needs, since it deserializes into the existing object.
    @SaveField
    private final BigStackItemHandler storage = new BigStackItemHandler(STORAGE_SIZE);
    @SaveField
    private final ToolBayHandler toolBay = new ToolBayHandler(BAY_SIZE);

    @Nullable
    private TickableSubscription tickSub;
    private int pullTimer;

    public CraftingStationMachine(BlockEntityCreationInfo info) {
        super(info);

        // MetaMachine is a BlockEntity in 8.0.0, so marking the chunk dirty is plain setChanged().
        storage.setOnContentsChanged(this::setChanged);
        toolBay.setOnContentsChanged(this::setChanged);
    }

    public BigStackItemHandler getStorage() {
        return storage;
    }

    public ToolBayHandler getToolBay() {
        return toolBay;
    }


    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        // MetaMachine only drops attached traits, and neither of these is one.
        if (getLevel() != null) {
            storage.dropInventoryInWorld(getLevel(), getBlockPos());
            toolBay.dropInventoryInWorld(getLevel(), getBlockPos());
        }
    }


    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickPullBuses);
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

    private void tickPullBuses() {
        if (isRemote() || !isFormed()) return;
        if (++pullTimer < PULL_INTERVAL) return;
        pullTimer = 0;
        for (MultiblockPartMachine part : getParts()) {
            if (part instanceof ItemBusPartMachine bus) {
                IItemHandlerModifiable busInv = bus.getInventory().storage;
                for (int i = 0; i < busInv.getSlots(); i++) {
                    ItemStack stack = busInv.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(storage, stack, false);
                    if (remainder.getCount() != stack.getCount()) {
                        busInv.setStackInSlot(i, remainder);
                    }
                }
            }
        }
    }


    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        CraftingStationUI.build(this, mainWidget, syncManager);
    }
}
