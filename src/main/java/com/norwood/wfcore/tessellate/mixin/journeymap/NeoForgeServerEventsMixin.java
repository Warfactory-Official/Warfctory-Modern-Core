package com.norwood.wfcore.tessellate.mixin.journeymap;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player death off the server thread (region worker) => same handler re-run on the server thread next tick. */
@Mixin(targets = "journeymap.common.event.NeoForgeServerEvents", remap = false)
public abstract class NeoForgeServerEventsMixin {

    @Shadow
    public abstract void onLivingDeath(LivingDeathEvent event);

    @Inject(method = "onLivingDeath", at = @At("HEAD"), cancellable = true)
    private void wfcore$deathOnMain(LivingDeathEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof ServerPlayer player && !player.server.isSameThread()) {
            player.server.execute(() -> onLivingDeath(event));
            ci.cancel();
        }
    }
}
