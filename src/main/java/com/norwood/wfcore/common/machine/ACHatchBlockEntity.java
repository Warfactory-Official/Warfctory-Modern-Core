package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.capability.WFCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for AC converter hatches. {@link MetaMachineBlockEntity}'s capability dispatch is hardcoded to
 * GregTech's known capabilities, so a custom one is needed to expose WFCore's {@link IACEnergyContainer} on
 * the hatch's front face for cables to connect to.
 */
public class ACHatchBlockEntity extends MetaMachineBlockEntity {

    public ACHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == WFCapabilities.CAPABILITY_AC_ENERGY && getMetaMachine() instanceof IACEnergyContainer ac) {
            if (side == null || side == getMetaMachine().getFrontFacing()) {
                return WFCapabilities.CAPABILITY_AC_ENERGY.orEmpty(cap, LazyOptional.of(() -> ac));
            }
        }
        return super.getCapability(cap, side);
    }
}
