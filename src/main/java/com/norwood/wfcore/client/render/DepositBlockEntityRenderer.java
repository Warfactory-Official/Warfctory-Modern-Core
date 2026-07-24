package com.norwood.wfcore.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.machine.DepositBlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;


public class DepositBlockEntityRenderer implements BlockEntityRenderer<DepositBlockEntity> {

    private static final ResourceLocation FALLBACK = new ResourceLocation("minecraft", "textures/block/bedrock.png");
    private static final ResourceLocation BEDROCK = new ResourceLocation("minecraft", "textures/block/bedrock.png");
    /** Overlay cube is scaled up a hair so its faces sit just outside the base cube (no z-fighting). */
    private static final float OVERLAY_SCALE = 1.003f;

    public DepositBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(DepositBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        DepositType type = be.getDepositType();
        ResourceLocation overlay = type == null ? null : type.overlayTexture();

        int[] light = faceLights(be, packedLight);

        if (overlay == null) {
            drawCube(buffers.getBuffer(RenderType.entityCutoutNoCull(resolveTexture(type))), poseStack,
                    light, packedOverlay, 255, 255, 255);
            return;
        }

        drawCube(buffers.getBuffer(RenderType.entityCutoutNoCull(BEDROCK)), poseStack,
                light, packedOverlay, 255, 255, 255);

        int rgb = overlayTint(type);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(OVERLAY_SCALE, OVERLAY_SCALE, OVERLAY_SCALE);
        poseStack.translate(-0.5, -0.5, -0.5);

        drawCube(buffers.getBuffer(RenderType.entityTranslucent(expand(overlay))), poseStack,
                light, packedOverlay, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        poseStack.popPose();
    }

    /** Overlay tint: the explicit {@code overlayColor}, else the prospector material's colour, else white. */
    private static int overlayTint(DepositType type) {
        if (type.overlayColor() >= 0) {
            return type.overlayColor() & 0xFFFFFF;
        }
        String mat = type.prospectorMaterial();
        if (mat != null) {
            Material material = GTMaterials.get(mat);
            if (material != null && !material.isNull()) {
                return material.getMaterialRGB() & 0xFFFFFF;
            }
        }
        return 0xFFFFFF;
    }

    private static ResourceLocation resolveTexture(DepositType type) {
        return type == null ? FALLBACK : expand(type.texture());
    }

    /** {@code ns:block/foo} -> {@code ns:textures/block/foo.png}. */
    private static ResourceLocation expand(ResourceLocation tex) {
        return new ResourceLocation(tex.getNamespace(), "textures/" + tex.getPath() + ".png");
    }

    private static void drawCube(VertexConsumer vc, PoseStack poseStack, int[] light, int overlay,
                                 int r, int g, int b) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        // north (-z), south (+z), west (-x), east (+x), down (-y), up (+y) — light[] follows the same order.
        face(vc, pose, normal, light[0], overlay, r, g, b, 0, 0, -1,
                1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 0);
        face(vc, pose, normal, light[1], overlay, r, g, b, 0, 0, 1,
                0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1);
        face(vc, pose, normal, light[2], overlay, r, g, b, -1, 0, 0,
                0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1);
        face(vc, pose, normal, light[3], overlay, r, g, b, 1, 0, 0,
                1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0);
        face(vc, pose, normal, light[4], overlay, r, g, b, 0, -1, 0,
                0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0);
        face(vc, pose, normal, light[5], overlay, r, g, b, 0, 1, 0,
                0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1);
    }

    private static int[] faceLights(DepositBlockEntity be, int fallback) {
        var level = be.getLevel();
        if (level == null) {
            return new int[] {fallback, fallback, fallback, fallback, fallback, fallback};
        }
        BlockPos pos = be.getBlockPos();
        return new int[] {
                LevelRenderer.getLightColor(level, pos.north()),
                LevelRenderer.getLightColor(level, pos.south()),
                LevelRenderer.getLightColor(level, pos.west()),
                LevelRenderer.getLightColor(level, pos.east()),
                LevelRenderer.getLightColor(level, pos.below()),
                LevelRenderer.getLightColor(level, pos.above()),
        };
    }

    private static void face(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light, int overlay,
                             int r, int g, int b, int nx, int ny, int nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(vc, pose, normal, light, overlay, r, g, b, nx, ny, nz, x1, y1, z1, 0, 0);
        vertex(vc, pose, normal, light, overlay, r, g, b, nx, ny, nz, x2, y2, z2, 0, 1);
        vertex(vc, pose, normal, light, overlay, r, g, b, nx, ny, nz, x3, y3, z3, 1, 1);
        vertex(vc, pose, normal, light, overlay, r, g, b, nx, ny, nz, x4, y4, z4, 1, 0);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light, int overlay,
                               int r, int g, int b, int nx, int ny, int nz, float x, float y, float z,
                               float u, float v) {
        vc.vertex(pose, x, y, z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }
}
