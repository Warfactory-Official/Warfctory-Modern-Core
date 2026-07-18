package com.norwood.wfcore.client.render.kmodo;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import com.norwood.wfcore.mixin.LightTextureAccessor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * Kmodo Accelerator — shared GL helpers for the retained vehicle draws. A raw {@code VertexBuffer.drawWithShader}
 * bypasses vanilla's lightmap binding and leaves the VAO/immediate-buffer cache dirty (the same pitfalls
 * {@code GltfMachineRenderer} handles), so the draws bind a world-light texture and reset the raw bindings
 * afterwards.
 */
public final class KmodoLight {

    private KmodoLight() {}

    /**
     * Must be 16x16: the entity vertex shader reads the lightmap with {@code texelFetch(Sampler2, UV2 / 16, 0)},
     * and the baked meshes carry {@code UV2 = FULL_BRIGHT}, so the fetch lands at texel (15,15). A smaller
     * texture is out of bounds for that fetch and {@code texelFetch} returns black (the pure-black bug).
     */
    private static final int LIGHTMAP_SIZE = 16;

    private static DynamicTexture worldLightTexture;

    /**
     * Returns the GL id of a reused 16x16 texture filled uniformly with the vanilla lightmap colour for
     * {@code packedLight} (the authoritative value the entity render dispatcher already computed for the
     * vehicle). The baked meshes carry {@code FULL_BRIGHT} light UVs, so every {@code texelFetch} into this
     * texture returns that one real world-light value — bright at noon, dim at night.
     */
    public static int worldLightLightmap(int packedLight) {
        if (worldLightTexture == null) {
            worldLightTexture = new DynamicTexture(new NativeImage(LIGHTMAP_SIZE, LIGHTMAP_SIZE, false));
        }
        int color = 0xFFFFFFFF;
        NativeImage pixels = ((LightTextureAccessor) Minecraft.getInstance().gameRenderer.lightTexture())
                .wfcore$getLightPixels();
        if (pixels != null) {
            color = pixels.getPixelRGBA(LightTexture.block(packedLight), LightTexture.sky(packedLight));
        }
        NativeImage image = worldLightTexture.getPixels();
        for (int y = 0; y < LIGHTMAP_SIZE; y++) {
            for (int x = 0; x < LIGHTMAP_SIZE; x++) {
                image.setPixelRGBA(x, y, color);
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldLightTexture.getId());
        image.upload(0, 0, 0, false);
        return worldLightTexture.getId();
    }

    /**
     * Resets the raw GL bindings a manual {@code VertexBuffer} draw leaves dirty so the vanilla batched draws
     * later in the frame are not corrupted. {@link BufferUploader#invalidate()} is the critical call: binding
     * VAO 0 unbinds whatever immediate-draw VAO {@code BufferUploader} still cached, and without invalidating
     * that cache the next matching {@code endBatch()} runs {@code glDrawElements} with VAO 0 bound
     * (GL_INVALID_OPERATION in a core profile) — the exact bug the GLTF renderer guards against.
     */
    public static void finishRawDraw() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        BufferUploader.invalidate();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }
}
