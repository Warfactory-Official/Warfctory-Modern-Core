package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.capability.WFCapabilities;
import org.jetbrains.annotations.NotNull;

/**
 * The Large Transformer's AC converter hatch. An INPUT hatch buffers AC EU arriving from a cable (drained by
 * the transformer into DC); an OUTPUT hatch is a source the transformer pushes DC-converted AC into, which it
 * forwards to the connected cable. Exposes WFCore's AC capability on its front face (see
 * {@link ACHatchBlockEntity}).
 */
public class ACHatchPartMachine extends TieredPartMachine implements IACEnergyContainer {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(ACHatchPartMachine.class,
            TieredPartMachine.MANAGED_FIELD_HOLDER);

    private final boolean isOutput;
    private final long capacity;
    @Persisted
    private long stored;

    public ACHatchPartMachine(IMachineBlockEntity holder, int tier, boolean isOutput) {
        super(holder, tier);
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

    /** OUTPUT side: the transformer pushes converted AC, which is forwarded onto the connected cable. */
    public long pushAC(long amount) {
        if (!isOutput || amount <= 0) return 0;
        BlockEntity te = getLevel().getBlockEntity(getPos().relative(getFrontFacing()));
        if (te == null) return 0;
        IACEnergyContainer cable = te.getCapability(WFCapabilities.CAPABILITY_AC_ENERGY,
                getFrontFacing().getOpposite()).orElse(null);
        if (cable == null) return 0;
        return cable.acceptEnergy(getFrontFacing().getOpposite(), amount);
    }

    // ------------------------------------------------------------------ IACEnergyContainer

    @Override
    public long acceptEnergy(Direction side, long amount) {
        if (isOutput || amount <= 0) return 0;
        long accept = Math.min(amount, capacity - stored);
        if (accept <= 0) return 0;
        stored += accept;
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

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public boolean canShared() {
        return false;
    }

    @Override
    public @NotNull ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }
}
