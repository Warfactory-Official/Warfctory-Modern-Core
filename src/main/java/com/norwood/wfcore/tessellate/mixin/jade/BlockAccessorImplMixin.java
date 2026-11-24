package com.norwood.wfcore.tessellate.mixin.jade;

import com.llamalad7.mixinextras.sugar.Local;
import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "snownee.jade.impl.BlockAccessorImpl", remap = false)
public abstract class BlockAccessorImplMixin {

    @Inject(method = "lambda$handleRequest$0", at = @At(value = "INVOKE",
            target = "Lsnownee/jade/impl/WailaCommonRegistration;instance()Lsnownee/jade/impl/WailaCommonRegistration;"))
    private static void wfcore$leaseRegion(CallbackInfo ci, @Local ServerLevel level, @Local BlockPos pos) {
        RegionThreads.lease(level, ChunkPos.asLong(pos));
    }
}
