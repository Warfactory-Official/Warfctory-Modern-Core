package com.norwood.wfcore.integration.tacz;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;

import io.netty.buffer.Unpooled;

import java.util.LinkedList;
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
    // TaCZ Tweaks' per-bullet state (gun stack, pierce counters, shot indices, damage modifiers). Set only by
    // its shoot-constructor injector; bare-constructor re-spawn drops it (null gunStack crashes its ray-trace).
    private static final String KEY_TT = "TaczTweaks";
    // Combat fields TACZ sets in the firing constructor but omits from writeSpawnData (damage falloff table,
    // multipliers, penetration, knockback, ignite, explosion flags). Dropped on re-spawn → 0-damage bullets.
    private static final String KEY_COMBAT = "Combat";
    private static final String KEY_DMG_TABLE = "DmgTable";
    private static final String KEY_DMG_DIST = "d";
    private static final String KEY_DMG_VAL = "g";
    private static final String KEY_DISTANCE_AMOUNT = "DistanceAmount";
    private static final String KEY_DAMAGE_MODIFIER = "DamageModifier";
    private static final String KEY_SHOT_MULT = "ShotDamageMultiplier";
    private static final String KEY_ARMOR_IGNORE = "ArmorIgnore";
    private static final String KEY_HEADSHOT = "HeadShot";
    private static final String KEY_KNOCKBACK = "Knockback";
    private static final String KEY_IGNITE_TIME = "IgniteEntityTime";
    private static final String KEY_EXP_KNOCKBACK = "ExplosionKnockback";
    private static final String KEY_EXP_DESTROY = "ExplosionDestroyBlock";
    private static final String KEY_EXP_DELAY = "ExplosionDelayCount";

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

        // Carry TaCZ Tweaks' per-bullet state across the virtual phase; readSpawnData doesn't restore it.
        if (TaczTweaksCompat.isAvailable()) {
            CompoundTag tt = TaczTweaksCompat.saveState(bullet);
            if (tt != null) {
                typeToken.put(KEY_TT, tt);
            }
        }

        // Carry the combat fields writeSpawnData omits, or the re-spawned bullet deals ~0 damage.
        typeToken.put(KEY_COMBAT, saveCombat((EntityKineticBulletAccessor) (Object) bullet));

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

            // Put TaCZ Tweaks' per-bullet state back before the entity ticks. restoreState handles a missing
            // tag by setting gunStack to EMPTY so its non-null block-interaction read never NPEs (e.g. bullets
            // virtualised before this carry existed).
            if (TaczTweaksCompat.isAvailable()) {
                TaczTweaksCompat.restoreState(bullet,
                        v.typeToken.contains(KEY_TT) ? v.typeToken.getCompound(KEY_TT) : null);
            }

            // Restore combat state (damage falloff table + multipliers) before the entity can hit anything.
            if (v.typeToken.contains(KEY_COMBAT)) {
                restoreCombat((EntityKineticBulletAccessor) (Object) bullet, v.typeToken.getCompound(KEY_COMBAT));
            }
        } catch (Throwable t) {
            WFCore.LOGGER.warn("Ballistics: failed to re-spawn TACZ bullet {}", v.id, t);
            return null;
        }

        if (!level.addFreshEntity(bullet)) {
            return null;
        }
        return bullet;
    }

    /** Snapshot the combat fields TACZ's writeSpawnData drops (see {@link EntityKineticBulletAccessor}). */
    private static CompoundTag saveCombat(EntityKineticBulletAccessor a) {
        CompoundTag combat = new CompoundTag();

        ListTag table = new ListTag();
        LinkedList<ExtraDamage.DistanceDamagePair> pairs = a.wfcore$getDamageAmount();
        if (pairs != null) {
            for (ExtraDamage.DistanceDamagePair pair : pairs) {
                CompoundTag e = new CompoundTag();
                e.putFloat(KEY_DMG_DIST, pair.getDistance());
                e.putFloat(KEY_DMG_VAL, pair.getDamage());
                table.add(e);
            }
        }
        combat.put(KEY_DMG_TABLE, table);

        combat.putFloat(KEY_DISTANCE_AMOUNT, a.wfcore$getDistanceAmount());
        combat.putFloat(KEY_DAMAGE_MODIFIER, a.wfcore$getDamageModifier());
        combat.putFloat(KEY_SHOT_MULT, a.wfcore$getShotDamageMultiplier());
        combat.putFloat(KEY_ARMOR_IGNORE, a.wfcore$getArmorIgnore());
        combat.putFloat(KEY_HEADSHOT, a.wfcore$getHeadShot());
        combat.putFloat(KEY_KNOCKBACK, a.wfcore$getKnockback());
        combat.putInt(KEY_IGNITE_TIME, a.wfcore$getIgniteEntityTime());
        combat.putBoolean(KEY_EXP_KNOCKBACK, a.wfcore$getExplosionKnockback());
        combat.putBoolean(KEY_EXP_DESTROY, a.wfcore$getExplosionDestroyBlock());
        combat.putInt(KEY_EXP_DELAY, a.wfcore$getExplosionDelayCount());
        return combat;
    }

    /** Put the {@link #saveCombat} snapshot back before the re-spawned bullet can compute damage or hit. */
    private static void restoreCombat(EntityKineticBulletAccessor a, CompoundTag combat) {
        LinkedList<ExtraDamage.DistanceDamagePair> pairs = new LinkedList<>();
        ListTag table = combat.getList(KEY_DMG_TABLE, Tag.TAG_COMPOUND);
        for (int i = 0; i < table.size(); i++) {
            CompoundTag e = table.getCompound(i);
            pairs.add(new ExtraDamage.DistanceDamagePair(e.getFloat(KEY_DMG_DIST), e.getFloat(KEY_DMG_VAL)));
        }
        a.wfcore$setDamageAmount(pairs);

        a.wfcore$setDistanceAmount(combat.getFloat(KEY_DISTANCE_AMOUNT));
        a.wfcore$setDamageModifier(combat.getFloat(KEY_DAMAGE_MODIFIER));
        a.wfcore$setShotDamageMultiplier(combat.getFloat(KEY_SHOT_MULT));
        a.wfcore$setArmorIgnore(combat.getFloat(KEY_ARMOR_IGNORE));
        a.wfcore$setHeadShot(combat.getFloat(KEY_HEADSHOT));
        a.wfcore$setKnockback(combat.getFloat(KEY_KNOCKBACK));
        a.wfcore$setIgniteEntityTime(combat.getInt(KEY_IGNITE_TIME));
        a.wfcore$setExplosionKnockback(combat.getBoolean(KEY_EXP_KNOCKBACK));
        a.wfcore$setExplosionDestroyBlock(combat.getBoolean(KEY_EXP_DESTROY));
        a.wfcore$setExplosionDelayCount(combat.getInt(KEY_EXP_DELAY));
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
