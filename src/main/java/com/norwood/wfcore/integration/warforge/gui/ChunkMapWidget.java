package com.norwood.wfcore.integration.warforge.gui;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.widget.ParentWidget;

/**
 * The chunk map's interaction layer: click-and-drag panning (like the vanilla advancements screen / {@link
 * com.norwood.wfcore.common.gui.widget.PanViewport}) plus click-to-target, replacing the old arrow buttons.
 * Unlike a scroll container the map has no larger content to shift — dragging instead moves the view centre in
 * whole-chunk steps (one grid cell = one chunk) and re-bakes the tiles via {@code request}. A press that never
 * moves past {@link #DRAG_THRESHOLD} is treated as a click and targets the chunk under the cursor via
 * {@code onPick}; the (non-interactive) tile children draw through to this widget so it receives every event.
 */
public class ChunkMapWidget extends ParentWidget<ChunkMapWidget> implements Interactable {

    /** Pixels the cursor must travel before a press becomes a pan rather than a click. */
    private static final int DRAG_THRESHOLD = 4;

    private final int[] center;
    private final int cell;
    private final Runnable request;
    private final Runnable onPick;

    private boolean pressed;
    private boolean dragged;
    private int startMouseX;
    private int startMouseY;
    private int appliedX;
    private int appliedY;

    /**
     * @param center  {@code [chunkX, chunkZ]} view centre, mutated in place as the map is panned
     * @param cell    grid cell size in pixels (one cell spans one chunk)
     * @param request re-bakes the tiles for the current {@code center}
     * @param onPick  targets the chunk under the cursor (reads the hovered block published by the tiles)
     */
    public ChunkMapWidget(int[] center, int cell, Runnable request, Runnable onPick) {
        this.center = center;
        this.cell = cell;
        this.request = request;
        this.onPick = onPick;
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton != 0) {
            return Result.IGNORE;
        }
        ModularGuiContext ctx = getContext();
        pressed = true;
        dragged = false;
        startMouseX = ctx.getAbsMouseX();
        startMouseY = ctx.getAbsMouseY();
        appliedX = 0;
        appliedY = 0;
        return Result.SUCCESS;
    }

    @Override
    public void onMouseDrag(int mouseButton, double startX, double startY) {
        if (!pressed) {
            return;
        }
        ModularGuiContext ctx = getContext();
        int totalDx = ctx.getAbsMouseX() - startMouseX;
        int totalDy = ctx.getAbsMouseY() - startMouseY;
        if (!dragged && (Math.abs(totalDx) > DRAG_THRESHOLD || Math.abs(totalDy) > DRAG_THRESHOLD)) {
            dragged = true;
        }
        if (!dragged) {
            return;
        }
        // Grab-and-drag: moving the map right/down reveals content to the west/north, so the view centre
        // shifts opposite the drag. Apply only the whole-chunk change since the press so panning is stable.
        int desiredX = -totalDx / cell;
        int desiredY = -totalDy / cell;
        if (desiredX != appliedX || desiredY != appliedY) {
            center[0] += desiredX - appliedX;
            center[1] += desiredY - appliedY;
            appliedX = desiredX;
            appliedY = desiredY;
            request.run();
        }
    }

    @Override
    public boolean onMouseReleased(int mouseButton) {
        if (pressed && !dragged && mouseButton == 0) {
            onPick.run();
        }
        pressed = false;
        return false;
    }
}
