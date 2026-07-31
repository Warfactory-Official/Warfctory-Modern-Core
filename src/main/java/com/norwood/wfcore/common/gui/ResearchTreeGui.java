package com.norwood.wfcore.common.gui;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.FluidDrawable;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.screen.UISettings;
import brachy.modularui.utils.Alignment;
import brachy.modularui.utils.Color;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.PageButton;
import brachy.modularui.widgets.PagedWidget;
import brachy.modularui.widgets.RichTextWidget;
import brachy.modularui.widgets.SlotGroupWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import com.norwood.wfcore.api.research.*;
import com.norwood.wfcore.common.gui.widget.PanViewport;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Research-tree GUI on the ModularUI (brachy) fork. Layout:
 * <ul>
 * <li>category tabs across the top (one research tree per {@link Research#getCategory()});</li>
 * <li>the big ~16:10 central panel: a 2D drag/scroll node graph for the active category (click a node to
 * select it);</li>
 * <li>bottom-left detail panel (only shown while a node is selected): name, description, inputs/run, the
 * compute + power cost per run, the unlocked item, a start/cancel button, and a fill bar (tooltip = %);</li>
 * <li>bottom-right queue strip: square slots showing the running/queued researches; click one to drop it.</li>
 * </ul>
 * Per-research progress is banked in the controller's {@link ResearchState}, so the bar and queue keep their
 * progress when the player switches between researches.
 */
public final class ResearchTreeGui {

    // Sized to fit a 1080p screen at GUI scale 3 (640x360 effective) while staying centred clear of the
    // JEI/EMI side panels: ~96px of margin remains on each side at that scale.
    private static final int PANEL_W = 448;
    private static final int PANEL_H = 340;

    /**
     * Gap between sections (and the panel's outer margin).
     */
    private static final int PAD = 4;
    /**
     * Inner content inset within a section. Applied directly to child coordinates rather than via
     * {@code padding()}: in this ModularUI fork padding only offsets relatively-positioned children, so a
     * statically-positioned {@code pos(x, y)}/{@code right(x)} child ignores it and would sit on the edge.
     */
    private static final int INSET = 5;

    // GT-style tabs/title share the 28x32 TAB textures, whose 4px connecting edge overlaps (and merges into)
    // the panel it attaches to.
    private static final int TAB_W = 28;
    private static final int TAB_TEX_H = 32;
    private static final int TAB_INSET = 4;

    private static final int POWER_SIZE = 18;
    private static final int QUEUE_H = 48;
    private static final int QUEUE_SLOT = 24;

    private static final int TITLE_H = 24;
    private static final int TITLE_W = 140;

    private static final int DETAIL_W = 280;
    private static final int DETAIL_H = 120;

    // title: a TAB_TOP-textured nameplate (block icon + name) attached to the top-left of the tree pane
    private static final int TITLE_X = PAD;
    private static final int TITLE_Y = PAD;
    private static final int TREE_Y = TITLE_Y + TITLE_H - TAB_INSET;
    // bottom-left detail panel, anchored to the panel's bottom edge
    private static final int DETAIL_X = PAD;
    private static final int QUEUE_X = DETAIL_X + DETAIL_W + PAD;
    private static final int QUEUE_W = PANEL_W - PAD - QUEUE_X - POWER_SIZE - PAD;
    private static final int SCREEN_X = QUEUE_X;
    private static final int SCREEN_W = PANEL_W - PAD - SCREEN_X;
    private static final int BOTTOM_Y = PANEL_H - PAD - DETAIL_H;
    // category tabs run along the bottom of the tree pane (GT-style PageButtons; TAB_BOTTOM is 28x32)
    private static final int TAB_Y = BOTTOM_Y - 2 - TAB_TEX_H;
    private static final int TREE_H = TAB_Y + TAB_INSET - TREE_Y;
    private static final int QUEUE_Y = TREE_Y + TREE_H + PAD;
    // two stacked 18px buttons in the right column: the working toggle, then the library opener below it
    private static final int WORKING_Y = QUEUE_Y + 4;
    private static final int LIBRARY_Y = WORKING_Y + POWER_SIZE + 2;
    private static final int SCREEN_Y = QUEUE_Y + QUEUE_H + PAD;
    private static final int SCREEN_H = PANEL_H - PAD - SCREEN_Y;
    // tree pane: from just under the attached title (its inset overlaps the title) down to where tabs connect
    private static final int TREE_X = PAD;
    private static final int TREE_W = PANEL_W - 2 * PAD;
    // right column: a shortened queue with the GT power button to its right, status screen filling below them
    private static final int POWER_X = PANEL_W - PAD - POWER_SIZE;
    private static final int NODE = 26;
    private static final int COL_SPACING = 46;
    private static final int ROW_SPACING = 40;
    /**
     * Half the empty gap between adjacent columns / rows; connector legs route through these gutters.
     */
    private static final int GUTTER = (COL_SPACING - NODE) / 2;
    private static final int ROWGUT = (ROW_SPACING - NODE) / 2;
    private static final int MARGIN = 12;
    private static final int BAR_SEGMENTS = 20;
    private static final int ARROW_LEN = 5;

    private static final int COLOR_BORDER = 0xFF101010;
    private static final int COLOR_PANEL = 0xFF202024;
    private static final int COLOR_SLOT = 0xFF101014;
    private static final int COLOR_BAR_BG = 0xFF0A0A0A;
    private static final int COLOR_BAR_FILL = 0xFF44A050;
    private static final int COLOR_LOCKED = 0xFF555555;
    private static final int COLOR_AVAILABLE = 0xFF2F6BD8;
    private static final int COLOR_QUEUED = 0xFFB07818;
    private static final int COLOR_ACTIVE = 0xFFE0A020;
    private static final int COLOR_COMPLETE = 0xFF44A050;
    private static final int COLOR_BUTTON_DISABLED = 0xFF3A3A40;
    private static final int COLOR_BUTTON_SHADE = 0x55000000;
    private static final int COLOR_NODE_HOVER = 0x60FFFFFF;

    // Floating library window: kept in the same dark family as the main GUI, with the two actions colour-coded -
    // green "read" (import a stick's blueprints into the unit) vs. blue "write" (imprint the unit's onto a stick).
    private static final int COLOR_LIB_WELL = 0xFF08080C;      // recessed list background
    private static final int COLOR_LIB_ROW_HOVER = 0x33FFFFFF; // translucent brighten over an un-selected row
    private static final int COLOR_LIB_READ = COLOR_COMPLETE;  // green: item -> unit
    private static final int COLOR_LIB_WRITE = COLOR_AVAILABLE; // blue: unit -> item
    private static final int COLOR_HEADER = 0xFFFFD37A;        // warm title text
    private static final int COLOR_SUBTEXT = 0xFF9A9AA4;       // muted section-label / hint text

    // GTCEu's power on/off button texture (18x36 vertical atlas: top frame = off, bottom = on/lit)
    private static final UITexture POWER_TEX = UITexture.builder()
            .location("gtceu:gui/widget/button_power").imageSize(18, 36).build();

    // client-side selection (one GUI per client)
    private static final String[] SELECTED = { null };
    // client-side selection of the library window's write target (mirrors SELECTED's pattern)
    private static final String[] LIBRARY_SELECTED = { null };
    // data-orb icon flagging a library row whose research is stored on a connected Data Bank
    private static final ItemStack DATA_BANK_ICON = new ItemStack(GTItems.TOOL_DATA_ORB.asItem());

    private ResearchTreeGui() {}

    public static ModularPanel<?> build(ResearchUnitMachine mte, PosGuiData data,
                                        PanelSyncManager syncManager, UISettings settings) {
        // Unformed (or a passive SLAVE) has no research tree to present; hand back a compact, self-explanatory
        // panel instead of the full-size invisible one, which read as a giant empty square.
        if (!mte.isFormed() || mte.getMode() != ResearchUnitMachine.Mode.CONTROL) {
            return buildInfoPanel(mte);
        }

        ModularPanel<?> panel = ModularPanel.defaultPanel("research_unit", PANEL_W, PANEL_H);
        panel.invisible();
        // Tree first so the title (added next) draws over the tree's top-left edge and merges into it.
        buildTabsAndTree(panel, mte, syncManager);
        panel.child(buildTitle(mte));
        panel.child(buildWorkingButton(mte, syncManager));
        panel.child(buildLibraryButton(mte, syncManager));
        panel.child(buildScreen(mte));
        panel.child(buildDetail(mte, syncManager));
        panel.child(buildQueue(mte, syncManager));
        return panel;
    }

    /**
     * The screen shown when the controller can't present its research tree: either the 3x3x3 multiblock isn't
     * assembled — in which case it names the special parts the pattern needs and tells the player how to
     * preview it in-world — or the unit is a passive SLAVE lending a slot to a nearby Control. Replaces the old
     * one-line notice that floated in the corner of a full-size blank panel.
     */
    private static ModularPanel<?> buildInfoPanel(ResearchUnitMachine mte) {
        boolean slave = mte.isFormed(); // only reached formed when the unit is a SLAVE
        int w = 198;
        int h = slave ? 104 : 186;
        ModularPanel<?> panel = ModularPanel.defaultPanel("research_unit", w, h);

        // header: the controller's own block icon + display name, with a hairline divider beneath
        ItemStack block = mte.getDefinition().asStack();
        panel.child(itemIcon(() -> block, 16).pos(8, 7).name("info_icon"));
        panel.child(new TextWidget<>(Text.of(block.getHoverName())).pos(28, 10).name("info_title"));
        panel.child(new ParentWidget<>().background(new Rectangle().color(COLOR_BORDER))
                .pos(7, 27).size(w - 14, 1).name("info_divider"));

        // status heading — red "structure incomplete", or gold "slave mode"
        panel.child(new TextWidget<>(Text.of(Component.translatable(slave ?
                "wfcore.gui.research.info_slave_title" : "wfcore.gui.research.info_unformed_title")
                .withStyle(slave ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED)))
                .pos(8, 33).name("info_heading"));

        // wrapped body: why the tree is unavailable and what to do about it
        RichTextWidget body = new RichTextWidget();
        body.name("info_body");
        body.pos(8, 45).size(w - 16, h - 51);
        body.autoUpdate(true);
        body.textBuilder(rt -> {
            if (slave) {
                rt.addLine(gray("wfcore.gui.research.info_slave_desc"));
                rt.newLine();
                rt.addLine(Component.translatable("wfcore.gui.research.info_slave_hint")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            } else {
                rt.addLine(gray("wfcore.gui.research.info_unformed_desc"));
                rt.newLine();
                rt.addLine(Component.translatable("wfcore.gui.research.info_needs")
                        .withStyle(net.minecraft.ChatFormatting.WHITE));
                rt.addLine(gray("wfcore.gui.research.info_part_energy"));
                rt.addLine(gray("wfcore.gui.research.info_part_items"));
                rt.addLine(gray("wfcore.gui.research.info_part_computation"));
                rt.addLine(gray("wfcore.gui.research.info_part_data"));
                rt.newLine();
                rt.addLine(Component.translatable("wfcore.gui.research.info_hint_preview")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        });
        panel.child(body);
        return panel;
    }

    private static Component gray(String langKey) {
        return Component.translatable(langKey).withStyle(net.minecraft.ChatFormatting.GRAY);
    }

    //////////////////// title nameplate (attached tab) ////////////////////

    /**
     * The GT-multiblock-style title: a nameplate showing the controller's block model and display name, drawn
     * with the {@link GuiTextures#TAB_TOP} tab texture so it reads as a tab attached to the tree pane below it
     * (added after the tree in {@link #build}, its connecting inset overlaps and merges into the pane's edge).
     */
    private static ParentWidget<?> buildTitle(ResearchUnitMachine mte) {
        ParentWidget<?> title = new ParentWidget<>();
        title.name("title");
        title.pos(TITLE_X, TITLE_Y).size(TITLE_W, TITLE_H);
        title.background(titleTabBackground());

        ItemStack block = mte.getDefinition().asStack();
        title.child(itemIcon(() -> block, 16).pos(INSET, 3).name("title_icon"));
        title.child(new TextWidget<>(Text.of(block.getHoverName()))
                .pos(INSET + 20, 6).name("title_name"));
        return title;
    }

    /**
     * Draws a single active TAB_TOP tab as the title bar by slicing one piece horizontally — crisp left/right
     * caps with the centre stretched — so it reads as one seamless rounded tab (the three atlas pieces are
     * separate bordered tabs, so composing start+middle+end would show dividers).
     */
    private static IDrawable titleTabBackground() {
        UITexture tab = GuiTextures.TAB_TOP.getMiddle(true);
        UITexture left = tab.getSubArea(0f, 0f, 0.25f, 1f);
        UITexture mid = tab.getSubArea(0.25f, 0f, 0.75f, 1f);
        UITexture right = tab.getSubArea(0.75f, 0f, 1f, 1f);
        int cap = 7;
        return (ctx, x, y, w, h, theme) -> {
            left.draw(ctx, x, y, cap, h, theme);
            mid.draw(ctx, x + cap, y, w - 2 * cap, h, theme);
            right.draw(ctx, x + w - cap, y, cap, h, theme);
        };
    }

    /**
     * GT-style power button to the right of the queue: lit while working is enabled, dim while paused.
     */
    private static ButtonWidget<?> buildWorkingButton(ResearchUnitMachine mte, PanelSyncManager sync) {
        InteractionSyncHandler toggle = new InteractionSyncHandler()
                .setOnMousePressed(d -> mte.toggleWorkingEnabled());
        sync.syncValue("working_enabled", 0, toggle);

        UITexture on = POWER_TEX.getSubArea(0f, 0.5f, 1f, 1f);
        UITexture off = POWER_TEX.getSubArea(0f, 0f, 1f, 0.5f);

        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("working_toggle");
        button.pos(POWER_X, WORKING_Y).size(POWER_SIZE, POWER_SIZE).syncHandler("working_enabled", 0);
        button.background((ctx, x, y, w, h, theme) -> (mte.isWorkingEnabled() ? on : off).draw(ctx, x, y, w, h, theme));

        button.tooltipDynamic(t -> t.addLine(Text.lang(mte.isWorkingEnabled() ?
                "wfcore.gui.research.working_enabled" : "wfcore.gui.research.working_disabled")))
                .tooltipAutoUpdate(true);
        return button;
    }

    //////////////////// library window (read/write blueprints between the unit and a data item) ////////////////////

    /**
     * The button below the working toggle that opens the draggable library window: a list of every research the
     * player has obtained, a slot for a data item/paper, and two actions on that item - Read imports the item's
     * blueprints into this unit's database, Write imprints the picked blueprint onto the item.
     */
    private static ButtonWidget<?> buildLibraryButton(ResearchUnitMachine mte, PanelSyncManager sync) {
        IPanelHandler library = sync.syncedPanel("research_library", true,
                (mgr, handler) -> buildLibraryPanel(mte, mgr));

        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("library_button");
        button.pos(POWER_X, LIBRARY_Y).size(POWER_SIZE, POWER_SIZE);
        button.background(mcBackground(() -> COLOR_PANEL));
        button.overlay(new ItemDrawable(new ItemStack(GTItems.TOOL_DATA_ORB.asItem())).asIcon().size(14));
        button.onMousePressed((context, btn) -> {
            library.openPanel();
            return true;
        });
        button.tooltipDynamic(t -> t.addLine(Text.lang("wfcore.gui.research.library"))).tooltipAutoUpdate(true);
        return button;
    }

    private static ModularPanel<?> buildLibraryPanel(ResearchUnitMachine mte, PanelSyncManager sync) {
        int w = 214;
        int pad = 6;
        int wellY = 35;
        int footerY = 176;                 // footer divider (fixed, so the player inventory can sit below it)
        int slotRowY = footerY + 8;        // data-item slot + Read/Write buttons
        int invW = 9 * 18;                 // player inventory block: 3 rows + hotbar, 162x76
        int invX = (w - invW) / 2;         // centred horizontally
        int invY = slotRowY + 18 + 10;     // 10px gap under the button row
        int h = invY + 76 + pad;           // room for the inventory + bottom margin
        int wellH = footerY - wellY - 4;
        ModularPanel<?> panel = ModularPanel.defaultPanel("research_library", w, h);
        panel.draggable(true);

        // header: warm title, a working close button (panelCloseButton closes its own panel), hairline divider
        panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.library")).color(COLOR_HEADER)
                .pos(8, 7).name("library_title"));
        panel.child(ButtonWidget.panelCloseButton());
        panel.child(hairline(pad, 19, w - 2 * pad).name("library_header_divider"));
        panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.library_obtained")).color(COLOR_SUBTEXT)
                .scale(0.9f).pos(8, 24).name("library_subheader"));

        // recessed well: one row per obtained (fully-unlocked) blueprint, or a centred hint when there are none
        ParentWidget<?> well = new ParentWidget<>();
        well.name("library_well");
        well.pos(pad, wellY).size(w - 2 * pad, wellH);
        well.background(framedBackground(COLOR_LIB_WELL, COLOR_BORDER));

        ListWidget<IWidget, ?> list = libraryList();
        list.name("library_list");
        list.pos(2, 2).size(w - 2 * pad - 4, wellH - 4);
        list.scrollDirection(GuiAxis.Y);
        list.collapseDisabledChildren(true);
        List<IWidget> rows = new ArrayList<>();
        int index = 0;
        for (Research research : ResearchRegistry.all()) {
            rows.add(buildLibraryRow(mte, research, index++, sync, w - 2 * pad - 10));
        }
        list.children(rows);
        well.child(list);
        well.child(new TextWidget<>(Text.lang("wfcore.gui.research.library_empty")).color(COLOR_SUBTEXT)
                .alignment(Alignment.Center).pos(2, 2).size(w - 2 * pad - 4, wellH - 4)
                .name("library_empty").setEnabledIf(x -> !hasObtainedBlueprints(mte)));
        panel.child(well);

        // footer: divider, the data-item slot, then the colour-coded Read (import) / Write (export) actions
        panel.child(hairline(pad, footerY, w - 2 * pad).name("library_footer_divider"));

        ModularSlot slot = new ModularSlot(mte.getLibraryInv().storage, 0)
                .filter(ResearchDataItem::isDataItem).accessibility(true, true);
        sync.syncValue("library_slot", 0, new ItemSlotSyncHandler(slot));
        ItemSlot slotWidget = new ItemSlot();
        slotWidget.pos(8, slotRowY).size(18, 18);
        slotWidget.syncHandler("library_slot", 0);
        panel.child(slotWidget);

        int btnX = 30;
        int btnW = (w - pad - btnX - 4) / 2;
        panel.child(libraryActionButton(sync, "library_read", COLOR_LIB_READ, "wfcore.gui.research.read_label",
                "wfcore.gui.research.read", "wfcore.gui.research.read_hint", () -> mte.readLibrary(),
                btnX, slotRowY, btnW));
        panel.child(libraryActionButton(sync, "library_write", COLOR_LIB_WRITE, "wfcore.gui.research.write_label",
                "wfcore.gui.research.write", "wfcore.gui.research.write_hint", () -> mte.writeLibrary(),
                btnX + btnW + 4, slotRowY, btnW));

        // Player inventory, present only while this library window is open (it lives on this panel). Lets the
        // player move data sticks/orbs between their inventory and the library slot without closing the GUI.
        panel.child(hairline(pad, invY - 6, w - 2 * pad).name("library_inv_divider"));
        panel.child(SlotGroupWidget.playerInventory(false).pos(invX, invY).name("library_player_inv"));
        return panel;
    }

    /** A colour-coded, hover-lit footer action button that fires {@code action} server-side when pressed. */
    private static ButtonWidget<?> libraryActionButton(PanelSyncManager sync, String syncKey, int baseColor,
                                                       String labelKey, String titleKey, String hintKey,
                                                       Runnable action, int x, int y, int bw) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(d -> action.run());
        sync.syncValue(syncKey, 0, handler);

        ButtonWidget<?> button = new ButtonWidget<>();
        button.name(syncKey);
        button.pos(x, y).size(bw, 18).syncHandler(syncKey, 0);
        button.background(libraryButtonBackground(button, baseColor));
        button.child(new TextWidget<>(Text.lang(labelKey)).alignment(Alignment.Center).color(0xFFFFFFFF)
                .pos(0, 0).size(bw, 18).name(syncKey + "_label"));
        button.tooltipDynamic(t -> {
            t.titleMargin();
            t.addLine(Text.lang(titleKey));
            t.addLine(Text.of(Component.translatable(hintKey).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }).tooltipAutoUpdate(true);
        return button;
    }

    /** Self-typed generics don't infer cleanly against a wildcard target, so build the list raw and widen here. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static ListWidget<IWidget, ?> libraryList() {
        return new ListWidget();
    }

    /** One obtained-research row: click to pick it as the write target (only fully-unlocked researches show). */
    private static IWidget buildLibraryRow(ResearchUnitMachine mte, Research research, int index,
                                           PanelSyncManager sync, int rowW) {
        final String rid = research.getId();
        InteractionSyncHandler select = new InteractionSyncHandler().setOnMousePressed(d -> mte.selectLibrary(rid));
        sync.syncValue("library_select", index, select);

        ButtonWidget<?> row = new ButtonWidget<>();
        row.name("library_row_" + rid);
        row.size(rowW, 18);
        row.setEnabledIf(w -> mte.getResearchState().isPathComplete(rid));
        row.background(libraryRowBackground(row, rid));
        // ButtonWidget is single-child (a second .child() disposes the first), so pack the icon + name into one
        // content parent - otherwise only the name rendered and the research icon was silently dropped.
        ParentWidget<?> content = new ParentWidget<>();
        content.name("library_row_content_" + rid);
        content.pos(0, 0).size(rowW, 18);
        content.child(itemIcon(research::getIcon, 16).pos(3, 1).name("library_icon_" + rid));
        content.child(new TextWidget<>(Text.of(Component.translatable(research.getNameKey()))).color(0xFFE8E8E8)
                .pos(23, 5).name("library_name_" + rid));
        // right-aligned data-orb badge: shown only while this research is stored on a wired Data Bank
        ParentWidget<?> bankBadge = itemIcon(() -> DATA_BANK_ICON, 12).pos(rowW - 15, 3).name("library_bank_" + rid);
        bankBadge.setEnabledIf(w -> mte.isInDataBank(rid));
        bankBadge.tooltipDynamic(t -> t.addLine(Text.lang("wfcore.gui.research.in_data_bank"))).tooltipAutoUpdate(true);
        content.child(bankBadge);
        row.child(content);
        row.onMousePressed((context, btn) -> {
            LIBRARY_SELECTED[0] = rid;
            return false;
        });
        row.syncHandler("library_select", index);
        return row;
    }

    private static IDrawable libraryRowBackground(IWidget row, String rid) {
        Rectangle selected = new Rectangle().color(COLOR_AVAILABLE);
        Rectangle base = new Rectangle().color(COLOR_SLOT);
        Rectangle hover = new Rectangle().color(COLOR_LIB_ROW_HOVER);
        return (ctx, x, y, w, h, theme) -> {
            boolean sel = rid.equals(LIBRARY_SELECTED[0]);
            (sel ? selected : base).draw(ctx, x, y, w, h, theme);
            if (!sel && row.isHovering()) hover.draw(ctx, x, y, w, h, theme);
        };
    }

    /** A 1px horizontal divider line in the panel's border colour. */
    private static ParentWidget<?> hairline(int x, int y, int w) {
        return new ParentWidget<>().background(new Rectangle().color(COLOR_BORDER)).pos(x, y).size(w, 1);
    }

    /** A flat fill with a 1px inner border, the library window's panel/well style. */
    private static IDrawable framedBackground(int fill, int border) {
        Rectangle bg = new Rectangle().color(fill);
        Rectangle line = new Rectangle().color(border).hollow(1f);
        return (ctx, x, y, w, h, theme) -> {
            bg.draw(ctx, x, y, w, h, theme);
            line.draw(ctx, x, y, w, h, theme);
        };
    }

    /** A flat action-button fill that brightens on hover, with a hairline border. */
    private static IDrawable libraryButtonBackground(IWidget widget, int base) {
        Rectangle fill = new Rectangle().color(base);
        Rectangle hover = new Rectangle().color(brighten(base, 28));
        Rectangle border = new Rectangle().color(COLOR_BORDER).hollow(1f);
        return (ctx, x, y, w, h, theme) -> {
            (widget.isHovering() ? hover : fill).draw(ctx, x, y, w, h, theme);
            border.draw(ctx, x, y, w, h, theme);
        };
    }

    /** Returns the connector colour at ~40% of its original alpha, used to render anyOf (soft) edges. */
    private static int dimConnector(int argb) {
        int a = (int) (((argb >>> 24) & 0xFF) * 0.4f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Lightens each RGB channel of an ARGB colour by {@code amount} (clamped), keeping alpha. */
    private static int brighten(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + amount);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + amount);
        int b = Math.min(255, (argb & 0xFF) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** True if the player has at least one fully-unlocked blueprint to show in the library list. */
    private static boolean hasObtainedBlueprints(ResearchUnitMachine mte) {
        ResearchState state = mte.getResearchState();
        for (Research research : ResearchRegistry.all()) {
            if (state.isPathComplete(research.getId())) return true;
        }
        return false;
    }

    /// ///////////////// tabs + per-category trees ////////////////////

    private static void buildTabsAndTree(ModularPanel<?> panel, ResearchUnitMachine mte, PanelSyncManager sync) {
        List<ResearchCategory> categories = categories();
        int[] nodeCounter = { 0 };

        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> paged = new PagedWidget<>();
        paged.name("tree_paged");
        paged.background(GuiTextures.MC_BACKGROUND);
        paged.controller(controller);
        paged.pos(TREE_X, TREE_Y).size(TREE_W, TREE_H);

        // Each page is a fixed-size parent the size of the paged area; the canvas is inset by INSET on every
        // side so the scroll viewport sits inside a clean frame and never spills onto the tabs/panels (the old
        // coverChildren + padding wrapper grew the page past the paged area, which caused the overlap).
        int viewW = TREE_W - 2 * INSET;
        int viewH = TREE_H - 2 * INSET;
        for (ResearchCategory category : categories) {
            paged.addPage(new ParentWidget<>()
                    .name("tree_page_" + category.getId())
                    .size(TREE_W, TREE_H)
                    .child(buildCategoryCanvas(mte, category, sync, nodeCounter, viewW, viewH)));
        }
        paged.initialPage(0);
        panel.child(paged);

        int n = categories.size();
        for (int i = 0; i < n; i++) {
            int location = n == 1 ? 0 : i == 0 ? -1 : i == n - 1 ? 1 : 0;
            panel.child(buildTab(controller, categories.get(i), i, location));
        }
    }

    /**
     * A GT-multiblock-style tab that switches the active page. {@link PageButton} draws the connected
     * {@link GuiTextures#TAB_BOTTOM} texture (the active/inactive swap is driven by the shared controller) and
     * handles the page switch itself; {@code location} is the strip position: -1 start cap, 0 middle, +1 end.
     */
    private static PageButton buildTab(PagedWidget.Controller controller, ResearchCategory category,
                                       int index, int location) {
        PageButton tab = new PageButton(index, controller);
        tab.name("tab_" + category.getId());
        tab.tab(GuiTextures.TAB_BOTTOM, location);          // sets the 28x32 size + active/inactive backgrounds
        tab.pos(TREE_X + index * TAB_W, TAB_Y);             // flush, so the start/middle/end caps form one strip
        tab.overlay(new ItemDrawable(tabIcon(category)).asIcon().size(16));
        tab.tooltipBuilder(t -> t.addLine(Text.lang(category.getNameKey())));
        return tab;
    }

    private static PanViewport<?> buildCategoryCanvas(ResearchUnitMachine mte, ResearchCategory category,
                                                      PanelSyncManager sync, int[] nodeCounter,
                                                      int viewW, int viewH) {
        String id = category.getId();
        List<Research> nodes = ResearchRegistry.byCategory(id);
        Map<String, int[]> layout = ResearchLayout.compute(nodes);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] cell : layout.values()) {
            minX = Math.min(minX, cell[0]);
            minY = Math.min(minY, cell[1]);
            maxX = Math.max(maxX, cell[0]);
            maxY = Math.max(maxY, cell[1]);
        }
        if (nodes.isEmpty()) {
            minX = minY = maxX = maxY = 0;
        }
        final int ox = minX, oy = minY;

        // Occupied grid cells, so a connector can run straight through a gutter-free span instead of detouring
        // (a same-row hop only humps up-and-over when a node actually sits between the two).
        Set<String> occupied = new HashSet<>();
        for (int[] cell : layout.values()) occupied.add(cell[0] + "," + cell[1]);

        PanViewport<?> canvas = new PanViewport<>();
        canvas.name("tree_canvas_" + id);
        canvas.pos(INSET, INSET).size(viewW, viewH);
        canvas.background(categoryBackground(category));
        canvas.contentSize(MARGIN * 2 + (maxX - minX + 1) * COL_SPACING,
                MARGIN * 2 + (maxY - minY + 1) * ROW_SPACING);

        // connectors are added before the nodes, so the nodes (opaque tiles) always draw over them
        int connectorColor = category.getConnectorColor();
        // anyOf connectors share the hue but at ~40% alpha so they're visually softer than hard prerequisites
        int anyOfColor = dimConnector(connectorColor);
        for (Research research : nodes) {
            int[] to = layout.get(research.getId());
            for (String prereqId : research.getPrerequisites()) {
                Research prereq = ResearchRegistry.get(prereqId);
                if (prereq != null && prereq.getCategory().equals(id)) {
                    int[] from = layout.get(prereq.getId());
                    addConnector(canvas, from[0], from[1], to[0], to[1], ox, oy, connectorColor, occupied);
                }
            }
            for (List<String> group : research.getAnyOfGroups()) {
                for (String anyId : group) {
                    Research any = ResearchRegistry.get(anyId);
                    if (any != null && any.getCategory().equals(id)) {
                        int[] from = layout.get(any.getId());
                        addConnector(canvas, from[0], from[1], to[0], to[1], ox, oy, anyOfColor, occupied);
                    }
                }
            }
        }
        for (Research research : nodes) {
            int[] cell = layout.get(research.getId());
            canvas.child(buildNode(mte, research, nodeCounter[0]++, cell[0], cell[1], ox, oy, sync));
        }
        return canvas;
    }

    /**
     * The themed canvas background: a tiled texture, else a solid colour, else the default stone tiles.
     */
    private static IDrawable categoryBackground(ResearchCategory category) {
        if (category.getBackgroundTexture() != null) {
            return UITexture.builder()
                    .location(category.getBackgroundTexture().toString())
                    .imageSize(16, 16)
                    .tiled()
                    .build();
        }
        if (category.getBackgroundColor() != 0) {
            return new Rectangle().color(category.getBackgroundColor());
        }
        return UITexture.builder()
                .location("minecraft:gui/advancements/backgrounds/stone")
                .imageSize(16, 16)
                .tiled()
                .build();
    }

    private static ItemStack tabIcon(ResearchCategory category) {
        if (!category.getIcon().isEmpty()) return category.getIcon();
        List<Research> nodes = ResearchRegistry.byCategory(category.getId());
        if (!nodes.isEmpty() && !nodes.get(0).getIcon().isEmpty()) return nodes.get(0).getIcon();
        return new ItemStack(Items.BOOK);
    }

    private static ButtonWidget<?> buildNode(ResearchUnitMachine mte, Research research, int index, int col, int row,
                                             int ox, int oy, PanelSyncManager sync) {
        final String rid = research.getId();

        InteractionSyncHandler select = new InteractionSyncHandler().setOnMousePressed(d -> mte.setSelected(rid));
        sync.syncValue("research_node", index, select);

        ButtonWidget<?> node = new ButtonWidget<>();
        node.name("node_" + rid);
        node.pos(nodeX(col, ox), nodeY(row, oy)).size(NODE, NODE);
        // every node gets the opaque status tile so it always occludes the connectors routed behind it
        node.background(nodeBackground(mte, research, node));
        if (!research.getIcon().isEmpty()) {
            node.overlay(new ItemDrawable(research.getIcon()).asIcon().size(16));
        }
        node.tooltipDynamic(t -> {
            t.titleMargin();
            t.addLine(Text.lang(research.getNameKey()));
            t.addLine(Text.lang(research.getDescKey()));
            t.spaceLine(2);
            t.addLine(Text.lang("wfcore.gui.research.runs", research.getRunsRequired()));
            t.addLine(Text.lang("wfcore.gui.research.cwu_per_run", research.getCwuPerRun()));
            t.addLine(Text.str(statusLine(mte, rid)));
            if (statusOf(mte, rid) == NodeStatus.LOCKED &&
                    (!research.getPrerequisites().isEmpty() || !research.getAnyOfGroups().isEmpty())) {
                t.spaceLine(2);
                t.addLine(Text.lang("wfcore.gui.research.blocker_locked"));
                appendUnmetPrereqs(t, mte, research);
            }
        }).tooltipAutoUpdate(true);
        node.onMousePressed((context, button) -> {
            SELECTED[0] = rid;
            return false; // let the sync handler fire the server-side selection
        });
        node.syncHandler("research_node", index);
        return node;
    }

    /**
     * Routes a prerequisite → research connector as orthogonal legs that stay in the empty gutters between
     * node cells, so it never runs over a node tile: vertical risers sit in the column gutter beside a node,
     * and a connector spanning intermediate columns detours its long horizontal run through a row gutter. The
     * arrowhead always points into the child's near edge.
     */
    private static void addConnector(ParentWidget<?> canvas, int fromCol, int fromRow, int toCol, int toRow,
                                     int ox, int oy, int color, Set<String> occupied) {
        int py = nodeY(fromRow, oy) + NODE / 2;
        int cy = nodeY(toRow, oy) + NODE / 2;

        // Same column: a straight vertical run into the child's near (top/bottom) edge.
        if (fromCol == toCol) {
            int x = nodeX(fromCol, ox) + NODE / 2;
            int dir = cy >= py ? 1 : -1;
            int tipY = dir > 0 ? nodeY(toRow, oy) - 1 : nodeY(toRow, oy) + NODE;
            verticalLeg(canvas, x, py + dir * (NODE / 2), tipY, color);
            addArrowhead(canvas, x, tipY, 0, dir, color);
            return;
        }

        boolean rightward = toCol > fromCol;
        int exitX = rightward ? nodeX(fromCol, ox) + NODE : nodeX(fromCol, ox);
        int tipX = rightward ? nodeX(toCol, ox) - 1 : nodeX(toCol, ox) + NODE;
        int riser1 = rightward ? exitX + GUTTER : exitX - GUTTER; // gutter just past the parent

        if (Math.abs(toCol - fromCol) == 1) {
            // Adjacent columns: nothing can sit between them, so one riser in the shared gutter is clean.
            if (py == cy) {
                horizontalLeg(canvas, exitX, tipX, cy, color);
            } else {
                horizontalLeg(canvas, exitX, riser1, py, color);
                verticalLeg(canvas, riser1, py, cy, color);
                horizontalLeg(canvas, riser1, tipX, cy, color);
            }
        } else if (py == cy && rowSpanClear(occupied, fromCol, toCol, fromRow)) {
            // Same row with nothing sitting between the two: a single straight leg reads cleanest (no hump).
            horizontalLeg(canvas, exitX, tipX, cy, color);
        } else {
            // Spans intermediate columns: detour the long run through the row gutter beside the child's row,
            // so it crosses those columns only where no node sits.
            int riser2 = rightward ? nodeX(toCol, ox) - GUTTER : nodeX(toCol, ox) + NODE + GUTTER;
            int channelY = cy >= py ? nodeY(toRow, oy) - ROWGUT : nodeY(toRow, oy) + NODE + ROWGUT;
            horizontalLeg(canvas, exitX, riser1, py, color);
            verticalLeg(canvas, riser1, py, channelY, color);
            horizontalLeg(canvas, riser1, riser2, channelY, color);
            verticalLeg(canvas, riser2, channelY, cy, color);
            horizontalLeg(canvas, riser2, tipX, cy, color);
        }
        addArrowhead(canvas, tipX, cy, rightward ? 1 : -1, 0, color);
    }

    /** True if no node occupies a cell strictly between {@code fromCol} and {@code toCol} on {@code row}. */
    private static boolean rowSpanClear(Set<String> occupied, int fromCol, int toCol, int row) {
        int lo = Math.min(fromCol, toCol), hi = Math.max(fromCol, toCol);
        for (int c = lo + 1; c < hi; c++) {
            if (occupied.contains(c + "," + row)) return false;
        }
        return true;
    }

    private static void verticalLeg(ParentWidget<?> canvas, int x, int y1, int y2, int color) {
        canvas.child(line(x - 1, Math.min(y1, y2), 2, Math.abs(y2 - y1), color));
    }

    private static void horizontalLeg(ParentWidget<?> canvas, int x1, int x2, int y, int color) {
        canvas.child(line(Math.min(x1, x2), y - 1, Math.abs(x2 - x1), 2, color));
    }

    /**
     * A filled triangular arrowhead built from 1px slices, tip at (tipX,tipY) pointing along (dx,dy).
     */
    private static void addArrowhead(ParentWidget<?> canvas, int tipX, int tipY, int dx, int dy, int color) {
        for (int half = ARROW_LEN - 1; half >= 0; half--) {
            if (dx != 0) {
                canvas.child(line(tipX - dx * half, tipY - half, 1, 2 * half + 1, color));
            } else {
                canvas.child(line(tipX - half, tipY - dy * half, 2 * half + 1, 1, color));
            }
        }
    }

    private static ParentWidget<?> line(int x, int y, int w, int h, int color) {
        return new ParentWidget<>().name("tree_line").background(new Rectangle().color(color)).pos(x, y)
                .size(Math.max(1, w), Math.max(1, h));
    }

    /// ///////////////// bottom-left detail panel ////////////////////

    private static ParentWidget<?> buildDetail(ResearchUnitMachine mte, PanelSyncManager sync) {
        int innerW = DETAIL_W - 2 * INSET;
        ParentWidget<?> detail = new ParentWidget<>();
        detail.name("detail");
        detail.pos(DETAIL_X, BOTTOM_Y).size(DETAIL_W, DETAIL_H);
        detail.background(GuiTextures.MC_BACKGROUND);
        // Only visible while a research node is selected.
        detail.setEnabledIf(w -> selected() != null);

        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() : Component.translatable(r.getNameKey());
        })).pos(INSET, 3).name("detail_name"));

        RichTextWidget description = new RichTextWidget();
        description.name("detail_description");
        description.pos(INSET, 13).size(innerW, 30);
        description.autoUpdate(true);
        description.textBuilder(rt -> {
            Research r = selected();
            if (r != null) rt.add(Component.translatable(r.getDescKey()).withStyle(net.minecraft.ChatFormatting.GRAY));
        });
        detail.child(description);

        // per-run inputs: up to 4 item costs, then up to 3 fluid costs on the same row (before the unlocks column)
        addItemRow(detail, INSET, 46, ResearchTreeGui::inputPerRunAt, 4, "input_item");
        addFluidRow(detail, INSET + 4 * 17, 46, 3);

        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() :
                    Component.translatable("wfcore.gui.research.steps", r.getRunsRequired());
        })).pos(INSET, 64).name("detail_steps"))
                        .tooltip( richTooltip -> {
                           richTooltip.addLine(Text.dynamic(() -> {
                               Research r = selected();
                               int  time = r == null ? 0 : r.getTicksPerRun() / 20;
                               return Component.nullToEmpty(time + " seconds per run");
                           }));
                            richTooltip.addLine(Text.dynamic(() -> {
                                Research r = selected();
                                int  time = r == null ? 0 : (r.getTicksPerRun() / 20) * r.getRunsRequired();
                                return Component.nullToEmpty(time + " seconds per run");
                            }));
                        });

        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() :
                    Component.translatable("wfcore.gui.research.cost_per_run", r.getCwuPerRun()/r.getTicksPerRun(), r.getEut());
        })).pos(INSET, 76).name("detail_cost"));

        detail.child(buildActionButton(mte, sync));

        detail.child(
                new TextWidget<>(Text.lang("wfcore.gui.research.unlocks")).pos(INSET + 140, 46).name("unlocks_label"));
        addItemRow(detail, INSET + 140, 57, ResearchTreeGui::unlockedAt, 4, "unlock_item");

        addProgressBar(detail, mte, INSET, 110, innerW, 6);
        return detail;
    }

    /**
     * The start/cancel button. While the selected research is queued it reads "Cancel" and stays active; once a
     * research is complete (or otherwise can't be started) the button is greyed-out and pressed-in, with a
     * tooltip naming the blocker(s).
     */
    private static ButtonWidget<?> buildActionButton(ResearchUnitMachine mte, PanelSyncManager sync) {
        InteractionSyncHandler action = new InteractionSyncHandler().setOnMousePressed(d -> mte.toggleSelected());
        sync.syncValue("research_action", 0, action);

        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("action_button");
        button.pos(INSET, 90).size(108, 16).syncHandler("research_action", 0);
        button.background(new Rectangle().color(COLOR_BUTTON_DISABLED));

        ParentWidget<?> activeFill = new ParentWidget<>();
        activeFill.name("action_active").background(new Rectangle().color(COLOR_AVAILABLE)).pos(0, 0).size(108, 16);
        activeFill.setEnabledIf(w -> canAct(mte));
        button.child(activeFill);

        ParentWidget<?> pressedShade = new ParentWidget<>();
        pressedShade.name("action_shade").background(new Rectangle().color(COLOR_BUTTON_SHADE)).pos(0, 0).size(108, 16);
        pressedShade.setEnabledIf(w -> !canAct(mte));
        button.child(pressedShade);

        button.backgroundOverlay(new Rectangle().color(COLOR_BORDER).hollow(1f));
        button.child(new TextWidget<>(Text.dynamic(() -> actionLabel(mte))).pos(6, 4).name("action_label"));
        button.tooltipDynamic(t -> {
            Research r = selected();
            if (r == null) return;
            t.titleMargin();
            switch (startState(mte)) {
                case CANCEL -> {
                    t.addLine(Text.lang("wfcore.gui.research.cancel"));
                    t.addLine(Text.lang("wfcore.gui.research.tip_cancel"));
                }
                case START -> {
                    t.addLine(Text.lang("wfcore.gui.research.start"));
                    t.addLine(Text.lang("wfcore.gui.research.tip_start"));
                }
                case COMPLETE -> t.addLine(Text.of(Component
                        .translatable("wfcore.gui.research.blocker_complete")
                        .withStyle(net.minecraft.ChatFormatting.GREEN)));
                case QUEUE_FULL -> t.addLine(Text.of(Component
                        .translatable("wfcore.gui.research.blocker_queue_full", ResearchUnitMachine.QUEUE_SIZE)
                        .withStyle(net.minecraft.ChatFormatting.RED)));
                case LOCKED -> {
                    t.addLine(Text.of(Component.translatable("wfcore.gui.research.blocker_locked")
                            .withStyle(net.minecraft.ChatFormatting.RED)));
                    appendUnmetPrereqs(t, mte, r);
                }
            }
        }).tooltipAutoUpdate(true);
        return button;
    }

    private static Component actionLabel(ResearchUnitMachine mte) {
        return switch (startState(mte)) {
            case CANCEL -> Component.translatable("wfcore.gui.research.cancel");
            case COMPLETE -> Component.translatable("wfcore.gui.research.completed");
            default -> Component.translatable("wfcore.gui.research.start");
        };
    }

    /**
     * Why the selected research can (or can't) be acted on; drives the button label, colour and tooltip.
     */
    private static StartState startState(ResearchUnitMachine mte) {
        Research r = selected();
        if (r == null) return StartState.START;
        return switch (statusOf(mte, r.getId())) {
            case QUEUED, RESEARCHING -> StartState.CANCEL;
            case COMPLETE -> StartState.COMPLETE;
            case LOCKED -> StartState.LOCKED;
            case READY -> mte.getClientQueue().size() >= ResearchUnitMachine.QUEUE_SIZE ? StartState.QUEUE_FULL :
                    StartState.START;
        };
    }

    /**
     * True when the button does something on click (start a ready research, or cancel a queued one).
     */
    private static boolean canAct(ResearchUnitMachine mte) {
        StartState s = startState(mte);
        return s == StartState.START || s == StartState.CANCEL;
    }

    private static void addProgressBar(ParentWidget<?> detail, ResearchUnitMachine mte, int x, int y, int w, int h) {
        addSegmentBar(detail, x, y, w, h, () -> barProgress(mte), "progress_bar");
    }

    /**
     * A segmented achievement-style fill bar that lights segments up to {@code progress} (0..1) with a live "NN%"
     * tooltip. Returns the bar widget so callers can gate its visibility.
     */
    private static ParentWidget<?> addSegmentBar(ParentWidget<?> parent, int x, int y, int w, int h,
                                                 java.util.function.DoubleSupplier progress, String name) {
        ParentWidget<?> bar = new ParentWidget<>();
        bar.name(name);
        bar.pos(x, y).size(w, h);
        bar.background(new Rectangle().color(COLOR_BAR_BG));
        int segW = Math.max(1, w / BAR_SEGMENTS);
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            final float threshold = (i + 1) / (float) BAR_SEGMENTS;
            ParentWidget<?> seg = new ParentWidget<>();
            seg.name(name + "_seg_" + i);
            seg.pos(i * segW, 0).size(Math.max(1, segW - 1), h);
            seg.background(new Rectangle().color(COLOR_BAR_FILL));
            seg.setEnabledIf(s -> progress.getAsDouble() >= threshold);
            bar.child(seg);
        }
        bar.tooltipDynamic(t -> t.addLine(Text.str(percentOf(progress.getAsDouble()) + "%")))
                .tooltipAutoUpdate(true);
        parent.child(bar);
        return bar;
    }

    /// ///////////////// bottom-right queue strip ////////////////////

    private static ParentWidget<?> buildQueue(ResearchUnitMachine mte, PanelSyncManager sync) {
        ParentWidget<?> queue = new ParentWidget<>();
        queue.name("queue");
        queue.pos(QUEUE_X, QUEUE_Y).size(QUEUE_W, QUEUE_H);
        queue.background(GuiTextures.MC_BACKGROUND);
        queue.child(new TextWidget<>(Text.lang("wfcore.gui.research.queue")).pos(INSET, 4).name("queue_label"));

        int slots = ResearchUnitMachine.QUEUE_SIZE;
        int slotGap = Math.max(2, (QUEUE_W - 2 * INSET - slots * QUEUE_SLOT) / Math.max(1, slots - 1));
        for (int i = 0; i < slots; i++) {
            final int slot = i;
            InteractionSyncHandler remove = new InteractionSyncHandler().setOnMousePressed(d -> mte.dequeueAt(slot));
            sync.syncValue("research_dequeue", slot, remove);

            ButtonWidget<?> qb = new ButtonWidget<>();
            qb.name("queue_slot_" + i);
            qb.pos(INSET + i * (QUEUE_SLOT + slotGap), 14).size(QUEUE_SLOT, QUEUE_SLOT);
            qb.background(queueBackground(mte, slot));
            qb.overlay(itemIconDrawable(() -> queueIcon(mte, slot)).asIcon().size(18));
            qb.tooltipDynamic(t -> {
                List<String> q = mte.getClientQueue();
                if (slot >= q.size()) return;
                Research r = ResearchRegistry.get(q.get(slot));
                t.titleMargin();
                if (r != null) t.addLine(Text.lang(r.getNameKey()));
                t.addLine(Text.str(percentOf(mte.getClientProgress(q.get(slot))) + "%"));
                t.addLine(Text.lang(slot < mte.getJobCapacity() ? "wfcore.gui.research.running" :
                        "wfcore.gui.research.waiting"));
                t.spaceLine(2);
                t.addLine(Text.lang("wfcore.gui.research.remove_hint"));
            }).tooltipAutoUpdate(true);
            qb.syncHandler("research_dequeue", slot);
            queue.child(qb);
        }
        return queue;
    }

    /**
     * The dark GT "display" screen below the queue, showing the controller's live machine status: available
     * compute, parallel job capacity, how many jobs are active, and a coloured working/idling/paused line.
     */
    private static ParentWidget<?> buildScreen(ResearchUnitMachine mte) {
        ParentWidget<?> screen = new ParentWidget<>();
        screen.name("status_screen");
        screen.pos(SCREEN_X, SCREEN_Y).size(SCREEN_W, SCREEN_H);
        screen.background(GuiTextures.DISPLAY);

        int lh = 12;
        screen.child(screenLine(() -> Component.translatable("wfcore.gui.research.screen_compute", maxCwu(mte)),
                INSET).name("screen_compute"));
        screen.child(screenLine(() -> Component.translatable("wfcore.gui.research.screen_capacity",
                mte.getJobCapacity()), INSET + lh).name("screen_capacity"));
        screen.child(screenLine(() -> Component.translatable("wfcore.gui.research.screen_active",
                activeJobs(mte), mte.getJobCapacity()), INSET + 2 * lh).name("screen_active"));
        // status line carries its own colour, so it isn't forced white like the readout lines above
        screen.child(new TextWidget<>(Text.dynamic(() -> statusLine(mte)))
                .pos(INSET, INSET + 3 * lh + 3).name("screen_status"));
        // the research step ("recipe") currently in progress + a fill bar of its completion; hidden when idle
        screen.child(new TextWidget<>(Text.dynamic(() -> currentStepText(mte))).color(0xFFFFFFFF)
                .pos(INSET, INSET + 4 * lh + 6).name("screen_step"));
        addSegmentBar(screen, INSET, INSET + 5 * lh + 4, SCREEN_W - 2 * INSET, 5,
                () -> stepProgress(mte), "screen_step_bar")
                .setEnabledIf(wid -> leadingActiveId(mte) != null);
        return screen;
    }

    /** The leading active research id (the "current recipe" running), or null when nothing is being worked on. */
    private static String leadingActiveId(ResearchUnitMachine mte) {
        List<String> q = mte.getClientQueue();
        return q.isEmpty() ? null : q.get(0);
    }

    /** Current-step (per-run) completion 0..1 of the leading active research, for the status screen bar. */
    private static float stepProgress(ResearchUnitMachine mte) {
        String id = leadingActiveId(mte);
        return id == null ? 0f : mte.getClientStepProgress(id);
    }

    private static Component currentStepText(ResearchUnitMachine mte) {
        String id = leadingActiveId(mte);
        if (id == null) return Component.empty();
        return Component.translatable("wfcore.gui.research.screen_step", percentOf(stepProgress(mte)));
    }

    //////////////////// GT-style machine status screen ////////////////////

    /**
     * A white readout line for the dark screen, positioned at the screen's inset x and the given y.
     */
    private static TextWidget<?> screenLine(Supplier<Component> text, int y) {
        return new TextWidget<>(Text.dynamic(text)).color(0xFFFFFFFF).pos(INSET, y);
    }

    private static int maxCwu(ResearchUnitMachine mte) {
        var provider = mte.getComputationProvider();
        return provider == null ? 0 : provider.getMaxCWUt();
    }

    private static int activeJobs(ResearchUnitMachine mte) {
        return Math.min(mte.getClientQueue().size(), mte.getJobCapacity());
    }

    private static Component statusLine(ResearchUnitMachine mte) {
        return switch (mte.getRunStatus()) {
            case PAUSED -> Component.translatable("wfcore.gui.research.screen_paused")
                    .withStyle(net.minecraft.ChatFormatting.RED);
            case WORKING -> Component.translatable("wfcore.gui.research.screen_working")
                    .withStyle(net.minecraft.ChatFormatting.GREEN);
            case WAITING_MATERIALS -> Component.translatable("wfcore.gui.research.screen_waiting_materials")
                    .withStyle(net.minecraft.ChatFormatting.GOLD);
            case WAITING_COMPUTE -> Component.translatable("wfcore.gui.research.screen_waiting_compute")
                    .withStyle(net.minecraft.ChatFormatting.GOLD);
            case WAITING_ENERGY -> Component.translatable("wfcore.gui.research.screen_waiting_energy")
                    .withStyle(net.minecraft.ChatFormatting.GOLD);
            case WAITING_MAINTENANCE -> Component.translatable("wfcore.gui.research.screen_waiting_maintenance")
                    .withStyle(net.minecraft.ChatFormatting.RED);
            case IDLE -> Component.translatable("wfcore.gui.research.screen_idling")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW);
        };
    }

    /// ///////////////// item rows ////////////////////

    private static void addItemRow(ParentWidget<?> detail, int x, int y, IntFunction<ItemStack> provider, int count,
                                   String nameTag) {
        for (int i = 0; i < count; i++) {
            final int idx = i;
            ParentWidget<?> sprite = itemIcon(() -> provider.apply(idx), 16);
            sprite.name(nameTag + "_" + idx).pos(x + i * 17, y);
            sprite.tooltipDynamic(t -> {
                ItemStack s = provider.apply(idx);
                if (!s.isEmpty()) t.addLine(Text.of(s.getHoverName()));
            }).tooltipAutoUpdate(true);
            detail.child(sprite);
        }
    }

    /** A row of {@code count} fluid-cost sprites for the selected research, each with a name + amount tooltip. */
    private static void addFluidRow(ParentWidget<?> detail, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            final int idx = i;
            ParentWidget<?> sprite = fluidIcon(() -> fluidPerRunAt(idx), 16);
            sprite.name("input_fluid_" + idx).pos(x + i * 17, y);
            sprite.tooltipDynamic(t -> {
                FluidStack s = fluidPerRunAt(idx);
                if (!s.isEmpty()) {
                    t.addLine(Text.of(s.getDisplayName().copy().append(Component.literal(" " + s.getAmount() + " mB")
                            .withStyle(net.minecraft.ChatFormatting.GRAY))));
                }
            }).tooltipAutoUpdate(true);
            detail.child(sprite);
        }
    }

    /**
     * Tabs, in order: every explicitly-registered {@link ResearchCategory} first, then a default category for
     * any id a research references but that was never registered. A registered category with no researches
     * still shows its (empty) tab.
     */
    private static List<ResearchCategory> categories() {
        Map<String, ResearchCategory> ordered = new LinkedHashMap<>();
        for (ResearchCategory c : ResearchCategoryRegistry.all()) {
            ordered.put(c.getId(), c);
        }
        for (Research r : ResearchRegistry.all()) {
            ordered.computeIfAbsent(r.getCategory(), ResearchCategoryRegistry::getOrCreate);
        }
        if (ordered.isEmpty()) {
            ordered.put("wfcore", ResearchCategoryRegistry.getOrCreate("wfcore"));
        }
        return new ArrayList<>(ordered.values());
    }

    // ------------------------------------------------------------------ helpers

    private static Research selected() {
        return SELECTED[0] == null ? null : ResearchRegistry.get(SELECTED[0]);
    }

    private static float barProgress(ResearchUnitMachine mte) {
        Research r = selected();
        return r == null ? 0f : mte.getResearchState().getProgress(r.getId());
    }

    /**
     * Formats 0..1 progress as an integer percent, never reading "100%" until the research is actually complete:
     * a plain {@code Math.round} shows "100%" from 99.5% onward, which looked like a finished research that hadn't
     * finished. Anything short of a true 1.0 is capped at 99%.
     */
    private static int percentOf(double progress) {
        if (progress >= 1.0) return 100;
        return Math.min(99, (int) Math.round(progress * 100.0));
    }

    private static ItemStack queueIcon(ResearchUnitMachine mte, int slot) {
        List<String> q = mte.getClientQueue();
        if (slot >= q.size()) return ItemStack.EMPTY;
        Research r = ResearchRegistry.get(q.get(slot));
        return r == null ? ItemStack.EMPTY : r.getIcon();
    }

    private static ItemStack inputPerRunAt(int i) {
        Research r = selected();
        if (r == null) return ItemStack.EMPTY;
        List<com.norwood.wfcore.api.research.ResearchInput> inputs = r.getItemInputs();
        // cyclingStack rotates through all tag members (~1/s) for tag costs; a single stack for exact costs.
        return i < inputs.size() ? inputs.get(i).cyclingStack() : ItemStack.EMPTY;
    }

    private static FluidStack fluidPerRunAt(int i) {
        Research r = selected();
        if (r == null) return FluidStack.EMPTY;
        List<FluidStack> fluids = r.getFluidsPerRun();
        return i < fluids.size() ? fluids.get(i) : FluidStack.EMPTY;
    }

    private static ItemStack unlockedAt(int i) {
        Research r = selected();
        if (r == null) return ItemStack.EMPTY;
        List<ItemStack> items = r.getUnlockedItems();
        return i < items.size() ? items.get(i) : ItemStack.EMPTY;
    }

    /**
     * A plain icon widget that renders just the item via {@link ItemDrawable} (no slot background) at {@code size}.
     */
    private static ParentWidget<?> itemIcon(Supplier<ItemStack> supplier, int size) {
        ParentWidget<?> w = new ParentWidget<>();
        w.size(size).background(itemIconDrawable(supplier));
        return w;
    }

    private static IDrawable itemIconDrawable(Supplier<ItemStack> supplier) {
        ItemDrawable drawable = new ItemDrawable();
        return (ctx, x, y, w, h, theme) -> {
            ItemStack s = supplier.get();
            if (s != null && !s.isEmpty()) {
                drawable.item(s);
                drawable.draw(ctx, x, y, w, h, theme);
            }
        };
    }

    /** A plain icon widget that renders just the fluid via {@link FluidDrawable} (no slot background). */
    private static ParentWidget<?> fluidIcon(Supplier<FluidStack> supplier, int size) {
        ParentWidget<?> w = new ParentWidget<>();
        w.size(size).background(fluidIconDrawable(supplier));
        return w;
    }

    private static IDrawable fluidIconDrawable(Supplier<FluidStack> supplier) {
        FluidDrawable drawable = new FluidDrawable();
        return (ctx, x, y, w, h, theme) -> {
            FluidStack s = supplier.get();
            if (s != null && !s.isEmpty()) {
                drawable.fluid(s);
                drawable.draw(ctx, x, y, w, h, theme);
            }
        };
    }

    /**
     * The single source of truth for "what state is this research in", used by the node tile colour, the node
     * tooltip status line and the start/cancel button. Evaluated live (never cached at build time) so the tile
     * colour always agrees with the tooltip and the synced server state.
     */
    private static NodeStatus statusOf(ResearchUnitMachine mte, String rid) {
        ResearchState state = mte.getResearchState();
        if (state.isComplete(rid)) return NodeStatus.COMPLETE;
        if (mte.isActiveClient(rid)) return NodeStatus.RESEARCHING;
        if (mte.isQueuedClient(rid)) return NodeStatus.QUEUED;
        if (state.isUnlocked(rid)) return NodeStatus.READY;
        return NodeStatus.LOCKED;
    }

    /**
     * Adds one greyed line per still-incomplete prerequisite and one block per unsatisfied any-of group to a
     * tooltip. Cross-category prerequisites are included; any-of groups show all candidates so the player
     * knows which paths they can take.
     */
    private static void appendUnmetPrereqs(RichTooltip t, ResearchUnitMachine mte, Research r) {
        ResearchState state = mte.getResearchState();
        for (String prereqId : r.getPrerequisites()) {
            if (state.isComplete(prereqId)) continue;
            Research p = ResearchRegistry.get(prereqId);
            Component name = p != null ? Component.translatable(p.getNameKey()) : Component.literal(prereqId);
            t.addLine(Text.of(Component.literal(" - ").append(name).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
        for (List<String> group : r.getAnyOfGroups()) {
            if (group.stream().anyMatch(state::isComplete)) continue;
            t.addLine(Text.of(Component.translatable("wfcore.gui.research.blocker_any_of")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)));
            for (String id : group) {
                Research p = ResearchRegistry.get(id);
                Component name = p != null ? Component.translatable(p.getNameKey()) : Component.literal(id);
                t.addLine(Text.of(Component.literal("   ◦ ").append(name)
                        .withStyle(net.minecraft.ChatFormatting.GRAY)));
            }
        }
    }

    private static String statusLine(ResearchUnitMachine mte, String rid) {
        int pct = percentOf(mte.getResearchState().getProgress(rid));
        return switch (statusOf(mte, rid)) {
            case COMPLETE -> Component.translatable("wfcore.gui.research.status_complete").getString();
            case RESEARCHING -> Component.translatable("wfcore.gui.research.status_running", pct).getString();
            case QUEUED -> Component.translatable("wfcore.gui.research.status_queued", pct).getString();
            case READY -> Component.translatable("wfcore.gui.research.status_ready", pct).getString();
            case LOCKED -> Component.translatable("wfcore.gui.research.status_locked").getString();
        };
    }

    private static int nodeX(int col, int ox) {
        return MARGIN + (col - ox) * COL_SPACING;
    }

    private static int nodeY(int row, int oy) {
        return MARGIN + (row - oy) * ROW_SPACING;
    }

    /**
     * A live node tile, re-evaluated every frame: a status-tinted nine-slice MC background, overlaid with a
     * green completion fill that rises from the bottom edge as the research's progress climbs to 100%, plus a
     * brighten-on-hover highlight so the tile visibly reacts to the cursor.
     */
    private static IDrawable nodeBackground(ResearchUnitMachine mte, Research research, IWidget node) {
        final String rid = research.getId();
        final Rectangle fill = new Rectangle().color(COLOR_BAR_FILL);
        final Rectangle highlight = new Rectangle().color(COLOR_NODE_HOVER);
        return (ctx, x, y, w, h, theme) -> {
            Color.setGlColor(nodeTint(mte, research));
            GuiTextures.MC_BACKGROUND.draw(ctx, x, y, w, h);
            Color.resetGlColor();

            float progress = mte.getResearchState().getProgress(rid);
            if (progress > 0f) {
                int inset = 3;
                int fillH = Math.round(progress * (h - 2 * inset));
                if (fillH > 0) {
                    fill.draw(ctx, x + inset, y + h - inset - fillH, w - 2 * inset, fillH, theme);
                }
            }
            if (node.isHovering()) {
                highlight.draw(ctx, x, y, w, h, theme);
            }
        };
    }

    /**
     * The tile tint: a research's own {@link Research#getNodeColor() colour} when ready, else status colours.
     */
    private static int nodeTint(ResearchUnitMachine mte, Research research) {
        return switch (statusOf(mte, research.getId())) {
            case LOCKED -> COLOR_LOCKED;
            case READY -> research.getNodeColor() != 0 ? research.getNodeColor() : COLOR_AVAILABLE;
            case QUEUED -> COLOR_QUEUED;
            case RESEARCHING -> COLOR_ACTIVE;
            case COMPLETE -> COLOR_COMPLETE;
        };
    }

    private static IDrawable queueBackground(ResearchUnitMachine mte, int slot) {
        return mcBackground(() -> queueTint(mte, slot));
    }

    /**
     * Empty slots stay dim; a running job glows active, a waiting one shows the queued colour.
     */
    private static int queueTint(ResearchUnitMachine mte, int slot) {
        if (slot >= mte.getClientQueue().size()) return COLOR_SLOT;
        return slot < mte.getJobCapacity() ? COLOR_ACTIVE : COLOR_QUEUED;
    }

    /**
     * Tints the nine-slice {@code MC_BACKGROUND} with an ARGB colour fetched each frame (achievement-tile look).
     */
    private static IDrawable mcBackground(java.util.function.IntSupplier color) {
        return (ctx, x, y, w, h, theme) -> {
            Color.setGlColor(color.getAsInt());
            GuiTextures.MC_BACKGROUND.draw(ctx, x, y, w, h);
            Color.resetGlColor();
        };
    }

    private enum StartState {
        START,
        CANCEL,
        COMPLETE,
        LOCKED,
        QUEUE_FULL
    }

    private enum NodeStatus {
        LOCKED,
        READY,
        QUEUED,
        RESEARCHING,
        COMPLETE
    }
}
