package com.norwood.wfcore.common.ballistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public interface BallisticsAdapter {

    ResourceLocation id();

    boolean matches(Entity entity);

    VirtualProjectile capture(Entity liveProjectile, int currentTick);

    Entity spawnLive(ServerLevel level, VirtualProjectile v);

    DeferredImpact resolveImpact(ServerLevel level, VirtualProjectile v, BlockPos hitPos,
                                 Direction hitFace, BlockState hitState);

    default int maxAgeTicks(VirtualProjectile v) {
        return -1;
    }

    default Vec3 advanceVelocity(VirtualProjectile v) {

        double k = 1.0 - v.drag;
        return new Vec3(v.vel.x * k, (v.vel.y - v.gravity) * k, v.vel.z * k);
    }
}
