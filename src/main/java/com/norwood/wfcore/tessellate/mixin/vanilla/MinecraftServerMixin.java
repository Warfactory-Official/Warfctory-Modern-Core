package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Difficulty: WorldData + per-level spawn flags + player broadcast, server thread only. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "setDifficulty", at = @At("HEAD"), cancellable = true)
    private void wfcore$difficultyOnMain(Difficulty difficulty, boolean forced, CallbackInfo ci) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (!self.isSameThread()) {
            RegionThreads.executeOnMain("setDifficulty", () -> self.setDifficulty(difficulty, forced));
            ci.cancel();
        }
    }

    @Inject(method = "setDifficultyLocked", at = @At("HEAD"), cancellable = true)
    private void wfcore$lockOnMain(boolean locked, CallbackInfo ci) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (!self.isSameThread()) {
            RegionThreads.executeOnMain("setDifficultyLocked", () -> self.setDifficultyLocked(locked));
            ci.cancel();
        }
    }
}
