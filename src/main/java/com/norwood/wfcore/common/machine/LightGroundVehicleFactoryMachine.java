package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.data.GTBlocks;

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

    public LightGroundVehicleFactoryMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
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

    @Override
    public AABB getClearanceBox() {
        return new AABB(getSpawnPos()).inflate(1.0, 0.0, 1.0).expandTowards(0.0, 1.0, 0.0);
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
