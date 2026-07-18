package com.norwood.wfcore.client.render.vehicle;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import org.joml.Matrix4f;

/**
 * Draws a batch of baked bone {@link VertexBuffer}s through the vanilla entity-cutout render state with a raw
 * {@code drawWithShader}. Using {@link RenderType#entityCutoutNoCull} + {@code setupRenderState()} once for the
 * whole vehicle gives the correct shader, blend/cull/depth state and the overlay (Sampler1) binding for free;
 * only the lightmap (Sampler2) is overridden with a 1x1 world-light texture (from the entity's packed light),
 * because the baked meshes carry {@code FULL_BRIGHT} light UVs.
 * <p>
 * Each bone's model-view matrix is composed like {@code GltfMachineRenderer}:
 * {@code RenderSystem.getModelViewMatrix() * bonePose}, where {@code bonePose} is the live transform GeckoLib
 * applied for that bone. Geometry is opaque, so drawing immediately is fine — the depth test orders it against
 * the batched entities flushed later in the frame.
 */
public final class BakedMeshRenderer {

    private BakedMeshRenderer() {}

    public static void drawBatch(List<VertexBuffer> buffers, List<Matrix4f> bonePoses, ResourceLocation texture,
                                 Level level, int packedLight) {
        RenderType renderType = RenderType.entityCutoutNoCull(texture);
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        renderType.setupRenderState();
        // Override the lightmap (which the render state binds expecting per-vertex light) with a 1x1 texture
        // holding the entity's real world light; the baked FULL_BRIGHT UVs then sample that single value.
        RenderSystem.setShaderTexture(2, WFGlState.packedLightmap(packedLight));
        ShaderInstance shader = RenderSystem.getShader();
        try {
            for (int i = 0; i < buffers.size(); i++) {
                Matrix4f modelView = new Matrix4f(view).mul(bonePoses.get(i));
                VertexBuffer vbo = buffers.get(i);
                vbo.bind();
                vbo.drawWithShader(modelView, projection, shader);
            }
            VertexBuffer.unbind();
        } finally {
            renderType.clearRenderState();
            WFGlState.finishRawDraw();
        }
    }
}
