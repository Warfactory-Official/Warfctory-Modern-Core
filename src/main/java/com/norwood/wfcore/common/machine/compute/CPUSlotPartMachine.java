package com.norwood.wfcore.common.machine.compute;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import com.norwood.wfcore.common.compute.CPURegistry;
import com.norwood.wfcore.common.machine.MainframeMachine;
import org.jetbrains.annotations.Nullable;

/** Holds one CPU item; its stats feed the mainframe's compute output. */
public class CPUSlotPartMachine extends MultiblockPartMachine implements ICpuSlot, IMuiMachine {


    /** Whether the socket holds a CPU; drives the front-overlay model variant (empty vs filled). */
    public enum CpuFill implements StringRepresentable {

        EMPTY("empty", ""),
        FILLED("filled", "_filled");

        private final String name;
        private final String suffix;

        CpuFill(String name, String suffix) {
            this.name = name;
            this.suffix = suffix;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Texture suffix: empty for no CPU (cpu_slot.png), {@code _filled} otherwise (cpu_slot_filled.png). */
        public String suffix() {
            return suffix;
        }
    }

    public static final EnumProperty<CpuFill> CPU_FILL = EnumProperty.create("cpu_fill", CpuFill.class);

    @SaveField
    protected final NotifiableItemStackHandler inventory;

    public CPUSlotPartMachine(BlockEntityCreationInfo info) {
        super(info);
        this.inventory = attachTrait(new NotifiableItemStackHandler(1, IO.NONE, IO.BOTH) {

            @Override
            public void onContentsChanged() {
                super.onContentsChanged();
                if (!isRemote()) {
                    scheduleRenderUpdate();
                    notifyComputeDirty();
                }
            }
        }.setFilter(CPURegistry::isCPU));
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

    @Override
    public void scheduleRenderUpdate() {
        if (!isRemote()) {
            setRenderState(getRenderState().setValue(CPU_FILL,
                    inventory.getStackInSlot(0).isEmpty() ? CpuFill.EMPTY : CpuFill.FILLED));
        }
        super.scheduleRenderUpdate();
    }

    @Override
    @Nullable
    public CPURegistry.CPUEntry getStats() {
        ItemStack stack = inventory.getStackInSlot(0);
        return CPURegistry.isCPU(stack) ? CPURegistry.getEntry(stack) : null;
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.child(new ItemSlot()
                .slot(new ModularSlot(inventory.storage, 0).singletonSlotGroup(0))
                .background(GTGuiTextures.SLOT)
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
