package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import com.flansmod.warforge.api.WarForgeCapabilities;
import com.flansmod.warforge.api.interfaces.IChunkReinforcer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Chunk Reinforcer controller. {@link MetaMachineBlockEntity}'s capability dispatch is
 * hardcoded to GregTech's known capabilities, so a custom one is needed to expose WarForge's
 * {@code CHUNK_REINFORCER} capability (queried generically by WarForge when resolving siege defence).
 */
public class ChunkReinforcerBlockEntity extends MetaMachineBlockEntity {

    public ChunkReinforcerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == WarForgeCapabilities.CHUNK_REINFORCER && getMetaMachine() instanceof IChunkReinforcer reinforcer) {
            return WarForgeCapabilities.CHUNK_REINFORCER.orEmpty(cap, LazyOptional.of(() -> reinforcer));
        }
        return super.getCapability(cap, side);
    }
}
