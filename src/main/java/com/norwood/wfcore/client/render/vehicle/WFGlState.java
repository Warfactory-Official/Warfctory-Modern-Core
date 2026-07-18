package com.norwood.wfcore.client.render.vehicle;

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
 * Shared GL helpers for the retained-vehicle draws. A raw {@code VertexBuffer.drawWithShader} bypasses vanilla's
 * lightmap binding and leaves the VAO/immediate-buffer cache dirty (the same pitfalls
 * {@code GltfMachineRenderer} handles), so the vehicle draws bind a 1x1 world-light texture and reset the raw
 * bindings afterwards.
 */
public final class WFGlState {

    private WFGlState() {}

    private static DynamicTexture worldLightTexture;

    /**
     * Returns the GL id of a reused 1x1 texture holding the vanilla lightmap colour for {@code packedLight}
     * (the authoritative value the entity render dispatcher already computed for the vehicle). The baked meshes
     * carry {@code FULL_BRIGHT} light UVs, so binding this to sampler unit 2 makes the whole mesh take that one
     * real light value. Using the passed packed light — instead of re-sampling at the entity's block position —
     * avoids the "black vehicle" bug where a vehicle sunk against the ground sampled an occluded (dark) block.
     */
    public static int packedLightmap(int packedLight) {
        if (worldLightTexture == null) {
            worldLightTexture = new DynamicTexture(new NativeImage(1, 1, false));
        }
        int color = 0xFFFFFFFF;
        NativeImage pixels = ((LightTextureAccessor) Minecraft.getInstance().gameRenderer.lightTexture())
                .wfcore$getLightPixels();
        if (pixels != null) {
            color = pixels.getPixelRGBA(LightTexture.block(packedLight), LightTexture.sky(packedLight));
        }
        worldLightTexture.getPixels().setPixelRGBA(0, 0, color);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldLightTexture.getId());
        worldLightTexture.getPixels().upload(0, 0, 0, false);
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
