package com.norwood.wfcore.integration.warforge.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import com.flansmod.warforge.Tags;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * One chunk tile of the launcher's target map: draws the {@link ChunkMapTextureDaemon}-generated terrain
 * texture for the chunk plus a thin grid line, a block-precise target marker, a highlight on the silo's own
 * chunk, and a white block cursor under the mouse. The chunk coordinates come from suppliers so panning the
 * map (which shifts the client-side view centre) retargets every tile without rebuilding widgets.
 *
 * <p>
 * While hovered, the tile also writes the exact world block under the cursor into {@code hoverBlock} — the
 * hover math here (mouse vs the draw x/y) is the one coordinate pairing that is guaranteed consistent (it is
 * exactly what warforge's own {@code MapDrawable} uses), so the click handler in {@link ChunkMapSection}
 * reads the block from this holder instead of re-deriving it from widget areas.
 *
 * <p>
 * Client-only (references {@link Minecraft}); construct it exclusively from {@link ChunkMapSection}, which
 * the GUI only reaches on the client. The texture probe deliberately uses the two-arg
 * {@code getTexture(id, null)} lookup: the single-arg overload would try to load the (nonexistent) texture
 * file from the resource pack for ids the daemon hasn't registered yet, spam FileNotFound errors and cache
 * a broken entry — warforge's own {@code MapDrawable} documents the same pitfall. Tiles the daemon is still
 * building off-thread render as a neutral placeholder until their {@link DynamicTexture} lands.
 */
final class MapTileDrawable implements IDrawable {

    private static final int PLACEHOLDER = 0xFF2A2A2A;
    private static final int GRID = 0x33FFFFFF;
    private static final int TARGET = 0x66FF4040;
    private static final int MISSILE = 0xFFFFFF38;
    private static final int MISSILE_BORDER = 0xFF101010;
    private static final int SELF = 0x8040A0FF;
    private static final int CURSOR = 0x66FFFFFF;

    private final String namespace;
    private final IntSupplier chunkX;
    private final IntSupplier chunkZ;
    private final IntSupplier targetBlockX;
    private final IntSupplier targetBlockZ;
    private final BooleanSupplier hasTarget;
    private final IntSupplier missileBlockX;
    private final IntSupplier missileBlockZ;
    private final BooleanSupplier hasMissile;
    private final BooleanSupplier isSelfChunk;
    /** Shared out-holder: world block (x, z) under the mouse, written by whichever tile is hovered. */
    private final int[] hoverBlock;

    MapTileDrawable(String namespace, IntSupplier chunkX, IntSupplier chunkZ,
                     IntSupplier targetBlockX, IntSupplier targetBlockZ, BooleanSupplier hasTarget,
                     IntSupplier missileBlockX, IntSupplier missileBlockZ, BooleanSupplier hasMissile,
                     BooleanSupplier isSelfChunk, int[] hoverBlock) {
        this.namespace = namespace;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.targetBlockX = targetBlockX;
        this.targetBlockZ = targetBlockZ;
        this.hasTarget = hasTarget;
        this.missileBlockX = missileBlockX;
        this.missileBlockZ = missileBlockZ;
        this.hasMissile = hasMissile;
        this.isSelfChunk = isSelfChunk;
        this.hoverBlock = hoverBlock;
    }

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        int cx = chunkX.getAsInt();
        int cz = chunkZ.getAsInt();
        // The daemon registers its DynamicTextures under warforge's namespace regardless of ours.
        ResourceLocation texture = new ResourceLocation(Tags.MODID,
                ChunkMapTextureDaemon.getTextureName(namespace, level.dimension(), cx, cz));

        AbstractTexture loaded = Minecraft.getInstance().getTextureManager().getTexture(texture, null);
        if (loaded instanceof DynamicTexture) {
            GuiDraw.drawTexture(context.getLastGraphicsPose(), texture,
                    x, y, x + width, y + height, 0f, 0f, 1f, 1f, true);
        } else {
            GuiDraw.drawRect(context.getGraphics(), x, y, width, height, PLACEHOLDER);
        }

        // chunk grid line (top + left edge; neighbours supply the other two)
        GuiDraw.drawRect(context.getGraphics(), x, y, width, 1, GRID);
        GuiDraw.drawRect(context.getGraphics(), x, y, 1, height, GRID);

        float px = width / 16f; // on-screen size of one block
        if (isSelfChunk.getAsBoolean()) {
            GuiDraw.drawRect(context.getGraphics(), x, y, width, 2, SELF);
            GuiDraw.drawRect(context.getGraphics(), x, y + height - 2, width, 2, SELF);
            GuiDraw.drawRect(context.getGraphics(), x, y, 2, height, SELF);
            GuiDraw.drawRect(context.getGraphics(), x + width - 2, y, 2, height, SELF);
        }

        // block-precise target marker: crosshair bars through the target block within this chunk
        int tx = targetBlockX.getAsInt();
        int tz = targetBlockZ.getAsInt();
        if (hasTarget.getAsBoolean() && (tx >> 4) == cx && (tz >> 4) == cz) {
            float bx = x + (tx & 15) * px;
            float bz = y + (tz & 15) * px;
            GuiDraw.drawRect(context.getGraphics(), bx, y + 1, Math.max(1f, px), height - 1, TARGET);
            GuiDraw.drawRect(context.getGraphics(), x + 1, bz, width - 1, Math.max(1f, px), TARGET);
        }

        // Active missile marker: a high-contrast 5x5 beacon centred on its block. The suppliers read the
        // launcher telemetry snapshot, which remains live while WF-Ballistics offloads the missile to sim.
        int missileX = missileBlockX.getAsInt();
        int missileZ = missileBlockZ.getAsInt();
        if (hasMissile.getAsBoolean() && (missileX >> 4) == cx && (missileZ >> 4) == cz) {
            float markerX = x + (missileX & 15) * px + px * 0.5f;
            float markerY = y + (missileZ & 15) * px + px * 0.5f;
            GuiDraw.drawRect(context.getGraphics(), markerX - 3, markerY - 3, 6, 6, MISSILE_BORDER);
            GuiDraw.drawRect(context.getGraphics(), markerX - 2, markerY - 2, 4, 4, MISSILE);
        }

        // hover: publish the exact world block under the cursor and draw a block cursor. This mouse-vs-draw
        // comparison is the same one MapDrawable's hover uses, so it is always in the right space.
        int mx = context.getMouseX();
        int my = context.getMouseY();
        if (mx >= x && mx < x + width && my >= y && my < y + height) {
            int bx = Math.min(15, (mx - x) * 16 / width);
            int bz = Math.min(15, (my - y) * 16 / height);
            hoverBlock[0] = cx * 16 + bx;
            hoverBlock[1] = cz * 16 + bz;
            GuiDraw.drawRect(context.getGraphics(), x + bx * px, y + bz * px,
                    Math.max(1f, px), Math.max(1f, px), CURSOR);
        }
    }
}
