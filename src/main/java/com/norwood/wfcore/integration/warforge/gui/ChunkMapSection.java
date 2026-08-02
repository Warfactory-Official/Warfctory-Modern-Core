package com.norwood.wfcore.integration.warforge.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.TextWidget;
import com.flansmod.warforge.api.modularui.ChunkMapTextureDaemon;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.network.PacketRequestTerrainColors;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.norwood.wfcore.common.machine.MissileLauncherMachine;

import java.util.Map;

/**
 * The launcher GUI's pannable chunk-map target picker, built on WarForge's async map-tile pipeline:
 * {@link ChunkMapTextureDaemon} renders one 16x16 terrain texture per chunk off-thread (warforge's client
 * tick handler flushes the upload queue), a {@link PacketRequestTerrainColors} round-trip fills in chunks
 * the client hasn't loaded, and each grid cell draws its {@link MapTileDrawable} by texture name — so
 * panning only shifts the view centre and issues a new request, never rebuilds widgets.
 *
 * <p>
 * Clicking a tile writes the chunk's centre block into the GUI's X/Z coordinate sync values (and resets Y
 * to auto), which is the same client→server conduit the text fields use — the server never needs to know
 * the client's pan state. This class is the only place that touches {@code com.flansmod.*} for the map, and
 * it is only reached behind {@code WarforgeIntegration.isLoaded()}; tiles and daemon calls are additionally
 * client-gated so a dedicated server building the same panel never loads client texture classes.
 */
public final class ChunkMapSection {

    /** Daemon texture namespace; distinct from warforge's own "claimmap"/siege namespaces. */
    private static final String NAMESPACE = "wfcore_launcher";

    private static final int RADIUS = 4;
    private static final int GRID = 2 * RADIUS + 1;
    private static final int CELL = 20;
    /** Height reserved for the caption row above the map (one text line + gap). */
    private static final int CAPTION_H = 11;
    /** Re-bake the last request every this many client ticks so server terrain colours get picked up. */
    private static final int REBUILD_INTERVAL = 60;

    private static final int COLOR_BORDER = 0xFF101010;

    private ChunkMapSection() {}

