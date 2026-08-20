package com.norwood.wfcore.mixin;

import net.minecraft.world.phys.Vec3;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.LinkedList;


@Mixin(value = EntityKineticBullet.class, remap = false)
public interface EntityKineticBulletAccessor {

    @Accessor("startPos")
    Vec3 wfcore$getStartPos();

    @Accessor("startPos")
    void wfcore$setStartPos(Vec3 startPos);

    // getDamage(): distance -> damage falloff table, plus the scalar multipliers it folds in.
    @Accessor("damageAmount")
    LinkedList<ExtraDamage.DistanceDamagePair> wfcore$getDamageAmount();

    @Accessor("damageAmount")
    void wfcore$setDamageAmount(LinkedList<ExtraDamage.DistanceDamagePair> damageAmount);

    @Accessor("distanceAmount")
    float wfcore$getDistanceAmount();

    @Accessor("distanceAmount")
    void wfcore$setDistanceAmount(float distanceAmount);

    @Accessor("damageModifier")
    float wfcore$getDamageModifier();

    @Accessor("damageModifier")
    void wfcore$setDamageModifier(float damageModifier);

    @Accessor("shotDamageMultiplier")
    float wfcore$getShotDamageMultiplier();

    @Accessor("shotDamageMultiplier")
    void wfcore$setShotDamageMultiplier(float shotDamageMultiplier);

    // Hit application: armour penetration, headshot bonus, knockback, ignite duration.
    @Accessor("armorIgnore")
    float wfcore$getArmorIgnore();

    @Accessor("armorIgnore")
    void wfcore$setArmorIgnore(float armorIgnore);

    @Accessor("headShot")
    float wfcore$getHeadShot();

    @Accessor("headShot")
    void wfcore$setHeadShot(float headShot);

    @Accessor("knockback")
    float wfcore$getKnockback();

    @Accessor("knockback")
    void wfcore$setKnockback(float knockback);

    @Accessor("igniteEntityTime")
    int wfcore$getIgniteEntityTime();

    @Accessor("igniteEntityTime")
    void wfcore$setIgniteEntityTime(int igniteEntityTime);

    // Explosion behaviour flags (explosion/radius/damage are already in writeSpawnData; these are not).
    @Accessor("explosionKnockback")
    boolean wfcore$getExplosionKnockback();

    @Accessor("explosionKnockback")
    void wfcore$setExplosionKnockback(boolean explosionKnockback);

    @Accessor("explosionDestroyBlock")
    boolean wfcore$getExplosionDestroyBlock();

    @Accessor("explosionDestroyBlock")
    void wfcore$setExplosionDestroyBlock(boolean explosionDestroyBlock);

    @Accessor("explosionDelayCount")
    int wfcore$getExplosionDelayCount();

    @Accessor("explosionDelayCount")
    void wfcore$setExplosionDelayCount(int explosionDelayCount);
}
