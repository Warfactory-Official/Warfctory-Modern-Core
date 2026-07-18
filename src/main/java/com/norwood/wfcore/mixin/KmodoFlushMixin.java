package com.norwood.wfcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.client.render.kmodo.KmodoAccumulator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kmodo Accelerator — batches the retained bone draws for a vehicle. Targets the single abstract base
 * {@link VehicleRenderer} that every Superb Warfare vehicle renderer (and any addon that reuses it) extends, so
 * it applies to all vehicles with no per-vehicle wiring.
 * <p>
 * During {@code render}, {@code KmodoCubeRedirectMixin} records each retained bone into {@link KmodoAccumulator};
 * this mixin clears the accumulator at the start and flushes it (one render-state pass, using the entity's own
 * packed light) at the end. {@code render} is SBW's own covariant override (the vanilla-mapped method is the
 * synthetic {@code render(Entity,...)} bridge), so {@code remap} is left off. {@code require = 0} keeps the game
 * running if a future SBW build changes the signature.
 */
@Mixin(value = VehicleRenderer.class, remap = false)
public abstract class KmodoFlushMixin {

    @Inject(
            method = "render(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            require = 0
    )
    private void kmodo$beginRetain(VehicleEntity entity, float yaw, float partialTick, PoseStack pose,
                                   MultiBufferSource buffers, int light, CallbackInfo ci) {
        KmodoAccumulator.clear();
    }

    @Inject(
            method = "render(Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"),
            require = 0
    )
    private void kmodo$flushRetain(VehicleEntity entity, float yaw, float partialTick, PoseStack pose,
                                   MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (KmodoAccumulator.isEmpty()) {
            return;
        }
        ResourceLocation texture;
        try {
            texture = ((VehicleRenderer) (Object) this).getTextureLocation(entity);
        } catch (Throwable t) {
            KmodoAccumulator.clear();
            return;
        }
        KmodoAccumulator.flush(texture, entity.level(), light);
    }
}
