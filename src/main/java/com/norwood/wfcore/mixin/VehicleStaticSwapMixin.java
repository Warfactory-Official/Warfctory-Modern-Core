package com.norwood.wfcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.client.render.vehicle.VehicleStaticSwap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Strategy 1 hook. Targets the single abstract base {@link VehicleRenderer} that every Superb Warfare vehicle
 * renderer (and any addon that reuses it) extends, so the idle static-swap applies to all vehicles with no
 * per-vehicle registration. At the head of {@code render}, if the vehicle is idle we draw a baked rest-pose
 * buffer and cancel; otherwise GeckoLib renders as usual.
 * <p>
 * {@code render} is a vanilla-inherited method, so it is remapped (the default). {@code require = 0} keeps the
 * game running if a future SBW build changes the signature — the optimization simply goes dormant rather than
 * crashing.
 */
@Mixin(value = VehicleRenderer.class, remap = false)
public abstract class VehicleStaticSwapMixin {

    @Inject(
            method = "render(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void wfcore$staticSwap(VehicleEntity entity, float yaw, float partialTick, PoseStack pose,
                                   MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (VehicleStaticSwap.tryRenderIdle((VehicleRenderer) (Object) this, entity, yaw, partialTick, pose,
                buffers, light)) {
            ci.cancel();
        }
    }
}
