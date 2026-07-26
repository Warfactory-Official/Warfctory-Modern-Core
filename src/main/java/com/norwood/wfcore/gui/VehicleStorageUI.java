package com.norwood.wfcore.gui;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;

/**
 * Builds the WFCore vehicle-storage {@link ModularUI}, styled to resemble Superb Warfare's stock vehicle
 * container screen: two <em>detached</em> bordered panels — the vehicle's storage (titled with the vehicle
 * name, its slots in a scrollable list) sitting above a separate player-inventory panel. The main window
 * background is left {@link IGuiTexture#EMPTY} so the two panels read as distinct boxes rather than one sheet.
 *
 * <p>
 * The storage slots bind to the vehicle's item handler (wrapped as a {@link Container} by
 * {@link VehicleInventoryContainer}, since Superb Warfare 0.8.9 no longer makes {@code VehicleEntity} a
 * {@code Container}); LDLib's {@code ModularUIContainer} registers each {@link SlotWidget}'s native {@code Slot}
 * and syncs contents like a vanilla menu. Panels use {@link ResourceBorderTexture#BORDERED_BACKGROUND}, a
 * 9-slice that scales to any size — so an arbitrary (e.g. 50-slot) grid renders correctly, unlike Superb
 * Warfare's fixed per-size PNGs.
 *
 * <p>
 * Side-agnostic (no client-only refs): LDLib rebuilds the UI on the client by calling {@code createUI} again,
 * so the slot/column counts (and the vehicle name, read from the entity) are resolved identically on both sides.
 */
public final class VehicleStorageUI {

    private static final int SLOT = 18;
    /** Inner padding between a panel's border and its content. */
    private static final int PAD = 7;
    /** Height reserved at the top of a panel for its title label. */
    private static final int TITLE_H = 11;
    /** Storage rows shown before the list scrolls. */
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int SCROLL_BAR_W = 8;
    /** Gap between the main inventory rows and the hotbar row on the player panel. */
    private static final int HOTBAR_GAP = 4;
    /** Vertical gap between the two detached panels. */
    private static final int PANEL_GAP = 4;
    /** Minimum inner width of the storage panel, so the vehicle-name title always fits. */
    private static final int MIN_STORAGE_INNER_W = 160;
    /** Vanilla container-title colour (dark grey, no drop shadow). */
    private static final int TITLE_COLOR = 0x404040;

    private VehicleStorageUI() {}

    public static ModularUI build(VehicleEntity entity, Player player, int slots, int cols) {
        slots = Math.max(1, slots);
        cols = Math.max(1, Math.min(cols, slots));
        int rows = (slots + cols - 1) / cols;
        int visibleRows = Math.min(rows, MAX_VISIBLE_ROWS);
        boolean scroll = rows > MAX_VISIBLE_ROWS;
        int scrollW = scroll ? SCROLL_BAR_W : 0;

        int gridW = cols * SLOT;
        int gridH = visibleRows * SLOT;

        // --- panel geometry ---
        int storageInnerW = Math.max(gridW + scrollW, MIN_STORAGE_INNER_W);
        int storageW = storageInnerW + 2 * PAD;
        int storageH = PAD + TITLE_H + gridH + PAD;

        int playerInnerW = 9 * SLOT;
        int playerW = playerInnerW + 2 * PAD;
        int playerH = PAD + TITLE_H + 3 * SLOT + HOTBAR_GAP + SLOT + PAD;

        int uiW = Math.max(storageW, playerW);
        int uiH = storageH + PANEL_GAP + playerH;

        int storageX = (uiW - storageW) / 2;
        int playerX = (uiW - playerW) / 2;
        int playerY = storageH + PANEL_GAP;

        ModularUI ui = new ModularUI(uiW, uiH, (IUIHolder) entity, player);
        ui.background(IGuiTexture.EMPTY); // panels are the visible boxes -> detached look

        // ---- storage panel (title + scrollable slot list) ----
        WidgetGroup storagePanel = new WidgetGroup(storageX, 0, storageW, storageH);
        storagePanel.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);
        storagePanel.addWidget(new LabelWidget(PAD, PAD - 1, entity.getDisplayName())
                .setTextColor(TITLE_COLOR).setDropShadow(false));

        Container storage = new VehicleInventoryContainer(entity.getInventory());
        int gridX = PAD + (storageInnerW - gridW - scrollW) / 2; // centre the grid within the inner width
        DraggableScrollableWidgetGroup list =
                new DraggableScrollableWidgetGroup(gridX, PAD + TITLE_H, gridW + scrollW, gridH);
        list.setBackground(IGuiTexture.EMPTY);
        if (scroll) {
            list.setYScrollBarWidth(SCROLL_BAR_W)
                    .setYBarStyle(new ColorRectTexture(0x40000000), new ColorRectTexture(0xFF9A9A9A));
        }
        for (int i = 0; i < slots; i++) {
            list.addWidget(new SlotWidget(storage, i, (i % cols) * SLOT, (i / cols) * SLOT)
                    .setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE));
        }
        storagePanel.addWidget(list);
        ui.widget(storagePanel);

        // ---- player-inventory panel (detached, below) ----
        WidgetGroup playerPanel = new WidgetGroup(playerX, playerY, playerW, playerH);
        playerPanel.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);
        playerPanel.addWidget(new LabelWidget(PAD, PAD - 1, Component.translatable("container.inventory"))
                .setTextColor(TITLE_COLOR).setDropShadow(false));

        Inventory inv = player.getInventory();
        int playerGridTop = PAD + TITLE_H;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                playerPanel.addWidget(new SlotWidget(inv, 9 + r * 9 + c, PAD + c * SLOT, playerGridTop + r * SLOT)
                        .setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE)
                        .setLocationInfo(true, false));
            }
        }
        int hotbarTop = playerGridTop + 3 * SLOT + HOTBAR_GAP;
        for (int c = 0; c < 9; c++) {
            playerPanel.addWidget(new SlotWidget(inv, c, PAD + c * SLOT, hotbarTop)
                    .setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE)
                    .setLocationInfo(true, true));
        }
        ui.widget(playerPanel);

        return ui;
    }
}
