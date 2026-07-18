package com.norwood.wfcore.client.render.vehicle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import org.joml.Matrix4f;

/**
 * Draws a baked {@link VertexBuffer} through the vanilla entity-cutout render state with a raw
 * {@code drawWithShader}. Using {@link RenderType#entityCutoutNoCull} + {@code setupRenderState()} gives us the
 * correct shader, blend/cull/depth state and the overlay (Sampler1) binding for free; we only override the
 * lightmap (Sampler2) with a 1x1 world-light texture, because the baked meshes carry {@code FULL_BRIGHT} light
 * UVs and should instead take the real world light at the vehicle's position.
 * <p>
 * The model-view matrix is composed exactly like {@code GltfMachineRenderer}:
 * {@code RenderSystem.getModelViewMatrix() * poseStack}. Geometry is opaque, so drawing immediately (rather than
 * through the buffered {@code MultiBufferSource}) is fine — the depth test orders it correctly against the
 * batched entities flushed later in the frame.
 */
public final class BakedMeshRenderer {

    private BakedMeshRenderer() {}

    public static void draw(VertexBuffer vbo, PoseStack pose, ResourceLocation texture, Level level, BlockPos pos) {
        RenderType renderType = RenderType.entityCutoutNoCull(texture);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        renderType.setupRenderState();
        // Override the lightmap the render state just bound (which expects per-vertex real light) with a 1x1
        // texture holding the world light at this position; the baked FULL_BRIGHT UVs then sample that value.
        RenderSystem.setShaderTexture(2, WFGlState.worldLightLightmap(level, pos));
        try {
            vbo.bind();
            vbo.drawWithShader(modelView, projection, RenderSystem.getShader());
            VertexBuffer.unbind();
        } finally {
            renderType.clearRenderState();
            WFGlState.finishRawDraw();
        }
    }
}
