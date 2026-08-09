package com.norwood.wfcore.gui;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import com.norwood.wfcore.common.machine.crafting.CraftingStationCrafter;
import com.norwood.wfcore.common.machine.crafting.CraftingStationCrafter.Match;
import com.norwood.wfcore.common.machine.crafting.CraftingStationMachine;


public final class CraftingStationUI {

    private static final int SLOT = 18;
    private static final int PAD = 5;
    private static final int INSET = 6;
    private static final int SCROLL_W = 8;
    private static final int STORAGE_COLS = 9;
    private static final int STORAGE_ROWS = 5;

    private static final ResourceLocation WOOD_TEX = new ResourceLocation("gtceu", "textures/block/treated_wood_planks.png");
    private static final int WOOD_TILE = 16;
    private static final int COLOR_BORDER = 0xFF241708;      // very dark brown frame
    private static final int COLOR_RECESS = 0x99140C05;      // darkened-wood recessed section (wood shows through)
    private static final int COLOR_TITLE_BAR = 0x66201308;   // subtle darkening behind the title
    private static final int COLOR_HEADER = 0xFFFFD98A;      // warm gold title
    private static final int COLOR_LABEL = 0xFFDCC6A0;       // light-tan section labels

    // section geometry (absolute positions in the ModularUI; child widgets sit relative to their group)
    private static final int TITLE_Y = PAD;
    private static final int TITLE_H = 18;
    private static final int CRAFT_X = PAD;
    private static final int ROW_Y = TITLE_Y + TITLE_H + PAD;                 // top of the craft + storage row
    private static final int CRAFT_W = 96;
    private static final int ROW_H = 110;
    private static final int STORAGE_X = CRAFT_X + CRAFT_W + PAD;
    private static final int STORAGE_W = INSET * 2 + STORAGE_COLS * SLOT + SCROLL_W;
    // Search box: sits on the storage panel's header row, right of the "Storage" label, flush with the grid.
    private static final int SEARCH_H = 12;
    private static final int SEARCH_Y = 3;
    private static final int SEARCH_X = INSET + 42;
    private static final int SEARCH_W = INSET + STORAGE_COLS * SLOT + SCROLL_W - SEARCH_X;
    private static final int TOOLS_Y = ROW_Y + ROW_H + PAD;
    private static final int TOOLS_H = 38;
    private static final int PLAYER_Y = TOOLS_Y + TOOLS_H + PAD;
    private static final int PLAYER_H = 94;

    private static final int UI_W = STORAGE_X + STORAGE_W + PAD;
    private static final int UI_H = PLAYER_Y + PLAYER_H + PAD;
    private static final int SECTION_W = UI_W - 2 * PAD;

    private static final int CRAFT_ALL_CAP = 1000;

    private CraftingStationUI() {}

    private static IGuiTexture framed(int fill, int border) {
        return new GuiTextureGroup(new ColorRectTexture(fill), new ColorBorderTexture(border, COLOR_BORDER));
    }

