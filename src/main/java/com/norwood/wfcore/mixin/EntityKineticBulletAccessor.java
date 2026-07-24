package com.norwood.wfcore.mixin;

import net.minecraft.world.phys.Vec3;

import com.tacz.guns.entity.EntityKineticBullet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


@Mixin(value = EntityKineticBullet.class, remap = false)
public interface EntityKineticBulletAccessor {

    @Accessor("startPos")
    Vec3 wfcore$getStartPos();

    @Accessor("startPos")
    void wfcore$setStartPos(Vec3 startPos);
}
