package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import com.norwood.wfcore.common.item.PackagedVehicleItem;
import com.norwood.wfcore.integration.combinedassault.CombinedAssaultVehicles;
import org.jetbrains.annotations.Nullable;
import com.wf.combinedassault.custom.vehicle.BaseVehicleData;

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


    /** Sentinel for {@link #spawnPosKey}: no spawn spot resolved yet (structure unformed / no bed found). */
    private static final long NO_POS = Long.MIN_VALUE;

    @SaveField
    protected final NotifiableItemStackHandler vehicleOutput;

    /**
     * The deploy spot, packed via {@link BlockPos#asLong()} and synced to the client (for the clearance
     * overlay). Resolved on the server when the structure forms — see {@link #computeSpawnPos()} — because
     * {@link #formStructure(String)} only runs server-side and the client has no structure cache to compute from.
     */
    @SaveField
    @SyncToClient
    private long spawnPosKey = NO_POS;

    @Nullable
    protected TickableSubscription deploySub;

    public AbstractVehicleFactoryMachine(BlockEntityCreationInfo info) {
        // 8.0.0 takes the recipe logic as a constructor argument; createRecipeLogic is gone, and the logic
        // no longer holds a machine reference of its own — it reaches the machine through getMachine().
        super(info, new DeployGatedRecipeLogic());
        this.vehicleOutput = attachTrait(new NotifiableItemStackHandler(1, IO.OUT));
    }

    /**
     * Custom recipe logic that refuses to <em>finish</em> the craft while the spawn area is obstructed: the
     * recipe holds at 100% in the WAITING state (no output produced, no new craft started) and rechecks every
     * tick, so processing pauses until the obstruction clears instead of completing into a stuck buffer.
     */
    protected static class DeployGatedRecipeLogic extends RecipeLogic {

        @Override
        public void onRecipeFinish() {
            if (getMachine() instanceof AbstractVehicleFactoryMachine vf && vf.isDeployBlocked(lastRecipe)) {
                setWaiting(Component.translatable("wfcore.vehicle_factory.obstructed"));
                return;
            }
            super.onRecipeFinish();
        }
    }

    /**
     * Checked right before the craft finishes: does the vehicle this recipe would produce have a clear spot to
     * spawn? Reads the pending vehicle from the recipe's item output and defers the actual clearance test to
     * {@link #isSpawnClear}. Returns false (not blocked) when it can't tell, so a mis-tagged recipe never
     * deadlocks the machine.
     */
    public boolean isDeployBlocked(@Nullable GTRecipe recipe) {
        if (recipe == null || !(getLevel() instanceof ServerLevel level)) {
            return false;
        }
        for (ItemStack out : RecipeHelper.getOutputItems(recipe)) {
            ResourceLocation id = PackagedVehicleItem.getVehicleId(out);
            if (id == null) {
                continue;
            }
            BaseVehicleData<?> data = CombinedAssaultVehicles.data(id);
            if (data != null && !isSpawnClear(level, spawnFootprint(data))) {
                return true;
            }
        }
        return false;
    }

    /** The volume that must be clear for {@code data} to deploy at {@link #getSpawnPos()}. */
    protected AABB spawnFootprint(@Nullable BaseVehicleData<?> data) {
        return CombinedAssaultVehicles.footprint(data, getSpawnPos(), getSpawnYaw(), getClearanceHeight());
    }

    /**
     * How many blocks of headroom the deploy spot needs. Bounds both the hard obstruction test and the
     * "keep clear" overlay, so a player who keeps the drawn box clear can always deploy.
     */
    protected int getClearanceHeight() {
        return 4;
    }

    /** Whether {@code footprint} is free of colliding blocks and of other entities. */
    protected boolean isSpawnClear(ServerLevel level, AABB footprint) {
        return level.noCollision(footprint) &&
                level.getEntities((Entity) null, footprint, EntitySelector.NO_SPECTATORS).isEmpty();
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        // Server-only hook: resolve the deploy spot from the actual structure and sync it to clients.
        BlockPos resolved = computeSpawnPos();
        this.spawnPosKey = resolved != null ? resolved.asLong() : NO_POS;
        if (!isRemote()) {
            deploySub = subscribeServerTick(this::tryDeploy);
        }
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);
        this.spawnPosKey = NO_POS;
        if (deploySub != null) {
            deploySub.unsubscribe();
            deploySub = null;
        }
    }

    // The overlay tracker is populated here (runs on both sides on chunk load) rather than in
    // onStructureFormed, which is server-only; the renderer gates on isFormed() (synced) before drawing.
    @Override
    public void onLoad() {
        super.onLoad();
        if (isRemote()) {
            VehicleFactoryOverlayTracker.add(getBlockPos());
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (isRemote()) {
            VehicleFactoryOverlayTracker.remove(getBlockPos());
        }
    }

    /**
     * The world-space box that must stay clear for the vehicle to deploy; drawn as the client "keep clear"
     * overlay on deployer multiblocks. Defaults to the single spawn block; subclasses can widen it to the
     * vehicle's footprint.
     */
    public AABB getClearanceBox() {
        return new AABB(getSpawnPos());
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

    /** The world position the finished vehicle is deployed to (resolved from the structure, synced to client). */
    public BlockPos getSpawnPos() {
        return spawnPosKey != NO_POS ? BlockPos.of(spawnPosKey) : getBlockPos().relative(getFrontFacing(), 4).above();
    }

    /**
     * Resolve the deploy spot from the freshly-formed structure (called server-side from
     * {@link #formStructure(String)}). Return {@code null} to fall back to "4 blocks in front, 1 up".
     */
    @Nullable
    protected BlockPos computeSpawnPos() {
        return null;
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
     * Whether a craft is in progress (running or stalled waiting for power), used to pick the GeckoLib
     * animation. The working loop is kept selected while waiting so a power stall freezes it in place
     * rather than dropping to idle.
     */
    public boolean isCrafting() {
        var logic = getRecipeLogic();
        return logic != null && (logic.isWorking() || logic.isWaiting());
    }

    /** Whether the working animation clock advances; false (waiting for power) freezes it in place. */
    public boolean isAnimAdvancing() {
        var logic = getRecipeLogic();
        return logic != null && logic.isWorking();
    }

    /**
     * Spawn the vehicle encoded by {@code vehicleItem} at {@code pos}. Return true only if it was
     * spawned (the output item is then consumed); false to keep retrying (e.g. area obstructed).
     */
    protected abstract boolean deploy(ServerLevel level, BlockPos pos, ItemStack vehicleItem);
}
