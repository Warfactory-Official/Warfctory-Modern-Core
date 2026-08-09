package com.norwood.wfcore.gui;

import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Crafting Station's scrollable 512-slot store with client-side search filtering.
 *
 * <p>Holds every {@link StorageSlotWidget} in handler-index order and, on {@link #applyFilter}, compacts the slots
 * whose item matches the query into a gap-free grid at the top while parking the rest far above the viewport
 * (negative Y). Parked slots drop out of {@link #computeMax() the scroll height} (a large negative bottom never
 * wins the {@code Math.max}), out of the viewport, and out of mouse hit-testing — the same machinery scrolling
 * already relies on. A slot's <em>handler index never changes</em>, only its on-screen position, so the JEI
 * slot-index contract and on-disk persistence are untouched.
 *
 * <p>Filtering is a pure client-render concern and must run <b>client-side only</b>: the reflow toggles slot
 * visibility, and on the server an invisible slot reports {@code mayPlace == false} (via {@code isEnabled}), which
 * would stop JEI's server-side transfer from sourcing it. The caller guards {@link #applyFilter} accordingly.
 */
public class StorageGridWidget extends DraggableScrollableWidgetGroup {

    private static final int SLOT = 18;
    // Far above the viewport: excluded from computeMax (negative bottom) and never scrolled back into view.
    private static final int PARK_Y = -10_000;

    private final IItemHandlerModifiable storage;
    private final int cols;
    private final List<StorageSlotWidget> slots = new ArrayList<>();
    private String query = "";

    public StorageGridWidget(int x, int y, int width, int height, IItemHandlerModifiable storage, int cols) {
        super(x, y, width, height);
        this.storage = storage;
        this.cols = cols;
    }

    /** Adds a store slot. Must be called in handler-index order: the i-th slot added backs handler slot {@code i}. */
    public StorageGridWidget addSlot(StorageSlotWidget slot) {
        slots.add(slot);
        addWidget(slot);
        return this;
    }

    /** Re-flows the grid to show only slots whose item matches {@code text}; an empty query shows every slot. */
    public void applyFilter(String text) {
        this.query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        // Normalise children back to their base positions (and reset the scroll offset field) before reassigning.
        setScrollYOffset(0);
        int viewH = getSize().height;
        int shown = 0;
        for (int i = 0; i < slots.size(); i++) {
            StorageSlotWidget slot = slots.get(i);
            if (matches(storage.getStackInSlot(i))) {
                int col = shown % cols;
                int row = shown / cols;
                int rowY = row * SLOT;
                slot.setSelfPosition(col * SLOT, rowY);
                // Only rows in the current viewport draw now; scrolling reveals the rest (the scroll group
                // recomputes each child's visibility from its position on the next setScrollYOffset).
                slot.setVisible(rowY < viewH);
                shown++;
            } else {
                slot.setSelfPosition(0, PARK_Y);
                slot.setVisible(false);
            }
        }
        computeMax();
    }

    private boolean matches(ItemStack stack) {
        if (query.isEmpty()) return true;
        if (stack.isEmpty()) return false;
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.toString().toLowerCase(Locale.ROOT).contains(query);
    }
}
