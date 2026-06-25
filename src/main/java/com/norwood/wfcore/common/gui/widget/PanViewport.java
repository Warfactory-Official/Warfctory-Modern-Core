package com.norwood.wfcore.common.gui.widget;

import brachy.modularui.api.layout.IViewport;
import brachy.modularui.api.layout.IViewportStack;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.widget.ParentWidget;

/**
 * A clipped viewport whose (larger) content is moved by click-and-dragging anywhere inside it — like the
 * vanilla advancements screen — instead of by scrollbars. Children live in a virtual content space whose full
 * extent is declared with {@link #contentSize(int, int)}; only the part currently under the widget is drawn,
 * shifted by an {@code (x, y)} offset that dragging (and the mouse wheel) adjust and clamp to the content.
 *
 * <p>It is a drop-in replacement for a scroll container: add children at absolute positions in content space,
 * set {@code contentSize(w, h)} to the full extent, and panning/clipping is automatic. The implementation is
 * self-contained (it keeps its own offset, holds no external references) so it can be lifted into ModularUI
 * upstream as a general-purpose widget.
 *
 * @param <W> self type for the fluent builder
 */
public class PanViewport<W extends PanViewport<W>> extends ParentWidget<W> implements IViewport, Interactable {

    private int contentWidth;
    private int contentHeight;
    private int xOffset;
    private int yOffset;
    private int scrollStep = 16;

    private boolean dragging;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private int dragStartXOffset;
    private int dragStartYOffset;

    /** Full pannable extent of the content, in child coordinates. */
    public W contentSize(int width, int height) {
        this.contentWidth = Math.max(0, width);
        this.contentHeight = Math.max(0, height);
        clampOffsets();
        return getThis();
    }

    /** Pixels panned per mouse-wheel notch (default 16). */
    public W scrollStep(int step) {
        this.scrollStep = Math.max(1, step);
        return getThis();
    }

    public W setOffset(int x, int y) {
        this.xOffset = x;
        this.yOffset = y;
        clampOffsets();
        return getThis();
    }

    public int getOffsetX() {
        return xOffset;
    }

    public int getOffsetY() {
        return yOffset;
    }

    private int maxOffsetX() {
        return Math.max(0, contentWidth - getArea().width);
    }

    private int maxOffsetY() {
        return Math.max(0, contentHeight - getArea().height);
    }

    private void clampOffsets() {
        xOffset = clamp(xOffset, maxOffsetX());
        yOffset = clamp(yOffset, maxOffsetY());
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    // ---- IViewport: shift children by the pan offset, clip them to this widget's box ----

    @Override
    public void transformChildren(IViewportStack stack) {
        stack.translate(-xOffset, -yOffset);
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean transformed) {
        if (!transformed) {
            context.getStencil().pushAtZero(getArea());
        }
    }

    @Override
    public void postDraw(ModularGuiContext context, boolean transformed) {
        if (!transformed) {
            context.getStencil().pop();
        }
    }

    // ---- Interactable: drag to pan, wheel to nudge ----

    @Override
    public Result onMousePressed(int mouseButton) {
        if (mouseButton != 0 || (maxOffsetX() == 0 && maxOffsetY() == 0)) {
            return Result.IGNORE;
        }
        ModularGuiContext context = getContext();
        dragging = true;
        dragStartMouseX = context.getAbsMouseX();
        dragStartMouseY = context.getAbsMouseY();
        dragStartXOffset = xOffset;
        dragStartYOffset = yOffset;
        return Result.SUCCESS;
    }

    @Override
    public void onMouseDrag(int mouseButton, double startX, double startY) {
        if (!dragging) {
            return;
        }
        ModularGuiContext context = getContext();
        xOffset = clamp(dragStartXOffset - (context.getAbsMouseX() - dragStartMouseX), maxOffsetX());
        yOffset = clamp(dragStartYOffset - (context.getAbsMouseY() - dragStartMouseY), maxOffsetY());
    }

    @Override
    public boolean onMouseReleased(int mouseButton) {
        dragging = false;
        return false;
    }

    @Override
    public boolean onMouseScrolled(double scrollDelta) {
        if (maxOffsetX() == 0 && maxOffsetY() == 0) {
            return false;
        }
        int delta = (int) Math.signum(scrollDelta) * scrollStep;
        if (Interactable.hasShiftDown() && maxOffsetX() > 0) {
            xOffset = clamp(xOffset - delta, maxOffsetX());
        } else {
            yOffset = clamp(yOffset - delta, maxOffsetY());
        }
        return true;
    }
}
