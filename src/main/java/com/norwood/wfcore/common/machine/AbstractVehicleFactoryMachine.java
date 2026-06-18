package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Abstract crafting multiblock that runs a standard GT recipe map and, on completion, spawns a
 * vehicle at a designated spot instead of ejecting an output item.
 *
 * <p>
 * The recipe's single item output (a vehicle-encoding item) lands in {@link #vehicleOutput} — a
 * controller-owned {@code IO.OUT} handler, so it never reaches a player-accessible bus. A throttled
 * server tick then resolves that item and, when {@link #getSpawnPos()} is clear, spawns the vehicle
 * via the subclass {@link #deploy}. If the area is obstructed the finished vehicle is held and retried.
 */
public abstract class AbstractVehicleFactoryMachine extends WorkableElectricMultiblockMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            AbstractVehicleFactoryMachine.class,
            WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    protected final NotifiableItemStackHandler vehicleOutput;

    @Nullable
    protected TickableSubscription deploySub;

    public AbstractVehicleFactoryMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        this.vehicleOutput = new NotifiableItemStackHandler(this, 1, IO.OUT);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (!isRemote()) {
            deploySub = subscribeServerTick(this::tryDeploy);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (deploySub != null) {
            deploySub.unsubscribe();
            deploySub = null;
        }
    }

    protected void tryDeploy() {
        if (isRemote() || getOffsetTimer() % 10 != 0 || !(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack out = vehicleOutput.getStackInSlot(0);
        if (out.isEmpty()) {
            return;
        }
        if (deploy(serverLevel, getSpawnPos(), out)) {
            vehicleOutput.storage.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    /** The world position the finished vehicle is deployed to. Default: 4 blocks in front, 1 up. */
    public BlockPos getSpawnPos() {
        return getPos().relative(getFrontFacing(), 4).above();
    }

    public float getSpawnYaw() {
        Direction facing = getFrontFacing();
        return facing.toYRot();
    }

    /** Client-synced working state, used to drive the GeckoLib animation. */
    public boolean isWorking() {
        return getRecipeLogic() != null && getRecipeLogic().isWorking();
    }

    /**
     * Spawn the vehicle encoded by {@code vehicleItem} at {@code pos}. Return true only if it was
     * spawned (the output item is then consumed); false to keep retrying (e.g. area obstructed).
     */
    protected abstract boolean deploy(ServerLevel level, BlockPos pos, ItemStack vehicleItem);
}
