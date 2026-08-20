package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.diagnostics.DiagCatalogMessage;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.value.IStringValue;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.scroll.ScrollArea;
import brachy.modularui.widget.scroll.ScrollData;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.PagedWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The {@code /wfcore_diag view} browser: a Verified and a Flagged tab, each a scrolling list of the captures on
 * the server's disk grouped under the player they were taken from, next to a preview pane that shows whichever
 * capture is selected. A search box filters the list by player name.
 *
 * <p>
 * Unlike every other GUI in this mod, this one is client-only — there is no block entity, no
 * {@code IUIHolder} and no {@code PanelSyncManager}. Selection and the search filter live in
 * {@link DiagViewerClient} static state and are read back by the {@code background(...)} lambdas, which only
 * ever run client-side.
 *
 * <p>
 * The row set is built once from a catalog snapshot and filtered live through {@code setEnabledIf} plus
 * {@code collapseDisabledChildren}, so typing in the search box never rebuilds the panel; only Refresh does
 * (by re-opening the screen with a new catalog).
 */
final class DiagViewerGui {

    static final int PANEL_W = 500;
    static final int PANEL_H = 286;

    private static final int MARGIN = 8;

    private static final int TAB_Y = 6;
    private static final int TAB_W = 105;
    private static final int TAB_H = 18;

    private static final int SEARCH_Y = TAB_Y + TAB_H + 4;
    private static final int SEARCH_H = 14;

    private static final int LIST_X = MARGIN;
    private static final int LIST_Y = SEARCH_Y + SEARCH_H + 4;
    private static final int LIST_W = 188;
    private static final int LIST_H = 212;

    private static final int PREVIEW_X = LIST_X + LIST_W + MARGIN;
    private static final int PREVIEW_W = PANEL_W - PREVIEW_X - MARGIN;

    private static final int FOOTER_Y = LIST_Y + LIST_H + 6;
    private static final int FOOTER_H = 16;
    private static final int REFRESH_W = 75;

    private static final int ROW_H = 15;
    private static final int HEADER_H = 16;

    private static final int COLOR_ROW = 0xFF1A1A1E;
    private static final int COLOR_ROW_ALT = 0xFF202026;
    private static final int COLOR_ROW_SELECTED = 0xFF2F6BD8;
    private static final int COLOR_GROUP = 0xFF0E0E12;
    private static final int COLOR_BORDER = 0xFF101010;
    private static final int COLOR_FIELD = 0xFF121216;
    private static final int COLOR_TAB = 0xFF1A1A1E;
    private static final int COLOR_TAB_ACTIVE = 0xFF2F6BD8;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFF6E6E78;
    private static final int COLOR_GROUP_TEXT = 0xFF9AD0FF;
    private static final int COLOR_WARN = 0xFFFF6060;

    /**
     * Live handles onto the last-built panel, so arrow-key navigation can ask which tab is showing and scroll
     * the right list. Rebuilt (and replaced) whenever the panel is, which only happens on Refresh.
     */
    private static PagedWidget.Controller activeController;
    private static ListWidget<IWidget, ?> verifiedList;
    private static ListWidget<IWidget, ?> flaggedList;

    private DiagViewerGui() {}

    static ModularPanel<?> build() {
        ModularPanel<?> panel = ModularPanel.defaultPanel("diag_view", PANEL_W, PANEL_H);

        PagedWidget.Controller controller = new PagedWidget.Controller();
        activeController = controller;
        PagedWidget<?> paged = new PagedWidget<>();
        paged.name("diag_paged");
        paged.controller(controller);
        paged.pos(LIST_X, LIST_Y).size(LIST_W, LIST_H);
        paged.addPage(page(false));
        paged.addPage(page(true));
        paged.initialPage(0);
        panel.child(paged);

        panel.child(tab(controller, 0, false));
        panel.child(tab(controller, 1, true));
        panel.child(searchField());

        panel.child(new ParentWidget<>()
                .name("diag_preview")
                .pos(PREVIEW_X, LIST_Y).size(PREVIEW_W, LIST_H)
                .background(new FrameDrawable()));

        panel.child(refreshButton());
        panel.child(new TextWidget<>(DiagViewerGui::metadataLine)
                .color(COLOR_TEXT)
                .name("diag_meta")
                .pos(LIST_X + REFRESH_W + 8, FOOTER_Y + 4).size(PANEL_W - LIST_X - REFRESH_W - 14, 10));
        return panel;
    }

