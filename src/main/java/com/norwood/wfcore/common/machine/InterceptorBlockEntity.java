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
import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import com.norwood.wfcore.common.gui.InterceptorGui;

/**
 * Custom block entity for the interceptor battery so it can host a ModularUI (brachy fork) status screen,
 * opened from {@link InterceptorMachine#onUse} (GTCEu's {@code MetaMachineBlockEntity} only exposes the LDLib
 * UI system). Mirrors {@link MissileLauncherBlockEntity}.
 */
public class InterceptorBlockEntity extends MetaMachineBlockEntity implements IUIHolder<PosGuiData> {

    public InterceptorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public ModularPanel<?> buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        if (getMetaMachine() instanceof InterceptorMachine machine) {
            return InterceptorGui.build(machine, data, syncManager, settings);
        }
        return ModularPanel.defaultPanel("interceptor");
    }

    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel<?> panel) {
        return new ModularScreen(WFCore.MOD_ID, panel);
    }

    @Override
    public AABB getRenderBoundingBox() {
        // The iron dome rides above the controller and spans the whole battery footprint; inflate generously
        // so the GLTF model (drawn by GltfMachineRenderer) is never frustum-culled when only its top shows.
        return new AABB(getBlockPos()).inflate(24);
    }

    @Override
    public void setRemoved() {
        // Drop the render mask so the hidden upper-layer blocks reappear when the controller is broken or its
        // chunk unloads (the BER, which would otherwise unregister it, stops being called once this BE is gone).
        if (level != null && level.isClientSide) {
            RenderMaskManager.removeDisableModel(getBlockPos());
        }
        super.setRemoved();
    }
}
