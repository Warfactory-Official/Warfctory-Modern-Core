package com.norwood.wfcore.integration.tacz;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.ballistics.BallisticsAdapter;
import com.norwood.wfcore.common.ballistics.DeferredImpact;
import com.norwood.wfcore.common.ballistics.VirtualProjectile;
import com.norwood.wfcore.mixin.EntityKineticBulletAccessor;
import com.tacz.guns.entity.EntityKineticBullet;

import io.netty.buffer.Unpooled;

import java.util.UUID;

public final class TaczBallisticsAdapter implements BallisticsAdapter {

    private static final ResourceLocation ID = WFCore.id("tacz_kinetic_bullet");

    private static final String KEY_SPAWN = "SpawnData";

    private static final String KEY_GRAVITY = "Gravity";
    private static final String KEY_FRICTION = "Friction";
    private static final String KEY_LIFE = "Life";
    // TACZ's firing origin (getDamage falloff). Not in writeSpawnData, so we carry it through the virtual phase.
    private static final String KEY_START_X = "StartX";
    private static final String KEY_START_Y = "StartY";
    private static final String KEY_START_Z = "StartZ";

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean matches(Entity entity) {

        return entity instanceof EntityKineticBullet;
    }

    @Override
    public VirtualProjectile capture(Entity liveProjectile, int currentTick) {
        if (!(liveProjectile instanceof EntityKineticBullet bullet)) {
            return null;
        }

        if (bullet.getOwner() == null) {
            return null;
        }

        Vec3 pos = bullet.position();
        Vec3 vel = bullet.getDeltaMovement();

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        double gravity;
        double friction;
        int life;
        byte[] spawnBytes;
        try {
            bullet.writeSpawnData(buf);

            FriendlyByteBuf reader = new FriendlyByteBuf(buf.copy());
            reader.readFloat();
            reader.readFloat();
            reader.readDouble();
            reader.readDouble();
            reader.readDouble();
            reader.readInt();
            reader.readResourceLocation();
            gravity = reader.readFloat();
            reader.readBoolean();
            reader.readBoolean();
            reader.readBoolean();
            reader.readFloat();
            reader.readFloat();
            life = reader.readInt();
            reader.readFloat();
            friction = reader.readFloat();

            reader.release();

            spawnBytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), spawnBytes);
        } finally {
            buf.release();
        }

        CompoundTag typeToken = new CompoundTag();
        typeToken.putByteArray(KEY_SPAWN, spawnBytes);
        typeToken.putDouble(KEY_GRAVITY, gravity);
        typeToken.putDouble(KEY_FRICTION, friction);
        typeToken.putInt(KEY_LIFE, life);

        // Preserve the firing origin so damage falloff stays correct after re-materialisation (falling back to
        // the current position rather than leaving it null, which would NPE getDamage the moment it re-spawns).
        Vec3 startPos = ((EntityKineticBulletAccessor) (Object) bullet).wfcore$getStartPos();
        if (startPos == null) {
            startPos = pos;
        }
        typeToken.putDouble(KEY_START_X, startPos.x);
        typeToken.putDouble(KEY_START_Y, startPos.y);
        typeToken.putDouble(KEY_START_Z, startPos.z);

        Entity owner = bullet.getOwner();
        UUID shooter = owner != null ? owner.getUUID() : null;

        return new VirtualProjectile(
                bullet.getUUID(),
                pos,
                vel,
                gravity,
                friction,
                currentTick,
                ID,
                typeToken,
                shooter);
    }

    @Override
    public Entity spawnLive(ServerLevel level, VirtualProjectile v) {
        byte[] spawnBytes = v.typeToken.getByteArray(KEY_SPAWN);
        if (spawnBytes.length == 0) {
            return null;
        }

        EntityKineticBullet bullet;
        try {

            bullet = new EntityKineticBullet(EntityKineticBullet.TYPE, level);
            bullet.setUUID(v.id);
            bullet.setPos(v.pos.x, v.pos.y, v.pos.z);

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(spawnBytes));
            try {
                bullet.readSpawnData(buf);
            } finally {
                buf.release();
            }

            bullet.setPos(v.pos.x, v.pos.y, v.pos.z);
            bullet.setDeltaMovement(v.vel);
            bullet.hasImpulse = true;

            // Restore the firing origin (readSpawnData doesn't carry it) before the entity can tick, so
            // getDamage's distanceTo(startPos) never sees null.
            Vec3 startPos = v.typeToken.contains(KEY_START_X)
                    ? new Vec3(v.typeToken.getDouble(KEY_START_X), v.typeToken.getDouble(KEY_START_Y),
                            v.typeToken.getDouble(KEY_START_Z))
                    : v.pos;
            ((EntityKineticBulletAccessor) (Object) bullet).wfcore$setStartPos(startPos);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("Ballistics: failed to re-spawn TACZ bullet {}", v.id, t);
            return null;
        }

        if (!level.addFreshEntity(bullet)) {
            return null;
        }
        return bullet;
    }

    @Override
    public DeferredImpact resolveImpact(ServerLevel level, VirtualProjectile v, BlockPos hitPos,
                                        Direction hitFace, BlockState hitState) {

        return null;
    }

    @Override
    public int maxAgeTicks(VirtualProjectile v) {

        if (v.typeToken.contains(KEY_LIFE)) {
            int life = v.typeToken.getInt(KEY_LIFE);
            return life > 0 ? life : -1;
        }
        return -1;
    }
}
