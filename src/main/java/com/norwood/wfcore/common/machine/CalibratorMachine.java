package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.server.level.ServerLevel;

import com.norwood.wfcore.radar.data.CalibratorData;

public class CalibratorMachine extends MultiblockControllerMachine implements IFancyUIMachine, IMachineLife {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            CalibratorMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    public CalibratorMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    private void register() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            CalibratorData.get(serverLevel).add(getPos());
        }
    }

    private void deregister() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            CalibratorData.get(serverLevel).remove(getPos());
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        register();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        deregister();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote() && isFormed()) {
            register();
        }
    }

    @Override
    public void onMachineRemoved() {
        deregister();
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 170, 54);
        group.addWidget(new ImageWidget(4, 4, 162, 46, GuiTextures.DISPLAY));
        group.addWidget(new LabelWidget(8, 8, "wfcore.machine.satellite_distance_calibrator.name"));
        group.addWidget(new LabelWidget(8, 22, this::getStatusText).setTextColor(-1).setDropShadow(true));
        group.addWidget(new LabelWidget(8, 34, "wfcore.gui.calibrator.hint").setTextColor(-1).setDropShadow(true));
        return group;
    }

    private String getStatusText() {
        if (!isFormed()) {
            return "§cIncomplete structure";
        }
        return String.format("§aOnline §7(%d, %d, %d)", getPos().getX(), getPos().getY(), getPos().getZ());
    }
}
