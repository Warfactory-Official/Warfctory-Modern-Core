package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.capability.WFCapabilities;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.norwood.wfcore.common.data.WFMachines;

/**
 * The Large Transformer's AC converter hatch. An INPUT hatch buffers AC EU arriving from a cable (drained by
 * the transformer into DC); an OUTPUT hatch is a source the transformer pushes DC-converted AC into, which it
 * forwards to the connected cable. Exposes WFCore's AC capability on its front face (see
 * {@link ACHatchBlockEntity}).
 */
public class ACHatchPartMachine extends TieredPartMachine implements IACEnergyContainer {


    private final boolean isOutput;
    private final long capacity;
    @SaveField
    private long stored;

    public ACHatchPartMachine(BlockEntityCreationInfo info, int tier, boolean isOutput) {
        super(info, tier);
        this.isOutput = isOutput;
        this.capacity = GTValues.V[tier] * 64L;
    }

    public boolean isOutput() {
        return isOutput;
    }

    public long getStored() {
        return stored;
    }

    /** INPUT side: the transformer pulls buffered AC out to convert to DC. */
    public long drainBuffer(long max) {
        long drained = Math.min(max, stored);
        stored -= drained;
        return drained;
    }


    public long pushAC(long amount, boolean simulate) {
        if (!isOutput || amount <= 0) return 0;
        IACEnergyContainer cable = getLevel().getCapability(WFCapabilities.CAPABILITY_AC_ENERGY,
                getBlockPos().relative(getFrontFacing()), getFrontFacing().getOpposite());
        if (cable == null) return 0;
        return cable.acceptEnergy(getFrontFacing().getOpposite(), amount, simulate);
    }


    public boolean hasDestination() {
        if (!isOutput) return false;
        IACEnergyContainer cable = getLevel().getCapability(WFCapabilities.CAPABILITY_AC_ENERGY,
                getBlockPos().relative(getFrontFacing()), getFrontFacing().getOpposite());
        return cable != null && cable.hasDestination(getFrontFacing().getOpposite());
    }

    // ------------------------------------------------------------------ IACEnergyContainer

    @Override
    public long acceptEnergy(Direction side, long amount, boolean simulate) {
        if (isOutput || amount <= 0) return 0;
        long accept = Math.min(amount, capacity - stored);
        if (accept <= 0) return 0;
        if (!simulate) stored += accept;
        return accept;
    }

    @Override
    public long getThroughput() {
        return capacity;
    }

    @Override
    public boolean inputsAC(Direction side) {
        return !isOutput && side == getFrontFacing();
    }

    @Override
    public boolean outputsAC(Direction side) {
        return isOutput && side == getFrontFacing();
    }

    // ------------------------------------------------------------------ part plumbing

    // The 7.5.3 override of shouldOpenUI that used to sit here suppressed a UI this part never had.
    // In 8.0.0 the hook belongs to IMuiMachine, which this part does not implement, so nothing opens.

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String substructureName) {
        return false;
    }

    // GT 8.0.0's sync_system reads @SaveField/@SyncToClient annotations directly, so LDLib's
    // ManagedFieldHolder (and the getFieldHolder override that exposed it) has no counterpart.

    /**
     * Merged in from the former ACHatchBlockEntity, which existed only because GT 7.5.3's
     * MetaMachineBlockEntity hard-coded its capability dispatch to GregTech's own capabilities.
     * On NeoForge the provider is registered against the block instead, from {@link WFMachines}.
     */
    public static void attachCapabilities(RegisterCapabilitiesEvent event, Block block) {
        event.registerBlock(WFCapabilities.CAPABILITY_AC_ENERGY,
                (level, pos, state, blockEntity, side) -> {
                    if (!(blockEntity instanceof ACHatchPartMachine hatch)) return null;
                    // Cables may only tap the hatch's front face (or a side-agnostic query).
                    return side == null || side == hatch.getFrontFacing() ? hatch : null;
                },
                block);
    }
}
