package com.norwood.wfcore.tessellate.mixin.jade;

import com.llamalad7.mixinextras.sugar.Local;
import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "snownee.jade.impl.EntityAccessorImpl", remap = false)
public abstract class EntityAccessorImplMixin {

    @Inject(method = "lambda$handleRequest$0", at = @At(value = "INVOKE",
            target = "Lsnownee/jade/impl/WailaCommonRegistration;instance()Lsnownee/jade/impl/WailaCommonRegistration;"))
    private static void wfcore$leaseRegion(CallbackInfo ci, @Local ServerPlayer player, @Local Entity entity) {
        RegionThreads.lease(player.serverLevel(), entity.chunkPosition().toLong());
    }
}
