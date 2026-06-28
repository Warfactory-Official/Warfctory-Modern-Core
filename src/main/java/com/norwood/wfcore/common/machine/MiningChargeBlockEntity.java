package com.norwood.wfcore.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.common.block.ChargeOverlayTracker;
import com.norwood.wfcore.common.data.WFBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Data for a placed mining charge. Retains the UUID of whoever placed it (synced to clients) so an upcoming
 * feature can outline a player's own charges when they sneak.
 */
public class MiningChargeBlockEntity extends BlockEntity {

    @Nullable
    private UUID placer;

    public MiningChargeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            ChargeOverlayTracker.add(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            ChargeOverlayTracker.remove(worldPosition);
        }
    }

    @Nullable
    public UUID getPlacer() {
        return placer;
    }

    public void setPlacer(@Nullable UUID placer) {
        this.placer = placer;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (placer != null) {
            tag.putUUID("Placer", placer);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        placer = tag.hasUUID("Placer") ? tag.getUUID("Placer") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    public static BlockEntityType<MiningChargeBlockEntity> type() {
        return WFBlocks.MINING_CHARGE_BE.get();
    }
}
