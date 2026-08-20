package com.norwood.wfcore.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import brachy.modularui.api.layout.ILayoutWidget;
import brachy.modularui.api.widget.IParentWidget;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.widget.AbstractScrollWidget;
import brachy.modularui.widget.scroll.VerticalScrollData;

import java.util.List;
import java.util.Locale;

/**
 * The Crafting Station's scrollable 512-slot store with client-side search filtering.
 *
 * <p>
 * Children must be added in handler-index order: the i-th child backs handler slot {@code i}. On
 * {@link #applyFilter}, {@link #layoutWidgets()} packs the matching slots into a gap-free grid at the top and
 * parks the rest far above the viewport, where the scroll widget's stencil clips them out of the draw and
 * {@code getWidgetsAt} keeps them out of mouse hit-testing. A slot's <em>handler index never changes</em>,
 * only its on-screen position, so slot sync and the recipe-viewer transfer contract are untouched.
 *
 * <p>
 * Filtering deliberately moves slots rather than disabling them. In ModularUI 3, {@code ItemSlot.onUpdate}
 * pushes {@code areAncestorsEnabled()} to the server every tick, so a slot hidden with {@code setEnabledIf}
 * would deactivate its <em>vanilla</em> slot server-side. Since an empty stack never matches a non-empty
 * query, every empty slot would be filtered out, and shift-clicking into the store would stop working
 * entirely while a search was active. Repositioning has no server-side effect at all.
 */
public class StorageGridWidget extends AbstractScrollWidget<IWidget, StorageGridWidget>
                               implements ILayoutWidget, IParentWidget<IWidget, StorageGridWidget> {

    public static final int SLOT = 18;
    /** Far above the viewport: clipped from the stencil, and never scrolled back into view. */
    private static final int PARK_Y = -10_000;

    private final IItemHandler storage;
    private final int cols;
    private String query = "";

    public StorageGridWidget(IItemHandler storage, int cols) {
        super(null, new VerticalScrollData());
        this.storage = storage;
        this.cols = cols;
    }

    /**
     * Re-flows the grid to show only the slots whose item matches {@code text}; an empty query shows every
     * slot. Client-side only — the server's copy of the tree keeps an empty query and lays out normally.
     */
    public void applyFilter(String text) {
        String next = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (next.equals(this.query)) return;
        this.query = next;
        getScrollArea().getScrollY().scrollTo(getScrollArea(), 0);
        scheduleResize();
    }

    @Override
    public boolean layoutWidgets() {
        List<IWidget> slots = getChildren();
        int shown = 0;
        for (int i = 0; i < slots.size(); i++) {
            IWidget slot = slots.get(i);
            // Same contract as ListWidget: bail out and get called again once the children have been sized.
            if (!slot.resizer().isWidthCalculated() || !slot.resizer().isHeightCalculated()) {
                return false;
            }
            if (matches(storage.getStackInSlot(i))) {
                slot.getArea().rx = (shown % cols) * SLOT;
                slot.getArea().ry = (shown / cols) * SLOT;
                shown++;
            } else {
                slot.getArea().rx = 0;
                slot.getArea().ry = PARK_Y;
            }
            slot.resizer().setPosResized(true, true);
        }
        int rows = (shown + cols - 1) / cols;
        getScrollArea().getScrollY().setScrollSize(rows * SLOT);
        return true;
    }

    private boolean matches(ItemStack stack) {
        if (query.isEmpty()) return true;
        if (stack.isEmpty()) return false;
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public boolean addChild(IWidget child, int index) {
        return super.addChild(child, index);
    }
}
