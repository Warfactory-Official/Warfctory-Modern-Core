package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.gui.MissileLauncherGui;
import com.wf.wfballistics.sim.IMissileListener;
import com.wf.wfballistics.sim.MissileListenerRegistry;

/**
 * Custom block entity for the missile launch silo so it can host a ModularUI (brachy fork) screen with the
 * coordinate dispatch panel + chunk-map picker, opened from {@link MissileLauncherMachine#onUse}. Mirrors
 * {@link ResearchUnitBlockEntity} (GTCEu's {@code MetaMachineBlockEntity} only exposes the LDLib UI system).
 */
public class MissileLauncherBlockEntity extends MetaMachineBlockEntity
                                        implements IUIHolder<PosGuiData>, IMissileListener {

    public static final double MISSILE_LISTENER_RANGE = 512.0;

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

    public void registerMissileListener() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MissileListenerRegistry.get(serverLevel).register(getBlockPos(), this);
        }
    }

    public void deregisterMissileListener() {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            MissileListenerRegistry.get(serverLevel).deregister(getBlockPos());
        }
    }

    @Override
    public Vec3 listenerCenter() {
        return Vec3.atCenterOf(getBlockPos());
    }

    @Override
    public double listenerRange() {
        return MISSILE_LISTENER_RANGE;
    }

    @Override
    public boolean listenerValid() {
        return !isRemoved() && getMetaMachine() instanceof MissileLauncherMachine machine && machine.isFormed();
    }
}
