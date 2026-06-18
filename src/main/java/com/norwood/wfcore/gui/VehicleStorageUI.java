package com.norwood.wfcore.gui;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;

/**
 * Builds the resizable WFCore vehicle-storage {@link ModularUI}. The storage slots bind directly to the
 * {@link VehicleEntity} (which is itself a {@code Container}); LDLib's {@code ModularUIContainer} registers each
 * {@link SlotWidget}'s native {@code Slot} and syncs contents like a vanilla menu. The window background is
 * {@link ResourceBorderTexture#BORDERED_BACKGROUND}, a 9-slice texture that scales to any size — so unlike Superb
 * Warfare's fixed per-bucket PNGs, an arbitrary grid renders correctly.
 *
 * <p>
 * This is intentionally side-agnostic (no client-only references) because LDLib rebuilds the UI on the client by
 * calling {@code createUI} again; the slot/column counts are passed in so both sides build an identical grid.
 */
public final class VehicleStorageUI {

    private static final int SLOT = 18;
    private static final int MARGIN = 7;
    private static final int MAX_VISIBLE_ROWS = 6;
    /** Gap between the storage area and the player inventory (leaves room for the inventory label). */
    private static final int PLAYER_GAP = 13;
    private static final int SCROLL_BAR_W = 8;

    private VehicleStorageUI() {}

    public static ModularUI build(VehicleEntity entity, Player player, int slots, int cols) {
        slots = Math.max(1, slots);
        cols = Math.max(1, Math.min(cols, slots));
        int rows = (slots + cols - 1) / cols;
        boolean scroll = rows > MAX_VISIBLE_ROWS;
        int visibleRows = Math.min(rows, MAX_VISIBLE_ROWS);
        int scrollBarW = scroll ? SCROLL_BAR_W : 0;

        int storageW = cols * SLOT;
        int contentW = Math.max(cols, 9) * SLOT;

        int storageX = MARGIN + (contentW - storageW) / 2;
        int playerX = MARGIN + (contentW - 9 * SLOT) / 2;
        int gridTop = MARGIN;
        int storageAreaH = visibleRows * SLOT;

        int playerTop = gridTop + storageAreaH + PLAYER_GAP;
        int hotbarTop = playerTop + 3 * SLOT + 4;

        int width = contentW + 2 * MARGIN + scrollBarW;
        int height = hotbarTop + SLOT + MARGIN;

        ModularUI ui = new ModularUI(width, height, (IUIHolder) entity, player);
        ui.background(ResourceBorderTexture.BORDERED_BACKGROUND);

        // Storage slots bound to the vehicle entity (a Container). Indices 0..slots-1.
        if (scroll) {
            DraggableScrollableWidgetGroup group = new DraggableScrollableWidgetGroup(storageX, gridTop,
                    storageW + scrollBarW, storageAreaH);
            group.setBackground(IGuiTexture.EMPTY);
            group.setYScrollBarWidth(scrollBarW)
                    .setYBarStyle(new ColorRectTexture(0x40000000), new ColorRectTexture(0xFFAAAAAA));
            for (int i = 0; i < slots; i++) {
                group.addWidget(new SlotWidget(entity, i, (i % cols) * SLOT, (i / cols) * SLOT)
                        .setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE));
            }
            ui.widget(group);
        } else {
            for (int i = 0; i < slots; i++) {
                ui.widget(new SlotWidget(entity, i, storageX + (i % cols) * SLOT, gridTop + (i / cols) * SLOT)
                        .setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE));
            }
        }

        // Player inventory: main 3x9 (indices 9..35) then hotbar (indices 0..8).
        Inventory inv = player.getInventory();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                ui.widget(new SlotWidget(inv, 9 + r * 9 + c, playerX + c * SLOT, playerTop + r * SLOT)
                        .setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE)
                        .setLocationInfo(true, false));
            }
        }
        for (int c = 0; c < 9; c++) {
            ui.widget(new SlotWidget(inv, c, playerX + c * SLOT, hotbarTop)
                    .setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE)
                    .setLocationInfo(true, true));
        }

        return ui;
    }
}
