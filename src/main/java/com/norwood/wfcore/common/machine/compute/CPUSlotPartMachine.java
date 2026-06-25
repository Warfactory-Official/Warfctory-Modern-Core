package com.norwood.wfcore.common.machine.compute;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

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
public class CPUSlotPartMachine extends MultiblockPartMachine implements ICpuSlot, IUIMachine, IMachineLife {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(CPUSlotPartMachine.class,
            MultiblockPartMachine.MANAGED_FIELD_HOLDER);

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

    @Persisted
    protected final NotifiableItemStackHandler inventory;

    public CPUSlotPartMachine(IMachineBlockEntity holder) {
        super(holder);
        this.inventory = new NotifiableItemStackHandler(this, 1, IO.NONE, IO.BOTH) {

            @Override
            public void onContentsChanged() {
                super.onContentsChanged();
                if (!isRemote()) {
                    scheduleRenderUpdate();
                    notifyComputeDirty();
                }
            }
        }.setFilter(CPURegistry::isCPU);
    }

    private void notifyComputeDirty() {
        for (IMultiController controller : getControllers()) {
            if (controller instanceof MainframeMachine mainframe) {
                mainframe.markComputeDirty();
            }
        }
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
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
    public ModularUI createUI(Player player) {
        return new ModularUI(176, 166, this, player)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(8, 8, getBlockState().getBlock().getDescriptionId()))
                .widget(new SlotWidget(inventory, 0, 80, 30).setBackgroundTexture(GuiTextures.SLOT))
                .widget(UITemplate.bindPlayerInventory(player.getInventory(), GuiTextures.SLOT, 7, 84, true));
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
