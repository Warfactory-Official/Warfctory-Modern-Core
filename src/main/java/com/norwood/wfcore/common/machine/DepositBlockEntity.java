package com.norwood.wfcore.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the {@link DepositType} id and remaining yield of a single {@code wfcore:deposit} block. The drill
 * deducts from the yield and turns the block to bedrock when it runs dry; the renderer reads the type id (synced
 * to the client) to pick a texture.
 */
public class DepositBlockEntity extends BlockEntity {

    @Nullable
    private ResourceLocation depositTypeId;
    private int remainingYield;

    public DepositBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Worldgen entry point: set the type + starting yield. Client sync happens via {@link #getUpdateTag}. */
    public void init(ResourceLocation typeId, int yield) {
        this.depositTypeId = typeId;
        this.remainingYield = yield;
        setChanged();
    }

    @Nullable
    public ResourceLocation getDepositTypeId() {
        return depositTypeId;
    }

    @Nullable
    public DepositType getDepositType() {
        return depositTypeId == null ? null : WFDeposits.get(depositTypeId);
    }

    public int getRemainingYield() {
        return remainingYield;
    }

    /**
     * Deduct {@code amount} from this block's yield. When it reaches zero the block becomes bedrock.
     *
     * @return true if the block was depleted (now bedrock).
     */
    public boolean deplete(int amount) {
        remainingYield -= amount;
        if (remainingYield <= 0) {
            becomeBedrock();
            return true;
        }
        setChanged();
        return false;
    }

    public void becomeBedrock() {
        if (level != null && !level.isClientSide) {
            level.setBlock(worldPosition, Blocks.BEDROCK.defaultBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (depositTypeId != null) {
            tag.putString("DepositType", depositTypeId.toString());
        }
        tag.putInt("Yield", remainingYield);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        depositTypeId = tag.contains("DepositType") ? ResourceLocation.tryParse(tag.getString("DepositType")) : null;
        remainingYield = tag.getInt("Yield");
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

    public static BlockEntityType<DepositBlockEntity> type() {
        return WFBlocks.DEPOSIT_BE.get();
    }
}
