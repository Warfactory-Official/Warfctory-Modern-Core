package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.gui.ResearchTreeGui;

/**
 * Custom block entity for the research unit so it can host a ModularUI (brachy fork) research-tree screen,
 * opened from {@link ResearchUnitMachine#onUse}. GTCEu's own {@code MetaMachineBlockEntity} only exposes the
 * LDLib UI system, so the research tree (which needs ModularUI's drag-pan ScrollWidget) rides on its own
 * {@link IUIHolder} here.
 */
public class ResearchUnitBlockEntity extends MetaMachineBlockEntity implements IUIHolder<PosGuiData> {

    public ResearchUnitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public ModularPanel<?> buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (getMetaMachine() instanceof ResearchUnitMachine machine) {
            return ResearchTreeGui.build(machine, data, syncManager, settings);
        }
        return ModularPanel.defaultPanel("research_unit");
    }

    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel<?> panel) {
        return new ModularScreen(WFCore.MOD_ID, panel);
    }
}
