package com.norwood.wfcore.client.render;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.machine.DepositBlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a deposit block as a full cube textured with its {@link DepositType}'s texture. The texture is bound
 * directly (outside the block atlas) so deposit types — including ones added by KubeJS — can supply arbitrary
 * texture files. The deposit block itself renders nothing in the chunk mesh (ENTITYBLOCK_ANIMATED).
 */
public class DepositBlockEntityRenderer implements BlockEntityRenderer<DepositBlockEntity> {

    private static final ResourceLocation FALLBACK = new ResourceLocation("minecraft", "textures/block/bedrock.png");

    public DepositBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(DepositBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(resolveTexture(be)));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // north (-z), south (+z), west (-x), east (+x), down (-y), up (+y)
        face(vc, pose, normal, packedLight, packedOverlay, 0, 0, -1,
                1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0);
        face(vc, pose, normal, packedLight, packedOverlay, 0, 0, 1,
                0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1);
        face(vc, pose, normal, packedLight, packedOverlay, -1, 0, 0,
                0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1);
        face(vc, pose, normal, packedLight, packedOverlay, 1, 0, 0,
                1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0);
        face(vc, pose, normal, packedLight, packedOverlay, 0, -1, 0,
                0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0);
        face(vc, pose, normal, packedLight, packedOverlay, 0, 1, 0,
                0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1);
    }

    private static ResourceLocation resolveTexture(DepositBlockEntity be) {
        DepositType type = be.getDepositType();
        if (type == null) {
            return FALLBACK;
        }
        ResourceLocation tex = type.texture();
        return new ResourceLocation(tex.getNamespace(), "textures/" + tex.getPath() + ".png");
    }

    private static void face(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light, int overlay,
                             int nx, int ny, int nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(vc, pose, normal, light, overlay, nx, ny, nz, x1, y1, z1, 0, 0);
        vertex(vc, pose, normal, light, overlay, nx, ny, nz, x2, y2, z2, 0, 1);
        vertex(vc, pose, normal, light, overlay, nx, ny, nz, x3, y3, z3, 1, 1);
        vertex(vc, pose, normal, light, overlay, nx, ny, nz, x4, y4, z4, 1, 0);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light, int overlay,
                               int nx, int ny, int nz, float x, float y, float z, float u, float v) {
        vc.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }
}