    /**
     * Adds the map section into the pane content box {@code (x, y, w, h)}: the caption sits at the top-left
     * like the other panes' headers and the map is centred in the space below it. Widgets are only added on
     * the client — the server side of this synced panel needs none of them (tile clicks travel through the
     * coord sync values).
     */
    public static void attach(ModularPanel<?> panel, MissileLauncherMachine mte, PosGuiData data,
                              int x, int y, int w, int h,
                              IntSyncValue xSync, IntSyncValue ySync, IntSyncValue zSync) {
        if (!data.isClient()) {
            return;
        }
        BlockPos silo = data.getBlockPos();
        ResourceKey<Level> dim = data.getLevel().dimension();
        // client-side pan state: the chunk at the view centre. Open centred on the target if one is set
        // (typed or map-picked), otherwise on the silo.
        int[] center = mte.hasTarget()
                ? new int[] { mte.getTargetX() >> 4, mte.getTargetZ() >> 4 }
                : new int[] { silo.getX() >> 4, silo.getZ() >> 4 };
        // Last target chunk we auto-centred on, so we only re-centre when it actually moves (not every tick,
        // which would fight manual panning).
        int[] lastTarget = { mte.getTargetX() >> 4, mte.getTargetZ() >> 4 };

        Runnable request = () -> {
            ChunkMapTextureDaemon.requestMapUpdate(NAMESPACE, dim, center[0], center[1], RADIUS, Map.of());
            PacketRequestTerrainColors terrainReq = new PacketRequestTerrainColors();
            terrainReq.center = new DimChunkPos(dim, center[0], center[1]);
            terrainReq.radius = RADIUS;
            WarForgeMod.NETWORK.sendToServer(terrainReq);
        };

        // World block under the mouse, written each frame by the hovered tile's drawable (the draw pass is
        // the one place where mouse and tile coordinates are guaranteed to share a space) and read by the
        // click handler for block-precise targeting.
        int[] hoverBlock = { Integer.MIN_VALUE, Integer.MIN_VALUE };

        // A click (no drag) targets the block under the cursor, published by the hovered tile — the same
        // client->server conduit the coord fields use. Resetting Y to auto matches the field/tile behaviour.
        Runnable onPick = () -> {
            if (hoverBlock[0] == Integer.MIN_VALUE) {
                return;
            }
            xSync.setIntValue(hoverBlock[0], true, true);
            zSync.setIntValue(hoverBlock[1], true, true);
            ySync.setIntValue(MissileLauncherMachine.Y_AUTO, true, true);
        };

        // Header row at the pane's content origin (matching the left panes' headers), map centred below it.
        int mapSize = GRID * CELL;
        int mapX = x + Math.max(0, (w - mapSize) / 2);
        int mapY = y + CAPTION_H + Math.max(0, (h - CAPTION_H - mapSize) / 2);

        ChunkMapWidget map = new ChunkMapWidget(center, CELL, request, onPick);
        map.name("chunk_map");
        map.pos(mapX, mapY).size(mapSize, mapSize);
        map.background(new Rectangle().color(COLOR_BORDER));
        // Tooltip lives on the map itself (the tiles are hover-transparent so clicks/drags reach the map);
        // it reads the block under the cursor that the hovered tile published this frame.
        map.tooltipDynamic(t -> {
            if (hoverBlock[0] == Integer.MIN_VALUE) {
                return;
            }
            t.addLine(Text.str("Block " + hoverBlock[0] + ", " + hoverBlock[1]));
            t.addLine(Text.str("Chunk " + (hoverBlock[0] >> 4) + ", " + (hoverBlock[1] >> 4)));
            t.addLine(Text.lang("wfcore.gui.launcher.map_click"));
        }).tooltipAutoUpdate(true);

        // Kick the first request from the update loop (runs on the client screen only) and re-bake
        // periodically: warforge's terrain-colour reply only rebuilds its own "claimmap" namespace, so
        // ours refreshes on this timer instead. requestMapUpdate dedupes identical requests, and the
        // rebuild is 81 tiny textures on the daemon's single background thread — cheap while the GUI is up.
        int[] ticks = { 0 };
        map.onUpdateListener(widget -> {
            if (ticks[0] == 0) {
                request.run();
            }
            // Follow the target when it changes (e.g. coords typed into the fields) but only re-centre when
            // it lands outside the current view, so clicking a visible chunk or panning by hand still sticks.
            int tcx = mte.getTargetX() >> 4;
            int tcz = mte.getTargetZ() >> 4;
            if (mte.hasTarget() && (tcx != lastTarget[0] || tcz != lastTarget[1])) {
                lastTarget[0] = tcx;
                lastTarget[1] = tcz;
                boolean offView = Math.abs(tcx - center[0]) > RADIUS || Math.abs(tcz - center[1]) > RADIUS;
                if (offView) {
                    center[0] = tcx;
                    center[1] = tcz;
                    request.run();
                }
            }
            if (++ticks[0] % REBUILD_INTERVAL == 0) {
                ChunkMapTextureDaemon.rebuildLast(NAMESPACE);
            }
        });

        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                map.child(buildTile(mte, center, silo, gx, gz, hoverBlock));
            }
        }
        panel.child(map);

        // caption: the map pane's header row, top-left like "Available missiles" on the left panes
        panel.child(new TextWidget<>(Text.lang("wfcore.gui.launcher.map_hint"))
                .pos(x, y).name("map_hint"));
    }

    /**
     * One chunk cell — a hover-transparent visual tile. Its world chunk is derived from the pan centre every
     * frame, so the same widget shows a different chunk after panning. All interaction (panning + click-to-
     * target) is handled by the parent {@link ChunkMapWidget}: the tile reports {@code canHover() == false} and
     * {@code canHoverThrough() == true} so clicks/drags fall straight through to it. The tile still draws and,
     * via its {@link MapTileDrawable}, publishes the block under the cursor into {@code hoverBlock}.
     */
    private static Tile buildTile(MissileLauncherMachine mte, int[] center, BlockPos silo,
                                  int gx, int gz, int[] hoverBlock) {
        Tile tile = new Tile();
        tile.name("map_tile_" + gx + "_" + gz);
        tile.pos(gx * CELL, gz * CELL).size(CELL, CELL);
        tile.background(new MapTileDrawable(NAMESPACE,
                () -> center[0] + gx - RADIUS,
                () -> center[1] + gz - RADIUS,
                mte::getTargetX,
                mte::getTargetZ,
                mte::hasTarget,
                mte::getTrackedMissileX,
                mte::getTrackedMissileZ,
                mte::hasActiveTrackedMissile,
                () -> (silo.getX() >> 4) == center[0] + gx - RADIUS &&
                        (silo.getZ() >> 4) == center[1] + gz - RADIUS,
                hoverBlock));
        return tile;
    }

    /** A map cell that never captures the mouse, so all clicks/drags reach the parent {@link ChunkMapWidget}. */
    private static final class Tile extends ParentWidget<Tile> {
        @Override
        public boolean canHover() {
            return false;
        }

        @Override
        public boolean canHoverThrough() {
            return true;
        }
    }
}
