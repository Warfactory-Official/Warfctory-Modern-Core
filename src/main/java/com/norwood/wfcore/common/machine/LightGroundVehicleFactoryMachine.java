package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.atsuishio.superbwarfare.block.ContainerBlock;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.common.item.PackagedVehicleItem;

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
    protected boolean deploy(ServerLevel level, BlockPos pos, ItemStack vehicleItem) {
        ResourceLocation id = PackagedVehicleItem.getEntityId(vehicleItem);
        if (id == null) {
            return false;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            return false;
        }
        // Superb Warfare's own crate clearance check.
        if (!ContainerBlock.canOpen(level, pos, type, null)) {
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
