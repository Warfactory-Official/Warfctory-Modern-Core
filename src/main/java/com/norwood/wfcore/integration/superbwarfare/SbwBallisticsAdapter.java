package com.norwood.wfcore.integration.superbwarfare;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.ballistics.BallisticsAdapter;
import com.norwood.wfcore.common.ballistics.DeferredImpact;
import com.norwood.wfcore.common.ballistics.VirtualProjectile;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;

import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public final class SbwBallisticsAdapter implements BallisticsAdapter {

    private static final ResourceLocation ID = WFCore.id("sbw_projectile");

    private static final String KEY_TYPE = "SbwType";
    private static final String KEY_SAVE = "SbwSave";
    private static final String KEY_EXPLOSIVE = "SbwExplosive";

    private static final double BULLET_GRAVITY_FALLBACK = 0.05D;

    private static final double DRAG_DEFAULT = 0.01D;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean matches(Entity entity) {

        return entity instanceof FastThrowableProjectile || entity instanceof ProjectileEntity;
    }

    @Override
    public VirtualProjectile capture(Entity liveProjectile, int currentTick) {
        EntityType<?> type = liveProjectile.getType();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        if (typeId == null) {
            return null;
        }

        Vec3 pos = liveProjectile.position();
        Vec3 vel = liveProjectile.getDeltaMovement();

        double gravity;
        UUID shooter;
        boolean explosive;

        if (liveProjectile instanceof FastThrowableProjectile shell) {

            gravity = shell.getGravityValue();
            Entity owner = shell.getOwner();
            shooter = owner != null ? owner.getUUID() : null;
            explosive = true;
        } else {
            ProjectileEntity bullet = (ProjectileEntity) liveProjectile;
            gravity = BULLET_GRAVITY_FALLBACK;
            Entity owner = bullet.getShooter();
            shooter = owner != null ? owner.getUUID() : null;
            explosive = false;
        }

        CompoundTag entitySave = new CompoundTag();
        liveProjectile.saveWithoutId(entitySave);

        CompoundTag typeToken = new CompoundTag();
        typeToken.putString(KEY_TYPE, typeId.toString());
        typeToken.put(KEY_SAVE, entitySave);
        typeToken.putBoolean(KEY_EXPLOSIVE, explosive);

        return new VirtualProjectile(
                liveProjectile.getUUID(),
                pos,
                vel,
                gravity,
                DRAG_DEFAULT,
                currentTick,
                ID,
                typeToken,
                shooter);
    }

    @Override
    public Entity spawnLive(ServerLevel level, VirtualProjectile v) {
        EntityType<?> type = resolveType(v);
        if (type == null) {
            return null;
        }

        Entity entity = type.create(level);
        if (entity == null) {
            return null;
        }

        CompoundTag save = v.typeToken.getCompound(KEY_SAVE);
        if (!save.isEmpty()) {
            try {
                entity.load(save);
            } catch (Exception e) {

                WFCore.LOGGER.warn("SBW ballistics: failed to load saved state for {}, spawning bare", v.id, e);
            }
        }

        entity.setUUID(v.id);
        entity.setPos(v.pos.x, v.pos.y, v.pos.z);
        entity.setDeltaMovement(v.vel);

        if (v.shooter != null) {
            Entity owner = level.getEntity(v.shooter);
            if (owner != null) {
                if (entity instanceof FastThrowableProjectile shell) {
                    shell.setOwner(owner);
                } else if (entity instanceof ProjectileEntity bullet) {

                    bullet.shooter(owner);
                }
            }
        }

        if (!level.addFreshEntity(entity)) {
            return null;
        }

        if (entity instanceof FastThrowableProjectile shell) {
            shell.syncMotion();
        } else if (entity instanceof ProjectileEntity bullet) {
            bullet.syncMotion();
        }

        return entity;
    }

    @Override
    public DeferredImpact resolveImpact(ServerLevel level, VirtualProjectile v, BlockPos hitPos,
                                        Direction hitFace, BlockState hitState) {
        if (!v.typeToken.getBoolean(KEY_EXPLOSIVE)) {
            return null;
        }

        final Vec3 impact = v.impactPos != null
                ? v.impactPos
                : new Vec3(hitPos.getX() + 0.5D, hitPos.getY() + 0.5D, hitPos.getZ() + 0.5D);

        return serverLevel -> {
            Entity entity = spawnLive(serverLevel, v);
            if (!(entity instanceof FastThrowableProjectile shell)) {

                if (entity != null) {
                    entity.discard();
                }
                return;
            }

            shell.causeExplode(impact);
        };
    }

    @Override
    public int maxAgeTicks(VirtualProjectile v) {

        return -1;
    }

    private EntityType<?> resolveType(VirtualProjectile v) {
        ResourceLocation typeId = ResourceLocation.tryParse(v.typeToken.getString(KEY_TYPE));
        if (typeId == null) {
            return null;
        }
        return ForgeRegistries.ENTITY_TYPES.getValue(typeId);
    }
}