    /** A repeating treated-wood fill: the 16x16 plank texture blitted 1:1 across the area (never stretched). */
    private static IGuiTexture tiledWood() {
        return new IGuiTexture() {
            @Override
            public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
                int ox = (int) x, oy = (int) y;
                for (int dx = 0; dx < width; dx += WOOD_TILE) {
                    for (int dy = 0; dy < height; dy += WOOD_TILE) {
                        int w = Math.min(WOOD_TILE, width - dx);
                        int h = Math.min(WOOD_TILE, height - dy);
                        graphics.blit(WOOD_TEX, ox + dx, oy + dy, 0, 0, w, h, WOOD_TILE, WOOD_TILE);
                    }
                }
            }
        };
    }


    private static LabelWidget sectionLabel(int x, int y, String key) {
        return new LabelWidget(x, y, key).setTextColor(COLOR_LABEL).setDropShadow(true);
    }

    public static ModularUI build(CraftingStationMachine machine, Player player) {
        SimpleContainer grid = new SimpleContainer(9);
        SimpleContainer resultView = new SimpleContainer(1);
        IItemHandlerModifiable storage = machine.getStorage();
        IItemHandlerModifiable toolBay = machine.getToolBay();
        boolean client = player.level().isClientSide;

        Runnable recompute = () -> {
            if (client) return;
            Match m = CraftingStationCrafter.findMatch(player.level(), readGrid(grid), toolBay);
            resultView.setItem(0, m == null ? ItemStack.EMPTY : m.result().copy());
        };

        ModularUI ui = new ModularUI(UI_W, UI_H, machine, player);
        ui.background(new GuiTextureGroup(tiledWood(), new ColorBorderTexture(2, COLOR_BORDER)));

        ItemStack icon = machine.getDefinition().asStack();
        WidgetGroup title = new WidgetGroup(PAD, TITLE_Y, SECTION_W, TITLE_H);
        title.setBackground(framed(COLOR_TITLE_BAR, 1));
        title.addWidget(new ImageWidget(INSET - 3, 1, 16, 16, new ItemStackTexture(icon)));
        title.addWidget(new LabelWidget(INSET + 15, 5, icon.getDescriptionId())
                .setTextColor(COLOR_HEADER).setDropShadow(true));
        ui.widget(title);

        WidgetGroup craft = new WidgetGroup(CRAFT_X, ROW_Y, CRAFT_W, ROW_H);
        craft.setBackground(framed(COLOR_RECESS, 1));
        craft.addWidget(sectionLabel(INSET, 5, "container.crafting"));
        int gridY = 30;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int index = r * 3 + c;
                craft.addWidget(new SlotWidget(grid, index, INSET + c * SLOT, gridY + r * SLOT, true, true)
                        .setChangeListener(recompute)
                        .setBackgroundTexture(GuiTextures.SLOT));
            }
        }
        craft.addWidget(new CraftingResultSlot(resultView, INSET + 3 * SLOT + 8, gridY + SLOT, grid, toolBay,
                recompute));
        ui.widget(craft);

        WidgetGroup storagePanel = new WidgetGroup(STORAGE_X, ROW_Y, STORAGE_W, ROW_H);
        storagePanel.setBackground(framed(COLOR_RECESS, 1));
        storagePanel.addWidget(sectionLabel(INSET, 5, "wfcore.gui.crafting_station.storage"));

        StorageGridWidget storageGrid = new StorageGridWidget(
                INSET, 16, STORAGE_COLS * SLOT + SCROLL_W, STORAGE_ROWS * SLOT, storage, STORAGE_COLS);
        storageGrid.setBackground(IGuiTexture.EMPTY);
        storageGrid.setYScrollBarWidth(SCROLL_W)
                .setYBarStyle(new ColorRectTexture(0x40000000), new ColorRectTexture(0xFF9A9A9A));
        for (int i = 0; i < storage.getSlots(); i++) {
            StorageSlotWidget cell = new StorageSlotWidget(storage, i,
                    (i % STORAGE_COLS) * SLOT, (i / STORAGE_COLS) * SLOT);
            cell.setBackgroundTexture(GuiTextures.SLOT);
            storageGrid.addSlot(cell);
        }


        final TextFieldWidget[] searchRef = new TextFieldWidget[1];
        TextFieldWidget search = new TextFieldWidget(SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H,
                () -> searchRef[0] == null ? "" : searchRef[0].getRawCurrentString(),
                text -> {
                    if (client) storageGrid.applyFilter(text);
                });
        searchRef[0] = search;
        search.setClientSideWidget();
        search.setMaxStringLength(48)
                .setTextColor(COLOR_LABEL)
                .setBordered(false)
                .setBackground(framed(0xB3140C05, 1));
        search.setHoverTooltips(Component.translatable("wfcore.gui.crafting_station.search"));

        storagePanel.addWidget(storageGrid);
        storagePanel.addWidget(search);
        ui.widget(storagePanel);

        WidgetGroup tools = new WidgetGroup(PAD, TOOLS_Y, SECTION_W, TOOLS_H);
        tools.setBackground(framed(COLOR_RECESS, 1));
        tools.addWidget(sectionLabel(INSET, 4, "wfcore.gui.crafting_station.tools"));
        for (int i = 0; i < toolBay.getSlots(); i++) {
            tools.addWidget(new SlotWidget(toolBay, i, INSET + i * SLOT, 16)
                    .setChangeListener(recompute)   // auto-tool preview reacts to spares being added/removed
                    .setBackgroundTexture(GuiTextures.SLOT)
                    .setHoverTooltips(Component.translatable("wfcore.gui.crafting_station.tool_slot")));
        }
        ui.widget(tools);


        WidgetGroup playerPanel = new WidgetGroup(PAD, PLAYER_Y, SECTION_W, PLAYER_H);
        playerPanel.setBackground(framed(COLOR_RECESS, 1));
        playerPanel.addWidget(sectionLabel(INSET, 4, "container.inventory"));
        int invX = (SECTION_W - 9 * SLOT) / 2;
        int invY = 15;
        Inventory inv = player.getInventory();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                playerPanel.addWidget(new PlayerDepositSlot(inv, col + (row + 1) * 9,
                        invX + col * SLOT, invY + row * SLOT, false, storage));
            }
        }
        for (int col = 0; col < 9; col++) {
            playerPanel.addWidget(new PlayerDepositSlot(inv, col, invX + col * SLOT, invY + 58, true, storage));
        }
        ui.widget(playerPanel);

        // Return the grid to the player when the UI closes (transient, per-player); overflow drops at their feet.
        ui.registerCloseListener(() -> {
            if (client) return;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = grid.getItem(i);
                if (stack.isEmpty()) continue;
                grid.setItem(i, ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        });

        return ui;
    }

    private static ItemStack[] readGrid(Container grid) {
        ItemStack[] cells = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = grid.getItem(i);
        }
        return cells;
    }

    private static boolean canAddToCursor(ItemStack cursor, ItemStack output) {
        if (cursor.isEmpty()) return true;
        return ItemStack.isSameItemSameTags(cursor, output)
                && cursor.getCount() + output.getCount() <= cursor.getMaxStackSize();
    }

    private static boolean hasRoomFor(Player player, ItemStack output) {
        Inventory inv = player.getInventory();
        int needed = output.getCount();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) return true;
            if (ItemStack.isSameItemSameTags(slot, output) && slot.getCount() < slot.getMaxStackSize()) {
                needed -= slot.getMaxStackSize() - slot.getCount();
                if (needed <= 0) return true;
            }
        }
        return needed <= 0;
    }


    private static final class CraftingResultSlot extends SlotWidget {

        private final Container grid;
        private final IItemHandlerModifiable toolBay;
        private final Runnable recompute;

        CraftingResultSlot(Container resultView, int x, int y, Container grid, IItemHandlerModifiable toolBay,
                           Runnable recompute) {
            super(resultView, 0, x, y, true, false);
            this.grid = grid;
            this.toolBay = toolBay;
            this.recompute = recompute;
            setBackgroundTexture(GuiTextures.SLOT);
        }

        @Override
        public boolean canPutStack(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack slotClick(int dragType, ClickType clickType, Player player) {
            if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
                return getItem();   // non-null bypasses vanilla; the server drives crafting
            }
            if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE) {
                return getItem();
            }
            boolean craftAll = clickType == ClickType.QUICK_MOVE;
            int iterations = 0;
            int[] hint = null;
            do {
                Match match = CraftingStationCrafter.findMatch(serverPlayer.level(), readGrid(grid), toolBay, hint);
                if (match == null || match.result().isEmpty()) break;
                hint = match.injectedBaySlotForCell();
                ItemStack preview = match.result();
                if (craftAll) {
                    if (!hasRoomFor(serverPlayer, preview)) break;
                } else {
                    if (!canAddToCursor(serverPlayer.containerMenu.getCarried(), preview)) break;
                }

                ItemStack output = CraftingStationCrafter.craftOnce(serverPlayer, match, grid, toolBay);
                output.onCraftedBy(serverPlayer.level(), serverPlayer, output.getCount());

                if (craftAll) {
                    if (!serverPlayer.getInventory().add(output)) {
                        serverPlayer.drop(output, false);
                    }
                } else {
                    ItemStack cursor = serverPlayer.containerMenu.getCarried();
                    if (cursor.isEmpty()) {
                        serverPlayer.containerMenu.setCarried(output);
                    } else {
                        cursor.grow(output.getCount());
                    }
                }
            } while (craftAll && ++iterations < CRAFT_ALL_CAP);

            recompute.run();
            serverPlayer.containerMenu.broadcastChanges();
            return getItem();
        }
    }


    private static final class PlayerDepositSlot extends SlotWidget {

        private final IItemHandlerModifiable store;

        PlayerDepositSlot(Container inv, int index, int x, int y, boolean hotbar, IItemHandlerModifiable store) {
            super(inv, index, x, y, true, true);
            this.store = store;
            setBackgroundTexture(GuiTextures.SLOT);
            setLocationInfo(true, hotbar);
        }

        @Override
        public ItemStack slotClick(int dragType, ClickType clickType, Player player) {
            if (clickType != ClickType.QUICK_MOVE) {
                return null;
            }
            if (player.level().isClientSide) {
                return getItem();
            }
            ItemStack inSlot = getItem();
            if (inSlot.isEmpty()) {
                return getItem();
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(store, inSlot.copy(), false);
            setItem(remainder);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.containerMenu.broadcastChanges();
            }
            return getItem();
        }
    }
}
