package com.norwood.wfcore.client.render.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.norwood.wfcore.common.item.PackagedVehicleItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;


public class PackagedVehicleItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ModelResourceLocation CRATE =
            new ModelResourceLocation("minecraft", "barrel", "inventory");

    private static PackagedVehicleItemRenderer instance;

    public PackagedVehicleItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    public static PackagedVehicleItemRenderer instance() {
        if (instance == null) {
            instance = new PackagedVehicleItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        ResourceLocation entityId = stack.getItem() instanceof PackagedVehicleItem
                ? PackagedVehicleItem.getEntityId(stack) : null;

        VehicleMeshCache.Baked baked = entityId == null ? null : VehicleMeshCache.get(entityId);
        if (baked != null && !baked.byType.isEmpty()) {
            renderMesh(baked, ctx, pose, buffer, light);
        } else {
            renderCrate(stack, pose, buffer, light, overlay);
        }
    }

    private void renderMesh(VehicleMeshCache.Baked baked, ItemDisplayContext ctx, PoseStack pose,
                            MultiBufferSource buffer, int light) {
        pose.pushPose();

        // Spin in place (GUI only), then fit: centre the model in the slot and scale to unit size.
        if (ctx == ItemDisplayContext.GUI) {
            float spin = (float) ((System.currentTimeMillis() / 25L) % 360L);
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.translate(-0.5, -0.5, -0.5);
        }
        float scale = (float) (1.0 / baked.length);
        Vec3 c = baked.center;
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(scale, scale, scale);
        pose.translate(-c.x, -c.y, -c.z);

        Matrix4f mat = pose.last().pose();
        Matrix3f nor = pose.last().normal();
        for (Map.Entry<RenderType, float[]> e : baked.byType.entrySet()) {
            VertexConsumer vc = buffer.getBuffer(e.getKey());
            float[] v = e.getValue();
            for (int i = 0; i + VehicleMeshCache.STRIDE <= v.length; i += VehicleMeshCache.STRIDE) {
                vc.vertex(mat, v[i], v[i + 1], v[i + 2])
                        .color((int) v[i + 3], (int) v[i + 4], (int) v[i + 5], (int) v[i + 6])
                        .uv(v[i + 7], v[i + 8])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(nor, v[i + 9], v[i + 10], v[i + 11])
                        .endVertex();
            }
        }
        pose.popPose();
    }

    private void renderCrate(ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        BakedModel crate = Minecraft.getInstance().getModelManager().getModel(CRATE);
        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        Minecraft.getInstance().getItemRenderer().renderModelLists(crate, stack, light, overlay, pose, vc);
    }
}
