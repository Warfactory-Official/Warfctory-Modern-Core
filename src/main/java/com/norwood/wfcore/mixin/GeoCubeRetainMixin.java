package com.norwood.wfcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.entity.Entity;

import com.norwood.wfcore.client.render.vehicle.VehicleBoneRetain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Strategy 2 hook. Redirects the {@code renderCubesOfBone} call inside GeckoLib's own
 * {@code GeoEntityRenderer.renderRecursively}, so it covers every vehicle (Superb Warfare or addon) whose
 * renderer extends {@code GeoEntityRenderer}. For an allow-listed, active vehicle,
 * {@link VehicleBoneRetain#drawBone} draws the bone's cached retained buffer with the live pose GeckoLib
 * already applied; for anything else the original tessellating call runs unchanged.
 * <p>
 * {@code remap = false}: the target method/name are GeckoLib's own (stable across dev/prod) and the descriptor
 * references only official Minecraft class names (also stable). {@code require = 0} makes the redirect optional
 * — if a GeckoLib version doesn't match, Strategy 2 goes dormant instead of crashing.
 */
@Mixin(value = GeoEntityRenderer.class, remap = false)
public abstract class GeoCubeRetainMixin {

    @Redirect(
            method = "renderRecursively(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIIFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lsoftware/bernie/geckolib/renderer/GeoEntityRenderer;renderCubesOfBone(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/cache/object/GeoBone;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"
            ),
            require = 0
    )
    private void wfcore$retainCubes(GeoEntityRenderer<?> self, PoseStack pose, GeoBone bone, VertexConsumer buffer,
                                    int packedLight, int packedOverlay, float red, float green, float blue,
                                    float alpha,
                                    // captured prefix of the enclosing renderRecursively args:
                                    PoseStack enclosingPose, Entity animatable) {
        if (!VehicleBoneRetain.drawBone(self, animatable, pose, bone)) {
            self.renderCubesOfBone(pose, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}
