package com.norwood.wfcore.client.render.kmodo;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import org.joml.Matrix4f;

/**
 * Kmodo Accelerator — draws a batch of baked bone {@link VertexBuffer}s through the vanilla entity render state
 * with a raw {@code drawWithShader}. Using {@link RenderType#entityCutoutNoCull} + {@code setupRenderState()}
 * once for the whole vehicle gives the correct shader, blend/cull/depth state and the overlay (Sampler1)
 * binding for free.
 * <p>
 * The one thing we override is the lightmap (Sampler2): the entity vertex shader reads it with
 * {@code texelFetch(Sampler2, UV2 / 16, 0)}, and our baked meshes carry {@code UV2 = FULL_BRIGHT}, so the fetch
 * lands at texel (15,15). That requires a full 16x16 lightmap — {@link KmodoLight#worldLightLightmap} supplies a
 * 16x16 texture filled with the entity's real world light (a 1x1 texture would be out of bounds for the fetch
 * and return black, which was the pure-black-vehicle bug).
 * <p>
 * Each bone's model-view matrix is composed like {@code GltfMachineRenderer}:
 * {@code RenderSystem.getModelViewMatrix() * bonePose}, where {@code bonePose} is the live transform GeckoLib
 * applied for that bone. Geometry is opaque with depth write, so drawing immediately is fine — the depth test
 * orders it against the batched entities flushed later in the frame.
 */
public final class KmodoRenderer {

    private KmodoRenderer() {}

    public static void drawBatch(List<VertexBuffer> buffers, List<Matrix4f> bonePoses, ResourceLocation texture,
                                 Level level, int packedLight) {
        RenderType renderType = RenderType.entityCutoutNoCull(texture);
        Matrix4f view = RenderSystem.getModelViewMatrix();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        renderType.setupRenderState();
        RenderSystem.setShaderTexture(2, KmodoLight.worldLightLightmap(packedLight));
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            KmodoLight.finishRawDraw();
        }
    }
}
