package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.item.PackagedVehicleItem;
import com.norwood.wfcore.integration.combinedassault.VehicleGate;
import org.jetbrains.annotations.Nullable;

/**
 * MV light ground vehicle factory: spawns a CombinedAssault vehicle from the recipe's
 * {@link PackagedVehicleItem} output, after checking the spawn point is clear of blocks and entities.
 */
public class LightGroundVehicleFactoryMachine extends AbstractVehicleFactoryMachine {


    private static final long NO_POS = Long.MIN_VALUE;

    // Min/max corners of the concrete bed,
    @SaveField
    @SyncToClient
    private long bedMinKey = NO_POS;

    @SaveField
    @SyncToClient
    private long bedMaxKey = NO_POS;

    public LightGroundVehicleFactoryMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public float getSpawnYaw() {
        return getFrontFacing().getClockWise().toYRot();
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName); // resolves spawnPosKey
        Level level = getLevel();
        if (level == null) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : getDefaultPatternState().getCache().keySet()) {
            BlockPos pos = BlockPos.of(key);
            if (level.getBlockState(pos).is(GTBlocks.LIGHT_CONCRETE.get())) {
                if (pos.getX() < minX) minX = pos.getX();
                if (pos.getY() < minY) minY = pos.getY();
                if (pos.getZ() < minZ) minZ = pos.getZ();
                if (pos.getX() > maxX) maxX = pos.getX();
                if (pos.getY() > maxY) maxY = pos.getY();
                if (pos.getZ() > maxZ) maxZ = pos.getZ();
            }
        }
        if (maxX >= minX) {
            // Bed is a single Y layer, so minY == maxY here.
            this.bedMinKey = new BlockPos(minX, minY, minZ).asLong();
            this.bedMaxKey = new BlockPos(maxX, maxY, maxZ).asLong();
        }
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);
        this.bedMinKey = NO_POS;
        this.bedMaxKey = NO_POS;
    }

    @Override
    @Nullable
    protected BlockPos computeSpawnPos() {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        long sx = 0;
        long sy = 0;
        long sz = 0;
        int n = 0;
        for (long key : getDefaultPatternState().getCache().keySet()) {
            BlockPos pos = BlockPos.of(key);
            if (level.getBlockState(pos).is(GTBlocks.LIGHT_CONCRETE.get())) {
                sx += pos.getX();
                sy += pos.getY();
                sz += pos.getZ();
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        return new BlockPos((int) (sx / n), (int) (sy / n) + 1, (int) (sz / n));
    }

    /**
     * Full concrete-bed footprint extruded 4 blocks upward — the "keep-clear" overlay shown to
     * players. The corners are read from the {@code @SyncToClient} fields resolved on the server and
     * synced to the client, so this works on both sides without touching the structure cache.
     *
     * <p>
     * The hard obstruction check tests only the vehicle's own footprint around the spawn point, not the
     * full bed — but it shares this box's height, so anything it rejects is inside the drawn overlay.
     */
    @Override
    public AABB getClearanceBox() {
        if (bedMinKey == NO_POS || bedMaxKey == NO_POS) {
            // Not yet synced (structure just forming or not formed); fall back to the single-block default.
            return super.getClearanceBox();
        }
        BlockPos min = BlockPos.of(bedMinKey);
        BlockPos max = BlockPos.of(bedMaxKey);
        // bedY == min.getY() == max.getY() (single Y layer).
        int bedY = min.getY();
        // Footprint: full bed x/z extent; vertical: from one above the bed surface up the clearance height.
        return new AABB(min.getX(), bedY + 1, min.getZ(),
                max.getX() + 1, bedY + 1 + getClearanceHeight(), max.getZ() + 1);
    }

    @Override
    protected boolean deploy(ServerLevel level, BlockPos pos, ItemStack vehicleItem) {
        ResourceLocation id = PackagedVehicleItem.getVehicleId(vehicleItem);
        if (!VehicleGate.isKnown(id)) {
            // Consume rather than retry: an unknown vehicle would wedge the factory forever.
            WFCore.LOGGER.warn("{} produced unknown vehicle '{}'; discarding", getBlockPos(), id);
            return true;
        }
        if (!isSpawnClear(level, spawnFootprint(id))) {
            return false;
        }
        return VehicleGate.summon(level, pos, getSpawnYaw(), id);
    }
}
