package com.norwood.wfcore.common.pipenet.ac;

import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.common.blockentity.LaserPipeBlockEntity;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.capability.WFCapabilities;
import com.norwood.wfcore.common.pipenet.ac.net.ACNetHandler;
import com.norwood.wfcore.common.pipenet.ac.net.ACPipeNet;
import com.norwood.wfcore.common.pipenet.ac.net.LevelACPipeNet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;

public class ACPipeBlockEntity extends PipeBlockEntity<ACPipeType, ACPipeProperties> {

    protected final EnumMap<Direction, ACNetHandler> handlers = new EnumMap<>(Direction.class);
    private WeakReference<ACPipeNet> currentPipeNet = new WeakReference<>(null);
    protected ACNetHandler defaultHandler;

    public ACPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static void onBlockEntityRegister(BlockEntityType<ACPipeBlockEntity> type) {}

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == WFCapabilities.CAPABILITY_AC_ENERGY) {
            if (getLevel().isClientSide()) {
                return WFCapabilities.CAPABILITY_AC_ENERGY.orEmpty(cap,
                        LazyOptional.of(() -> IACEnergyContainer.DEFAULT));
            }
            if (side != null && !isConnected(side)) return LazyOptional.empty();
            if (handlers.isEmpty()) {
                initHandlers();
            }
            checkNetwork();
            return WFCapabilities.CAPABILITY_AC_ENERGY.orEmpty(cap,
                    LazyOptional.of(() -> handlers.getOrDefault(side, defaultHandler)));
        }
        return super.getCapability(cap, side);
    }

    @Override
    public boolean canHaveBlockedFaces() {
        return false;
    }

    public void initHandlers() {
        ACPipeNet net = getACPipeNet();
        if (net == null) return;
        for (Direction facing : GTUtil.DIRECTIONS) {
            handlers.put(facing, new ACNetHandler(net, this, facing));
        }
        defaultHandler = new ACNetHandler(net, this, null);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            ACPipeNet current = getACPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (ACNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
            }
        }
    }

    public ACPipeNet getACPipeNet() {
        if (level == null || level.isClientSide) {
            return null;
        }
        ACPipeNet cur = this.currentPipeNet.get();
        if (cur != null && cur.isValid() && cur.containsNode(getPipePos())) {
            return cur;
        }
        LevelACPipeNet worldNet = (LevelACPipeNet) getPipeBlock().getWorldPipeNet((ServerLevel) getPipeLevel());
        cur = worldNet.getNetFromPos(getPipePos());
        if (cur != null) {
            this.currentPipeNet = new WeakReference<>(cur);
        }
        return cur;
    }

    public void setActive(boolean active, int duration) {
        LaserPipeBlockEntity.setPipeActive(this, getBlockState(), active, duration);
    }

    public boolean isActive() {
        return getBlockState().getValue(GTBlockStateProperties.ACTIVE);
    }

    @Override
    public boolean canAttachTo(Direction side) {
        if (level != null) {
            BlockEntity neighbour = level.getBlockEntity(getBlockPos().relative(side));
            if (neighbour instanceof ACPipeBlockEntity) {
                return false;
            }
            return neighbour != null &&
                    neighbour.getCapability(WFCapabilities.CAPABILITY_AC_ENERGY, side.getOpposite()).isPresent();
        }
        return false;
    }

    @Override
    public void setConnection(Direction side, boolean connected, boolean fromNeighbor) {
        // point-to-point: a cable may only connect along a single straight line (like laser pipes)
        if (!getLevel().isClientSide && connected) {
            int connections = getConnections();
            connections &= ~(1 << side.ordinal());
            connections &= ~(1 << side.getOpposite().ordinal());
            if (connections != 0) return;

            BlockEntity tile = getLevel().getBlockEntity(getBlockPos().relative(side));
            if (tile instanceof IPipeNode<?, ?> pipeTile &&
                    pipeTile.getPipeType().getClass() == this.getPipeType().getClass()) {
                connections = pipeTile.getConnections();
                connections &= ~(1 << side.ordinal());
                connections &= ~(1 << side.getOpposite().ordinal());
                if (connections != 0) return;
            }
        }
        super.setConnection(side, connected, fromNeighbor);
    }

    @Override
    public GTToolType getPipeTuneTool() {
        return GTToolType.WIRE_CUTTER;
    }
}
