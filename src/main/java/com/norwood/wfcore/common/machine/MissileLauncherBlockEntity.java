package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.gui.MissileLauncherGui;

/**
 * Custom block entity for the missile launch silo so it can host a ModularUI (brachy fork) screen with the
 * coordinate dispatch panel + chunk-map picker, opened from {@link MissileLauncherMachine#onUse}. Mirrors
 * {@link ResearchUnitBlockEntity} (GTCEu's {@code MetaMachineBlockEntity} only exposes the LDLib UI system).
 */
public class MissileLauncherBlockEntity extends MetaMachineBlockEntity implements IUIHolder<PosGuiData> {

    public MissileLauncherBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public ModularPanel<?> buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (getMetaMachine() instanceof MissileLauncherMachine machine) {
            return MissileLauncherGui.build(machine, data, syncManager, settings);
        }
        return ModularPanel.defaultPanel("missile_launcher");
    }

    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel<?> panel) {
        return new ModularScreen(WFCore.MOD_ID, panel);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The missile model stands on the pad and, mid-launch, streaks up to the spawn height (~20 blocks up)
        // plus its own length; inflate generously so it isn't frustum-culled while rising.
        return new AABB(getBlockPos()).inflate(32);
    }
}
