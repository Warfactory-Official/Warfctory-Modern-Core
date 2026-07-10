package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import com.atsuishio.superbwarfare.block.ContainerBlock;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.common.item.PackagedVehicleItem;
import org.jetbrains.annotations.Nullable;

/**
 * MV light ground vehicle factory: spawns a Superb Warfare vehicle (e.g. the LAV-150) from the
 * recipe's {@link PackagedVehicleItem} output, reusing Superb Warfare's crate clearance check
 * ({@link ContainerBlock#canOpen}) for the obstruction test.
 */
public class LightGroundVehicleFactoryMachine extends AbstractVehicleFactoryMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            LightGroundVehicleFactoryMachine.class,
            AbstractVehicleFactoryMachine.MANAGED_FIELD_HOLDER);

    private static final long NO_POS = Long.MIN_VALUE;

    // Min/max corners of the concrete bed,
    @Persisted
    @DescSynced
    private long bedMinKey = NO_POS;

    @Persisted
    @DescSynced
    private long bedMaxKey = NO_POS;

    public LightGroundVehicleFactoryMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public float getSpawnYaw() {
        return getFrontFacing().getClockWise().toYRot();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed(); // resolves spawnPosKey
        Level level = getLevel();
        if (level == null) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : getMultiblockState().getCache()) {
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
    public void onStructureInvalid() {
        super.onStructureInvalid();
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
        for (BlockPos pos : getMultiblockState().getCache()) {
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
     * players. The corners are read from the {@code @DescSynced} fields resolved on the server and
     * synced to the client, so this works on both sides without touching the structure cache.
     *
     * <p>
     * Note: the hard deploy obstruction check ({@link #isSpawnClear} / {@link #isDeployBlocked})
     * is intentionally left unchanged — it still tests only the vehicle's entity footprint around the
     * spawn point, not the full bed.
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
        // Footprint: full bed x/z extent; vertical: from one above the bed surface up 4 blocks.
        return new AABB(min.getX(), bedY + 1, min.getZ(),
                max.getX() + 1, bedY + 1 + 4, max.getZ() + 1);
    }

    @Override
    protected boolean isSpawnClear(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (!ContainerBlock.canOpen(level, pos, type, null)) {
            return false;
        }
        AABB footprint = type.getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.getEntities((Entity) null, footprint, EntitySelector.NO_SPECTATORS).isEmpty();
    }

    @Override
    protected boolean deploy(ServerLevel level, BlockPos pos, ItemStack vehicleItem) {
        ResourceLocation id = PackagedVehicleItem.getEntityId(vehicleItem);
        if (id == null) {
            return false;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            return false;
        }
        if (!isSpawnClear(level, pos, type)) {
            return false;
        }
        Entity entity = type.create(level);
        if (entity == null) {
            return false;
        }
        entity.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        entity.setYRot(getSpawnYaw());
        if (entity instanceof VehicleEntity vehicle) {
            vehicle.getEntityData().set(VehicleEntity.SERVER_YAW, getSpawnYaw());
        }
        return level.addFreshEntity(entity);
    }
}
