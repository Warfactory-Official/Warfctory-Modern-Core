package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import com.norwood.wfcore.common.mui.WFMuiWidgets;
import com.norwood.wfcore.radar.data.CalibratorData;

import java.util.List;

public class CalibratorMachine extends MultiblockControllerMachine implements IMuiMachine {


    public CalibratorMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    private void register() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            CalibratorData.get(serverLevel).add(getBlockPos());
        }
    }

    private void deregister() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            CalibratorData.get(serverLevel).remove(getBlockPos());
        }
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        register();
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);
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
    public void onMachineDestroyed() {
        deregister();
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.child(WFMuiWidgets.displayBox(getWidgetsForDisplay(syncManager)).margin(4, 2));
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        List<IWidget> widgets = super.getWidgetsForDisplay(syncManager);
        BooleanSyncValue isFormedSync = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));

        widgets.add(Text.lang("wfcore.machine.satellite_distance_calibrator.name").asWidget());
        widgets.add(Text.dynamic(() -> getStatusText(isFormedSync.getBoolValue())).asWidget());
        widgets.add(Text.lang("wfcore.gui.calibrator.hint").asWidget());
        return widgets;
    }

    private Component getStatusText(boolean formed) {
        if (!formed) {
            return Component.literal("§cIncomplete structure");
        }
        return Component.literal(String.format("§aOnline §7(%d, %d, %d)",
                getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ()));
    }
}
