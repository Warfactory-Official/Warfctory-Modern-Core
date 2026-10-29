package com.norwood.wfcore.client.title;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.norwood.wfcore.WFCore;

/** Crossfading slideshow with a slow rigid shake, stretched over the window. */
final class TitleBackground {
    private static final ResourceLocation[] IMAGES = {
            WFCore.id("textures/gui/title/trench.png"),
            WFCore.id("textures/gui/title/mountain.png"),
            WFCore.id("textures/gui/title/bunker.png")};
    private static final float HOLD = 6;
    private static final float FADE = 1.5f;
    private static final float SHAKE_AMOUNT = 0.005f;
    private static final float SHAKE_ROTATION = 0.015f;
    private static final float SHAKE_SPEED = 0.4f;
    private static final float ZOOM = 0.90f;

    private final long start = System.nanoTime();

    private static float jitter(float t, float seed) {
        return Mth.sin(t * 0.9f + seed) * 0.5f + Mth.sin(t * 2.3f + seed * 1.7f) * 0.3f
                + Mth.sin(t * 5.1f + seed * 0.4f) * 0.2f;
    }

    void render(GuiGraphics graphics, int width, int height) {
        float time = (System.nanoTime() - start) * 1e-9f;
        float cycle = HOLD + FADE;
        float t = time % (cycle * IMAGES.length);
        int index = (int) (t / cycle);
        int next = (index + 1) % IMAGES.length;
        float fade = Mth.clamp((t % cycle - HOLD) / FADE, 0, 1);
        fade = fade * fade * (3 - 2 * fade);

        float dx = jitter(time * SHAKE_SPEED, 0) * SHAKE_AMOUNT * 0.5f * height;
        float dy = jitter(time * SHAKE_SPEED, 11.3f) * SHAKE_AMOUNT * 0.5f * height;
        float rot = jitter(time * SHAKE_SPEED * 0.7f, 5) * SHAKE_ROTATION;

        graphics.fill(0, 0, width, height, 0xFF000000);
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f + dx, height / 2f + dy, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(-rot));
        graphics.pose().scale(1 / ZOOM, 1 / ZOOM, 1);
        draw(graphics, IMAGES[index], width, height, 1);
        if (fade > 0) {
            draw(graphics, IMAGES[next], width, height, fade);
        }
        graphics.pose().popPose();
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, 1);
    }

    private static void draw(GuiGraphics graphics, ResourceLocation image, int width, int height, float alpha) {
        Minecraft.getInstance().getTextureManager().getTexture(image).setFilter(true, false);
        RenderSystem.enableBlend();
        graphics.setColor(1, 1, 1, alpha);
        graphics.blit(image, -width / 2, -height / 2, width, height, 0, 0, 1, 1, 1, 1);
    }
}
