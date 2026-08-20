package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.common.ballistics.BallisticsAdapter;
import com.norwood.wfcore.common.ballistics.BallisticsManager;
import com.norwood.wfcore.common.ballistics.BallisticsRegistry;
import com.norwood.wfcore.common.ballistics.VirtualProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileBallisticsMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void wfcore$demoteIfLeavingLoaded(CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        if (!WFCoreConfig.isBallisticsEnabled()) {
            return;
        }

        BallisticsAdapter a = BallisticsRegistry.find(self);
        if (a == null) {
            return;
        }
        ServerLevel sl = (ServerLevel) self.level();
        Vec3 next = self.position().add(self.getDeltaMovement());
        BlockPos nb = BlockPos.containing(next);
        if (sl.isPositionEntityTicking(nb)) {
            return;
        }
        VirtualProjectile v;
        try {

            v = a.capture(self, sl.getServer().getTickCount());
        } catch (Throwable t) {
            WFCore.LOGGER.error("Ballistics: adapter {} failed to capture {}; leaving it live", a.id(), self, t);
            return;
        }
        if (v == null) {
            return;
        }
        BallisticsManager.get(sl).addVirtual(v);
        self.discard();
        ci.cancel();
    }
}
