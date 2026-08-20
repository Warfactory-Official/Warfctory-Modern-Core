package com.norwood.wfcore.common.machine.compute;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.mui.GTMuiMachineUtil;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.norwood.wfcore.common.compute.RAMRegistry;
import com.norwood.wfcore.common.machine.MainframeMachine;

/** Holds RAM items; their summed throughput caps how much CWU the mainframe can route. */
public class RAMSlotPartMachine extends MultiblockPartMachine implements IRamSlot, IMuiMachine {


    private static final int SLOTS = 4;

    /** How many of the four sockets show a stick; drives the front-overlay model variant. */
    public enum FillLevel implements StringRepresentable {

        L0("0"),
        L1("1"),
        L2("2"),
        L3("3"),
        L4("4");

        public static final FillLevel[] VALUES = values();
        private final String name;

        FillLevel(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Texture suffix: empty for 0 sockets (ram_socket.png), the count otherwise (ram_socketN.png). */
        public String suffix() {
            return this == L0 ? "" : name;
        }

        public static FillLevel of(int filled) {
            return VALUES[Math.min(Math.max(filled, 0), SLOTS)];
        }
    }

    public static final EnumProperty<FillLevel> RAM_FILL = EnumProperty.create("ram_fill", FillLevel.class);

    @SaveField
    protected final NotifiableItemStackHandler inventory;

    public RAMSlotPartMachine(BlockEntityCreationInfo info) {
        super(info);
        this.inventory = attachTrait(new NotifiableItemStackHandler(SLOTS, IO.NONE, IO.BOTH) {

            @Override
            public void onContentsChanged() {
                super.onContentsChanged();
                if (!isRemote()) {
                    scheduleRenderUpdate();
                    notifyComputeDirty();
                }
            }
        }.setFilter(RAMRegistry::isRAM));
    }

    private void notifyComputeDirty() {
        for (MultiblockControllerMachine controller : getControllers()) {
            if (controller instanceof MainframeMachine mainframe) {
                mainframe.markComputeDirty();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            scheduleRenderUpdate();
        }
    }

    private int filledCount() {
        int n = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (RAMRegistry.isRAM(inventory.getStackInSlot(i))) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void scheduleRenderUpdate() {
        if (!isRemote()) {
            setRenderState(getRenderState().setValue(RAM_FILL, FillLevel.of(filledCount())));
        }
        super.scheduleRenderUpdate();
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
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.child(GTMuiMachineUtil
                .createSlotGroupFromInventory(inventory.storage, "ram_inv", SLOTS, 'R', syncManager,
                        "R".repeat(SLOTS))
                .center());
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return true;
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String substructureName) {
        return false;
    }
}
