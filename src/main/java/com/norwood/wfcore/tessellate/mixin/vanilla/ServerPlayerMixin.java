package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * {@code ServerPlayer} overrides both teleport entry points that Tessellate's {@code EntityMixin} defers, so a worker
 * reaching them ({@code /tp @p}, command block, KubeJS) runs PlayerList/connection/level hand-over off the server thread.
 * Off-thread result is a guess (true / null).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void wfcore$teleportOnMain(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relative,
                                       float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!self.server.isSameThread()) {
            RegionThreads.executeOnMain("ServerPlayer.teleportTo", () -> self.teleportTo(level, x, y, z, relative, yRot, xRot));
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void wfcore$teleportSimpleOnMain(ServerLevel level, double x, double y, double z, float yRot, float xRot,
                                             CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!self.server.isSameThread()) {
            RegionThreads.executeOnMain("ServerPlayer.teleportTo", () -> self.teleportTo(level, x, y, z, yRot, xRot));
            ci.cancel();
        }
    }

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void wfcore$dimensionOnMain(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!self.server.isSameThread()) {
            RegionThreads.executeOnMain("ServerPlayer.changeDimension", () -> self.changeDimension(transition));
            cir.setReturnValue(null);
        }
    }
}