    /// ///////////////// search ////////////////////

    private static TextFieldWidget searchField() {
        TextFieldWidget field = new TextFieldWidget();
        field.name("diag_search");
        field.pos(LIST_X, SEARCH_Y).size(LIST_W, SEARCH_H);
        field.setMaxLength(32);
        field.value(new FilterValue());
        // Push every keystroke straight into the filter so the list narrows as you type.
        field.autoUpdateOnChange(true);
        field.setTextColor(COLOR_TEXT);
        field.hintText(Component.translatable("wfcore.gui.diag_view.search"));
        field.hintColor(COLOR_HINT);
        field.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_FIELD);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
        });
        return field;
    }

    /**
     * Binds the search box to {@link DiagViewerClient}'s filter. This screen has no {@code PanelSyncManager},
     * so there is no sync value to hang the field on — a plain client-side {@link IStringValue} is the whole
     * contract the widget needs.
     */
    private static final class FilterValue implements IStringValue<String> {

        @Override
        public String getValue() {
            return DiagViewerClient.searchFilter();
        }

        @Override
        public void setValue(String value) {
            DiagViewerClient.setSearchFilter(value);
        }

        @Override
        public Class<String> getValueType() {
            return String.class;
        }

        @Override
        public String getStringValue() {
            return getValue();
        }

        @Override
        public void setStringValue(String value) {
            setValue(value);
        }
    }

    /// ///////////////// tabs ////////////////////

    private static IWidget page(boolean flagged) {
        ListWidget<IWidget, ?> list = rowList();
        list.name(flagged ? "diag_list_flagged" : "diag_list_verified");
        list.size(LIST_W, LIST_H);
        list.scrollDirection(GuiAxis.Y);
        // Filtered-out rows collapse instead of leaving gaps, so the search narrows the list in place.
        list.collapseDisabledChildren(true);
        list.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_ROW);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
        });
        list.children(rows(flagged));
        if (flagged) {
            flaggedList = list;
        } else {
            verifiedList = list;
        }
        return new ParentWidget<>()
                .name(flagged ? "diag_page_flagged" : "diag_page_verified")
                .size(LIST_W, LIST_H)
                .child(list);
    }

    /** Self-typed generics don't infer cleanly against a wildcard target, so build the list raw and widen. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static ListWidget<IWidget, ?> rowList() {
        return new ListWidget();
    }

    private static ButtonWidget<?> tab(PagedWidget.Controller controller, int index, boolean flagged) {
        ButtonWidget<?> tab = new ButtonWidget<>();
        tab.name("diag_tab_" + index);
        tab.pos(LIST_X + index * (TAB_W + 2), TAB_Y).size(TAB_W, TAB_H);
        tab.disableThemeBackground(true);
        tab.background((ctx, x, y, w, h, theme) -> {
            boolean active = controller.isInitialised() && controller.getActivePageIndex() == index;
            // Counts honour the filter, so the tabs say which side a searched-for player has captures on.
            Component label = Component.translatable(
                    flagged ? "wfcore.gui.diag_view.tab_flagged" : "wfcore.gui.diag_view.tab_verified",
                    DiagViewerClient.visibleCount(flagged));
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, active ? COLOR_TAB_ACTIVE : COLOR_TAB);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
            GuiDraw.drawText(ctx.getGraphics(), label, x + 7, y + 5, 1f,
                    flagged && !active ? COLOR_WARN : COLOR_TEXT, false);
        });
        tab.onMousePressed((context, btn) -> {
            controller.setPage(index);
            return true;
        });
        return tab;
    }

    private static ButtonWidget<?> refreshButton() {
        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("diag_refresh");
        button.pos(LIST_X, FOOTER_Y).size(REFRESH_W, FOOTER_H);
        button.disableThemeBackground(true);
        button.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_TAB);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
            GuiDraw.drawText(ctx.getGraphics(), Component.translatable("wfcore.gui.diag_view.refresh"),
                    x + 7, y + 4, 1f, COLOR_TEXT, false);
        });
        button.onMousePressed((context, btn) -> {
            DiagViewerClient.refresh();
            return true;
        });
        return button;
    }

    /// ///////////////// rows ////////////////////

    /**
     * A flat row sequence: one group header per player followed by that player's captures, newest first.
     * Grouping walks the (already newest-first) catalog, so players whose latest capture is most recent float
     * to the top. Every row is built regardless of the filter and hides itself when the name stops matching.
     */
    private static List<IWidget> rows(boolean flagged) {
        Map<String, List<DiagCatalogMessage.Entry>> grouped = grouped(flagged);

        List<IWidget> rows = new ArrayList<>();
        int rowW = LIST_W - 8;
        if (DiagViewerClient.isTruncated(flagged)) {
            rows.add(notice(Component.translatable("wfcore.gui.diag_view.truncated"), rowW, () -> true));
        }
        Component empty = grouped.isEmpty()
                ? Component.translatable("wfcore.gui.diag_view.empty")
                : Component.translatable("wfcore.gui.diag_view.no_matches");
        rows.add(notice(empty, rowW, () -> DiagViewerClient.visibleCount(flagged) == 0));

        for (Map.Entry<String, List<DiagCatalogMessage.Entry>> group : grouped.entrySet()) {
            rows.add(groupHeader(group.getKey(), group.getValue().size(), rowW));
            List<DiagCatalogMessage.Entry> entries = group.getValue();
            for (int i = 0; i < entries.size(); i++) {
                rows.add(captureRow(entries.get(i), i, rowW));
            }
        }
        return rows;
    }

    /** Captures on one tab, keyed by player in display order. The single source of row ordering. */
    private static Map<String, List<DiagCatalogMessage.Entry>> grouped(boolean flagged) {
        Map<String, List<DiagCatalogMessage.Entry>> grouped = new LinkedHashMap<>();
        for (DiagCatalogMessage.Entry entry : DiagViewerClient.catalog()) {
            if (entry.flagged() == flagged) {
                grouped.computeIfAbsent(entry.username(), k -> new ArrayList<>()).add(entry);
            }
        }
        return grouped;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IWidget groupHeader(String username, int count, int rowW) {
        Component label = Component.literal(username + "  (" + count + ")");
        ParentWidget row = new ParentWidget();
        row.size(rowW, HEADER_H);
        row.setEnabledIf(w -> DiagViewerClient.matchesFilter(username));
        row.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_GROUP);
            GuiDraw.drawRect(ctx.getGraphics(), x, y + h - 1, w, 1, COLOR_BORDER);
            GuiDraw.drawText(ctx.getGraphics(), label, x + 3, y + 4, 1f, COLOR_GROUP_TEXT, false);
        });
        return row;
    }

    private static IWidget captureRow(DiagCatalogMessage.Entry entry, int index, int rowW) {
        Component label = Component.literal(entry.stamp());
        ButtonWidget<?> row = new ButtonWidget<>();
        row.name("diag_row_" + (entry.flagged() ? "f" : "v") + "_" + entry.fileName());
        row.size(rowW, ROW_H);
        row.disableThemeBackground(true);
        row.setEnabledIf(w -> DiagViewerClient.matchesFilter(entry.username()));
        row.background((ctx, x, y, w, h, theme) -> {
            boolean selected = DiagViewerClient.isSelected(entry);
            int base = (index & 1) == 0 ? COLOR_ROW : COLOR_ROW_ALT;
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, selected ? COLOR_ROW_SELECTED : base);
            GuiDraw.drawText(ctx.getGraphics(), label, x + 9, y + 4, 1f, COLOR_TEXT, false);
        });
        row.onMousePressed((context, btn) -> {
            DiagViewerClient.select(entry);
            return true;
        });
        return row;
    }

    /** A non-interactive full-width line used for the empty, no-matches and truncated states. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IWidget notice(Component text, int rowW, BooleanSupplier visible) {
        ParentWidget row = new ParentWidget();
        row.size(rowW, HEADER_H);
        row.setEnabledIf(w -> visible.getAsBoolean());
        row.background((ctx, x, y, w, h, theme) ->
                GuiDraw.drawText(ctx.getGraphics(), text, x + 3, y + 4, 1f, COLOR_WARN, false));
        return row;
    }

    /// ///////////////// arrow-key navigation ////////////////////

    /**
     * Moves the selection {@code delta} places through the visible captures on the active tab and scrolls it
     * into view. With nothing selected yet, steps in from whichever end you are heading from. Clamps at both
     * ends rather than wrapping, so holding an arrow key settles on the first or last capture.
     */
    static void navigate(int delta) {
        boolean flagged = activeTabFlagged();
        List<DiagCatalogMessage.Entry> visible = visibleEntries(flagged);
        if (visible.isEmpty()) {
            return;
        }
        int current = -1;
        DiagCatalogMessage.Entry selected = DiagViewerClient.selected();
        if (selected != null) {
            for (int i = 0; i < visible.size(); i++) {
                if (DiagViewerClient.isSelected(visible.get(i))) {
                    current = i;
                    break;
                }
            }
        }
        int next;
        if (current < 0) {
            next = delta > 0 ? 0 : visible.size() - 1;
        } else {
            next = Math.max(0, Math.min(visible.size() - 1, current + delta));
        }
        DiagViewerClient.select(visible.get(next));
        scrollIntoView(flagged, next);
    }

    private static boolean activeTabFlagged() {
        return activeController != null && activeController.isInitialised()
                && activeController.getActivePageIndex() == 1;
    }

    /** The captures on {@code flagged}'s tab that pass the filter, in the order their rows appear. */
    private static List<DiagCatalogMessage.Entry> visibleEntries(boolean flagged) {
        List<DiagCatalogMessage.Entry> visible = new ArrayList<>();
        for (Map.Entry<String, List<DiagCatalogMessage.Entry>> group : grouped(flagged).entrySet()) {
            if (DiagViewerClient.matchesFilter(group.getKey())) {
                visible.addAll(group.getValue());
            }
        }
        return visible;
    }

    /**
     * Scrolls the list so the {@code index}-th visible capture is on screen. The row's offset is recomputed
     * from the same ordering and row heights {@link #rows} lays out with — disabled rows collapse, so only
     * enabled ones contribute — rather than read off the widget, which has no offset until after layout.
     */
    private static void scrollIntoView(boolean flagged, int index) {
        ListWidget<IWidget, ?> list = flagged ? flaggedList : verifiedList;
        if (list == null) {
            return;
        }
        int offset = DiagViewerClient.isTruncated(flagged) ? HEADER_H : 0;
        int seen = 0;
        for (Map.Entry<String, List<DiagCatalogMessage.Entry>> group : grouped(flagged).entrySet()) {
            if (!DiagViewerClient.matchesFilter(group.getKey())) {
                continue;
            }
            offset += HEADER_H;
            int size = group.getValue().size();
            if (index < seen + size) {
                offset += (index - seen) * ROW_H;
                break;
            }
            seen += size;
            offset += size * ROW_H;
        }

        ScrollArea area = list.getScrollArea();
        ScrollData data = list.getScrollData();
        int visibleSize = data.getVisibleSize(area);
        int scroll = data.getScroll();
        if (offset < scroll) {
            data.scrollTo(area, offset);
        } else if (offset + ROW_H > scroll + visibleSize) {
            data.scrollTo(area, offset + ROW_H - visibleSize);
        }
    }

    /// ///////////////// footer ////////////////////

    private static Component metadataLine() {
        DiagCatalogMessage.Entry entry = DiagViewerClient.selected();
        if (entry == null) {
            return Component.translatable("wfcore.gui.diag_view.no_selection");
        }
        DiagViewerClient.Frame frame = DiagViewerClient.selectedFrame();
        String dims = frame == null ? "-" : frame.width() + "x" + frame.height();
        Component line = Component.literal(entry.username() + "  " + entry.stamp()
                + "  " + dims + "  " + (entry.size() / 1024) + " KiB");
        if (entry.flagged()) {
            return line.copy().append(Component.literal("  FLAGGED").withStyle(ChatFormatting.RED));
        }
        return line;
    }
}
