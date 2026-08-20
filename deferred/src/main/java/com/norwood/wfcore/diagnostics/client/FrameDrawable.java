package com.norwood.wfcore.diagnostics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;

/**
 * The viewer's preview pane: blits the selected capture letterboxed inside the pane, or a status line while
 * nothing is selected, the bytes are still in flight, or the transfer failed.
 *
 * <p>
 * The texture probe deliberately uses the two-arg {@code getTexture(id, null)} lookup — the single-arg overload
 * tries to load the id from the resource pack, spams FileNotFound and caches a broken entry, which matters here
 * because a frame's id only becomes real once {@link DiagViewerClient} has uploaded it. Same pitfall the chunk
 * map's {@code MapTileDrawable} documents.
 */
final class FrameDrawable implements IDrawable {

    private static final int BACKDROP = 0xFF101014;
    private static final int BORDER = 0xFF303038;
    private static final int TEXT = 0xFFB0B0B8;

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        GuiDraw.drawRect(context.getGraphics(), x, y, width, height, BACKDROP);
        GuiDraw.drawBorderInsideXYWH(context.getGraphics(), x, y, width, height, 1, BORDER);

        DiagViewerClient.Frame frame = DiagViewerClient.selectedFrame();
        if (frame != null && isUploaded(frame)) {
            drawLetterboxed(context, frame, x + 1, y + 1, width - 2, height - 2);
            return;
        }
        status(context, x, y, width, height);
    }

    private static boolean isUploaded(DiagViewerClient.Frame frame) {
        AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(frame.texture(), null);
        return loaded instanceof DynamicTexture;
    }

    /**
     * Fits the frame inside the pane preserving its aspect ratio, so a capture is never stretched.
     *
     * <p>
     * Blits through vanilla {@link net.minecraft.client.gui.GuiGraphics} rather than
     * {@code GuiDraw.drawTexture}: ModularUI's helper first runs the id through
     * {@code UITexture.GUI_TEXTURE_ID_CONVERTER.fileToId} to look it up in the GUI sprite atlas, and that
     * conversion throws {@link StringIndexOutOfBoundsException} on a path shorter than its
     * {@code textures/gui/} prefix — which a short id like {@code wfcore:diag/view/1} always is. These frames
     * are {@link DynamicTexture}s registered straight into the {@code TextureManager}, never atlas sprites,
     * so the atlas probe is wrong for them regardless.
     */
    private static void drawLetterboxed(GuiContext context, DiagViewerClient.Frame frame,
                                        int x, int y, int width, int height) {
        float scale = Math.min((float) width / frame.width(), (float) height / frame.height());
        int w = Math.max(1, Math.round(frame.width() * scale));
        int h = Math.max(1, Math.round(frame.height() * scale));
        int ox = x + (width - w) / 2;
        int oy = y + (height - h) / 2;
        context.getGraphics().blit(frame.texture(), ox, oy, w, h,
                0f, 0f, frame.width(), frame.height(), frame.width(), frame.height());
    }

    private static void status(GuiContext context, int x, int y, int width, int height) {
        String error = DiagViewerClient.error();
        Component line;
        if (error != null) {
            line = Component.literal(error);
        } else if (DiagViewerClient.selected() == null) {
            line = Component.translatable("wfcore.gui.diag_view.no_selection");
        } else {
            line = Component.translatable("wfcore.gui.diag_view.loading");
        }
        int textWidth = Minecraft.getInstance().font.width(line);
        GuiDraw.drawText(context.getGraphics(), line,
                x + (width - textWidth) / 2f, y + height / 2f - 4, 1f, TEXT, false);
    }
}
