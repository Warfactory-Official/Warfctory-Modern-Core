package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forced-chunk set + synchronous chunk load + START ticket + spawn broadcast: server thread only. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Shadow
    public abstract boolean setChunkForced(int chunkX, int chunkZ, boolean add);

    @Shadow
    public abstract void setDefaultSpawnPos(BlockPos pos, float angle);

    /** Off-thread result is a guess (true): the real change flag is known only when the op replays. */
    @Inject(method = "setChunkForced", at = @At("HEAD"), cancellable = true)
    private void wfcore$forcedOnMain(int chunkX, int chunkZ, boolean add, CallbackInfoReturnable<Boolean> cir) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!self.getServer().isSameThread()) {
            RegionThreads.executeOnMain("setChunkForced", () -> setChunkForced(chunkX, chunkZ, add));
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setDefaultSpawnPos", at = @At("HEAD"), cancellable = true)
    private void wfcore$spawnOnMain(BlockPos pos, float angle, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!self.getServer().isSameThread()) {
            RegionThreads.executeOnMain("setDefaultSpawnPos", () -> setDefaultSpawnPos(pos, angle));
            ci.cancel();
        }
    }
}
