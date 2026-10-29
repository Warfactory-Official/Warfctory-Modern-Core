package com.norwood.wfcore.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;

/** Title screen: slideshow, a held PDA whose screen is the menu, vanilla splash text beside it. */
public final class WFTitleScreen extends TitleScreen {
    private static final float LOGO_WIDTH = 0.42f;
    private static final float LOGO_PIXELS = 256;
    private static final int LOGO_TOP = 6;
    private static Runnable pending;

    private final TitleBackground background = new TitleBackground();
    private final long start = System.nanoTime();
    private final LogoRenderer logo = new LogoRenderer(false);

    public WFTitleScreen() {
        super(false);
    }

    static void defer(Runnable run) {
        pending = run;
    }

    @Override
    protected void init() {
        SplashRenderer splash = minecraft.getSplashManager().getSplash();
        TitleMenu.splash(splash == null ? "WARFACTORY" : splash.splash);
    }

    @Override
    public void tick() {
        Runnable run = pending;
        pending = null;
        if (run != null) {
            run.run();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        background.render(graphics, width, height);
        float time = (System.nanoTime() - start) * 1e-9f;
        TitlePda.render(graphics, width, height, mouseX, mouseY, time);
        graphics.pose().pushPose();
        float scale = width * LOGO_WIDTH / LOGO_PIXELS;
        graphics.pose().scale(scale, scale, 1);
        logo.renderLogo(graphics, (int) (width / scale), 1, (int) (LOGO_TOP / scale));
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            TitlePda.press(true);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) {
            TitlePda.press(false);
        }
        return true;
    }

    @Override
    public void removed() {
        TitlePda.close();
    }
}
