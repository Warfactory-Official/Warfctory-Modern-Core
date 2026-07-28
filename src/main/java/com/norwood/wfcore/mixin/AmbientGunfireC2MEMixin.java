package com.norwood.wfcore.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// AmbientGunfireManager constructs PlayerTimer with level.getRandom(), which C2ME wraps as a
// thread-ownership-checked CheckedThreadLocalRandom. That check trips on the server tick even
// though both owner and current thread are the server thread (C2ME internal state mismatch).
// Give each PlayerTimer its own independent RandomSource so it never touches the world random.
@SuppressWarnings("UnresolvedMixinReference")
@Mixin(targets = "com.vinlanx.gunfireoverhaul.AmbientGunfireManager", remap = false)
public class AmbientGunfireC2MEMixin {

    @Redirect(
            method = "lambda$onServerTick$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getRandom()Lnet/minecraft/util/RandomSource;"),
            require = 0
    )
    private static RandomSource wfcore$safeRandom(Level level) {
        return RandomSource.create();
    }
}
