package com.norwood.wfcore.client.render;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.data.FoundryMolds;
import com.norwood.wfcore.common.machine.FoundryCastingBlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws the live contents of a foundry casting block (ported from HBM's {@code FoundryCastingRenderer}): the
 * installed mold and the finished casting lying flat inside, and the molten-metal surface as a fullbright
 * quad — HBM's grayscale lava texture vertex-tinted with the GT material's colour — rising with the fill
 * level. The basin/caster body itself renders from the chunk mesh (OBJ / elements model), so this only adds
 * the contents. Poses and heights match HBM's renderer.
 */
public class FoundryCastingRenderer implements BlockEntityRenderer<FoundryCastingBlockEntity> {

    private static final ResourceLocation MOLTEN_TEXTURE = WFCore.id("textures/models/lava_gray.png");

    /** Height the installed mold lies at (HBM: {@code moldHeight()} = 0.13 for both blocks). */
    private static final float MOLD_HEIGHT = 0.13f;
    /** Height the finished casting lies at (HBM: {@code outHeight()} — basin 0.875, caster 0.25). */
    private static final float OUT_HEIGHT_BASIN = 0.875f;
    private static final float OUT_HEIGHT_CASTER = 0.25f;
    /** Melt surface: base 0.125 + fill fraction * span (HBM: basin 0.75, caster 0.25). */
    private static final float MELT_BASE = 0.125f;
    private static final float MELT_SPAN_BASIN = 0.75f;
    private static final float MELT_SPAN_CASTER = 0.25f;
    /** The molten quad spans the vessel interior (HBM: 0.125 .. 0.875). */
    private static final float MELT_MIN = 0.125f;
    private static final float MELT_MAX = 0.875f;

    public FoundryCastingRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FoundryCastingBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        boolean basin = be.getMoldSize() == FoundryMolds.SIZE_BASIN;

        ItemStack mold = be.getMoldStack();
        if (!mold.isEmpty()) {
            drawFlatItem(be, mold, MOLD_HEIGHT, poseStack, buffers, packedLight);
        }
        ItemStack output = be.getOutputStack();
        if (!output.isEmpty()) {
            drawFlatItem(be, output, basin ? OUT_HEIGHT_BASIN : OUT_HEIGHT_CASTER,
                    poseStack, buffers, packedLight);
        }

        FluidStack melt = be.getStored();
        int capacity = be.getCapacity();
        if (!melt.isEmpty() && capacity > 0) {
            float fill = Math.min(1f, melt.getAmount() / (float) capacity);
            float y = MELT_BASE + fill * (basin ? MELT_SPAN_BASIN : MELT_SPAN_CASTER);
            drawMoltenSurface(melt, y, poseStack, buffers, packedOverlay);
        }
    }

    /**
     * An item lying flat in the vessel, posed exactly as HBM does it: centred, flat items turned around and
     * half-scaled first, block items dropped so they sit in the mold, then everything scaled 24/16 and tipped
     * onto the XZ plane.
     */
    private static void drawFlatItem(FoundryCastingBlockEntity be, ItemStack stack, float height,
                                     PoseStack poseStack, MultiBufferSource buffers, int light) {
        boolean is3d = stack.getItem() instanceof BlockItem;
        poseStack.pushPose();
        poseStack.translate(0.5f, height, 0.5f);
        if (!is3d) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-180f));
            poseStack.scale(0.5f, 0.5f, 0.5f);
        } else {
            poseStack.translate(0f, -0.352f, 0f);
        }
        float scale = 24f / 16f;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(90f));

        ItemStack one = stack.copyWithCount(1);
        Minecraft.getInstance().getItemRenderer().renderStatic(one, ItemDisplayContext.FIXED,
                light, OverlayTexture.NO_OVERLAY, poseStack, buffers, be.getLevel(), 0);
        poseStack.popPose();
    }

    /**
     * The molten pool: one up-facing quad across the vessel interior at the fill height, tinted by the melt
     * material's colour and drawn fullbright (molten metal glows).
     */
    private static void drawMoltenSurface(FluidStack melt, float y, PoseStack poseStack,
                                          MultiBufferSource buffers, int overlay) {
        int rgb = 0xFFFFFF;
        Material material = ChemicalHelper.getMaterial(melt.getFluid());
        if (material != null) {
            rgb = material.getMaterialRGB();
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(MOLTEN_TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        int light = LightTexture.FULL_BRIGHT;

        vertex(vc, pose, normal, light, overlay, r, g, b, MELT_MIN, y, MELT_MIN, 0, 0);
        vertex(vc, pose, normal, light, overlay, r, g, b, MELT_MIN, y, MELT_MAX, 0, 1);
        vertex(vc, pose, normal, light, overlay, r, g, b, MELT_MAX, y, MELT_MAX, 1, 1);
        vertex(vc, pose, normal, light, overlay, r, g, b, MELT_MAX, y, MELT_MIN, 1, 0);
    }

    private static void vertex(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light, int overlay,
                               int r, int g, int b, float x, float y, float z, float u, float v) {
        vc.vertex(pose, x, y, z)
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normal, 0, 1, 0)
                .endVertex();
    }
}
