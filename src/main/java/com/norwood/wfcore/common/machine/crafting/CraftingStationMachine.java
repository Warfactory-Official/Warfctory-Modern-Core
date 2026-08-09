package com.norwood.wfcore.common.machine.crafting;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import com.norwood.wfcore.gui.CraftingStationUI;
import org.jetbrains.annotations.Nullable;


public class CraftingStationMachine extends MultiblockControllerMachine implements IUIMachine, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            CraftingStationMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    public static final int GRID_START = 0;
    public static final int GRID_SIZE = 9;
    public static final int STORAGE_START = 10;
    public static final int STORAGE_SIZE = BigStackItemHandler.SLOTS;
    public static final int BAY_START = STORAGE_START + STORAGE_SIZE;   // 522
    public static final int BAY_SIZE = 15;
    public static final int PLAYER_START = BAY_START + BAY_SIZE;        // 537

    private static final int PULL_INTERVAL = 20;

    private final BigStackItemHandler storage = new BigStackItemHandler(STORAGE_SIZE);
    private final ToolBayHandler toolBay = new ToolBayHandler(BAY_SIZE);

    @Nullable
    private TickableSubscription tickSub;
    private int pullTimer;

    public CraftingStationMachine(IMachineBlockEntity holder) {
        super(holder);

        storage.setOnContentsChanged(this::onChanged);
        toolBay.setOnContentsChanged(this::onChanged);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public BigStackItemHandler getStorage() {
        return storage;
    }

    public ToolBayHandler getToolBay() {
        return toolBay;
    }


    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        tag.put("Storage", storage.saveBig());
        tag.put("ToolBay", toolBay.serializeNBT());
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("Storage")) storage.loadBig(tag.getCompound("Storage"));
        if (tag.contains("ToolBay")) toolBay.deserializeNBT(tag.getCompound("ToolBay"));
    }

    @Override
    public void onMachineRemoved() {
        clearInventory(storage);
        clearInventory(toolBay);
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
        for (IMultiPart part : getParts()) {
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
    public ModularUI createUI(Player entityPlayer) {
        return CraftingStationUI.build(this, entityPlayer);
    }
}
