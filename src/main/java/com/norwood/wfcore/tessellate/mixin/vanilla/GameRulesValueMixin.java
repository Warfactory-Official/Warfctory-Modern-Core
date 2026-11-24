package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rule callbacks reach server-wide state (spawn radius, mob spawning, debug info, player broadcasts): server thread only. */
@Mixin(GameRules.Value.class)
public abstract class GameRulesValueMixin {

    @Shadow
    protected abstract void onChanged(@Nullable MinecraftServer server);

    @Inject(method = "onChanged", at = @At("HEAD"), cancellable = true)
    private void wfcore$callbackOnMain(@Nullable MinecraftServer server, CallbackInfo ci) {
        if (server != null && !server.isSameThread()) {
            RegionThreads.executeOnMain("gamerule callback", () -> onChanged(server));
            ci.cancel();
        }
    }
}
