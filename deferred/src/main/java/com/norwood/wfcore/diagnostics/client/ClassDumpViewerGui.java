package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.diagnostics.ClassDumpCatalogMessage;

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
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;


final class ClassDumpViewerGui {

    static final int PANEL_W = 500;
    static final int PANEL_H = 286;

    private static final int MARGIN = 8;

    private static final int SEARCH_Y = 6;
    private static final int SEARCH_H = 14;

    private static final int LIST_X = MARGIN;
    private static final int LIST_Y = SEARCH_Y + SEARCH_H + 4;
    private static final int LIST_W = 168;
    private static final int LIST_H = 234;

    private static final int RIGHT_X = LIST_X + LIST_W + MARGIN;
    private static final int RIGHT_W = PANEL_W - RIGHT_X - MARGIN;
    private static final int RIGHT_HEADER_H = 12;
    private static final int CLASS_LIST_Y = LIST_Y + RIGHT_HEADER_H + 2;
    private static final int CLASS_LIST_H = LIST_H - RIGHT_HEADER_H - 2;

    private static final int FOOTER_Y = LIST_Y + LIST_H + 6;
    private static final int FOOTER_H = 16;
    private static final int REFRESH_W = 75;

    private static final int ROW_H = 15;
    private static final int HEADER_H = 16;
    private static final int CLASS_ROW_H = 11;

    private static final int COLOR_ROW = 0xFF1A1A1E;
    private static final int COLOR_ROW_ALT = 0xFF202026;
    private static final int COLOR_ROW_SELECTED = 0xFF2F6BD8;
    private static final int COLOR_GROUP = 0xFF0E0E12;
    private static final int COLOR_BORDER = 0xFF101010;
    private static final int COLOR_FIELD = 0xFF121216;
    private static final int COLOR_TAB = 0xFF1A1A1E;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HINT = 0xFF6E6E78;
    private static final int COLOR_GROUP_TEXT = 0xFF9AD0FF;
    private static final int COLOR_COMMENT = 0xFF7FC77F;
    private static final int COLOR_WARN = 0xFFFF6060;

    private static ListWidget<IWidget, ?> dumpsList;

    private ClassDumpViewerGui() {}

    static ModularPanel<?> build() {
        ModularPanel<?> panel = ModularPanel.defaultPanel("classdump_view", PANEL_W, PANEL_H);

        panel.child(searchField("classdump_search", "Search player...",
                ClassDumpViewerClient::searchFilter, ClassDumpViewerClient::setSearchFilter, true)
                .pos(LIST_X, SEARCH_Y).size(LIST_W, SEARCH_H));

        ListWidget<IWidget, ?> list = rowList();
        list.name("classdump_list");
        list.pos(LIST_X, LIST_Y).size(LIST_W, LIST_H);
        list.scrollDirection(GuiAxis.Y);
        list.collapseDisabledChildren(true);
        list.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_ROW);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
        });
        list.children(dumpRows());
        dumpsList = list;
        panel.child(list);

        panel.child(searchField("classdump_class_search", "Filter classes (Enter)...",
                ClassDumpViewerClient::classFilter, ClassDumpViewerClient::setClassFilter, true)
                .pos(RIGHT_X, SEARCH_Y).size(RIGHT_W, SEARCH_H));

        panel.child(new TextWidget<>(ClassDumpViewerGui::rightHeader)
                .color(COLOR_HINT).name("classdump_right_header")
                .pos(RIGHT_X + 2, LIST_Y + 2).size(RIGHT_W - 4, 10));

        panel.child(classPane());

        panel.child(refreshButton());
        panel.child(new TextWidget<>(ClassDumpViewerGui::metadataLine)
                .color(COLOR_TEXT).name("classdump_meta")
                .pos(LIST_X + REFRESH_W + 8, FOOTER_Y + 4).size(PANEL_W - LIST_X - REFRESH_W - 14, 10));
        return panel;
    }

    // ------------------------------------------------------------------------------------------------------
    // search boxes
    // ------------------------------------------------------------------------------------------------------

    private static TextFieldWidget searchField(String name, String hint, Supplier<String> get,
                                               Consumer<String> set, boolean live) {
        TextFieldWidget field = new TextFieldWidget();
        field.name(name);
        field.setMaxLength(96);
        field.value(stringValue(get, set));
        field.autoUpdateOnChange(live);
        field.setTextColor(COLOR_TEXT);
        field.hintText(Component.literal(hint));
        field.hintColor(COLOR_HINT);
        field.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_FIELD);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
        });
        return field;
    }

    private static IStringValue<String> stringValue(Supplier<String> get, Consumer<String> set) {
        return new IStringValue<>() {
            @Override
            public String getValue() {
                return get.get();
            }

            @Override
            public void setValue(String value) {
                set.accept(value);
            }

            @Override
            public Class<String> getValueType() {
                return String.class;
            }

            @Override
            public String getStringValue() {
                return get.get();
            }

            @Override
            public void setStringValue(String value) {
                set.accept(value);
            }
        };
    }

    // ------------------------------------------------------------------------------------------------------
    // left column: dumps grouped by player
    // ------------------------------------------------------------------------------------------------------

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static ListWidget<IWidget, ?> rowList() {
        return new ListWidget();
    }

    private static List<IWidget> dumpRows() {
        Map<String, List<ClassDumpCatalogMessage.Entry>> grouped = grouped();
        List<IWidget> rows = new ArrayList<>();
        int rowW = LIST_W - 8;
        if (ClassDumpViewerClient.isTruncated()) {
            rows.add(notice(Component.literal("Older dumps not shown"), rowW));
        }
        if (grouped.isEmpty()) {
            rows.add(notice(Component.literal("No class dumps"), rowW));
        }
        for (Map.Entry<String, List<ClassDumpCatalogMessage.Entry>> group : grouped.entrySet()) {
            rows.add(groupHeader(group.getKey(), group.getValue().size(), rowW));
            List<ClassDumpCatalogMessage.Entry> entries = group.getValue();
            for (int i = 0; i < entries.size(); i++) {
                rows.add(dumpRow(entries.get(i), i, rowW));
            }
        }
        return rows;
    }

    private static Map<String, List<ClassDumpCatalogMessage.Entry>> grouped() {
        Map<String, List<ClassDumpCatalogMessage.Entry>> grouped = new LinkedHashMap<>();
        for (ClassDumpCatalogMessage.Entry entry : ClassDumpViewerClient.catalog()) {
            grouped.computeIfAbsent(entry.username(), k -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IWidget groupHeader(String username, int count, int rowW) {
        Component label = Component.literal(username + "  (" + count + ")");
        ParentWidget row = new ParentWidget();
        row.size(rowW, HEADER_H);
        row.setEnabledIf(w -> ClassDumpViewerClient.matchesFilter(username));
        row.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_GROUP);
            GuiDraw.drawRect(ctx.getGraphics(), x, y + h - 1, w, 1, COLOR_BORDER);
            GuiDraw.drawText(ctx.getGraphics(), label, x + 3, y + 4, 1f, COLOR_GROUP_TEXT, false);
        });
        return row;
    }

    private static IWidget dumpRow(ClassDumpCatalogMessage.Entry entry, int index, int rowW) {
        Component label = Component.literal(entry.stamp() + "  (" + entry.classCount() + ")");
        ButtonWidget<?> row = new ButtonWidget<>();
        row.name("classdump_row_" + entry.fileName());
        row.size(rowW, ROW_H);
        row.disableThemeBackground(true);
        row.setEnabledIf(w -> ClassDumpViewerClient.matchesFilter(entry.username()));
        row.background((ctx, x, y, w, h, theme) -> {
            boolean selected = ClassDumpViewerClient.isSelected(entry);
            int base = (index & 1) == 0 ? COLOR_ROW : COLOR_ROW_ALT;
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, selected ? COLOR_ROW_SELECTED : base);
            GuiDraw.drawText(ctx.getGraphics(), label, x + 6, y + 4, 1f, COLOR_TEXT, false);
        });
        row.onMousePressed((context, btn) -> {
            ClassDumpViewerClient.select(entry);
            return true;
        });
        return row;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IWidget notice(Component text, int rowW) {
        ParentWidget row = new ParentWidget();
        row.size(rowW, HEADER_H);
        row.background((ctx, x, y, w, h, theme) ->
                GuiDraw.drawText(ctx.getGraphics(), text, x + 3, y + 4, 1f, COLOR_WARN, false));
        return row;
    }

    // ------------------------------------------------------------------------------------------------------
    // right pane: class list of the selected dump
    // ------------------------------------------------------------------------------------------------------

    private static IWidget classPane() {
        ListWidget<IWidget, ?> list = rowList();
        list.name("classdump_classes");
        list.pos(RIGHT_X, CLASS_LIST_Y).size(RIGHT_W, CLASS_LIST_H);
        list.scrollDirection(GuiAxis.Y);
        list.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_ROW);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
        });
        list.children(classRows());
        return list;
    }

    private static List<IWidget> classRows() {
        int rowW = RIGHT_W - 8;
        List<IWidget> rows = new ArrayList<>();
        if (ClassDumpViewerClient.selected() == null) {
            rows.add(classNotice(Component.literal("Select a dump"), rowW, COLOR_HINT));
            return rows;
        }
        if (ClassDumpViewerClient.error() != null) {
            rows.add(classNotice(Component.literal(ClassDumpViewerClient.error()), rowW, COLOR_WARN));
            return rows;
        }
        if (ClassDumpViewerClient.isLoading()) {
            rows.add(classNotice(Component.literal("Loading..."), rowW, COLOR_HINT));
            return rows;
        }
        for (String line : ClassDumpViewerClient.filteredLines()) {
            rows.add(classLine(line, rowW));
        }
        if (rows.isEmpty()) {
            rows.add(classNotice(Component.literal("No classes match"), rowW, COLOR_WARN));
        }
        return rows;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IWidget classLine(String line, int rowW) {
        boolean comment = !line.isEmpty() && line.charAt(0) == '#';
        Component label = Component.literal(line);
        ParentWidget row = new ParentWidget();
        row.size(rowW, CLASS_ROW_H);
        row.background((ctx, x, y, w, h, theme) ->
                GuiDraw.drawText(ctx.getGraphics(), label, x + 3, y + 1, 1f, comment ? COLOR_COMMENT : COLOR_TEXT, false));
        return row;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static IWidget classNotice(Component text, int rowW, int color) {
        ParentWidget row = new ParentWidget();
        row.size(rowW, HEADER_H);
        row.background((ctx, x, y, w, h, theme) ->
                GuiDraw.drawText(ctx.getGraphics(), text, x + 3, y + 4, 1f, color, false));
        return row;
    }

    private static Component rightHeader() {
        ClassDumpCatalogMessage.Entry entry = ClassDumpViewerClient.selected();
        if (entry == null) {
            return Component.literal("No dump selected");
        }
        if (ClassDumpViewerClient.isLoading()) {
            return Component.literal("Loading " + entry.username() + "...");
        }
        long matches = ClassDumpViewerClient.matchCount();
        long shown = Math.min(matches, ClassDumpViewerClient.MAX_VIEW_ROWS);
        String base = "matches: " + matches;
        return Component.literal(matches > shown ? base + " (showing " + shown + ", refine filter)" : base);
    }

    // ------------------------------------------------------------------------------------------------------
    // footer + navigation
    // ------------------------------------------------------------------------------------------------------

    private static ButtonWidget<?> refreshButton() {
        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("classdump_refresh");
        button.pos(LIST_X, FOOTER_Y).size(REFRESH_W, FOOTER_H);
        button.disableThemeBackground(true);
        button.background((ctx, x, y, w, h, theme) -> {
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_TAB);
            GuiDraw.drawBorderInsideXYWH(ctx.getGraphics(), x, y, w, h, 1, COLOR_BORDER);
            GuiDraw.drawText(ctx.getGraphics(), Component.literal("Refresh"), x + 7, y + 4, 1f, COLOR_TEXT, false);
        });
        button.onMousePressed((context, btn) -> {
            ClassDumpViewerClient.refresh();
            return true;
        });
        return button;
    }

    private static Component metadataLine() {
        ClassDumpCatalogMessage.Entry entry = ClassDumpViewerClient.selected();
        if (entry == null) {
            return Component.literal("Select a class dump  (" + ClassDumpViewerClient.visibleCount() + " listed)");
        }
        return Component.literal(entry.username() + "  " + entry.stamp() + "  "
                + entry.classCount() + " classes  " + (entry.size() / 1024) + " KiB");
    }

    /** Moves the selection through the visible dumps and scrolls it into view. */
    static void navigate(int delta) {
        List<ClassDumpCatalogMessage.Entry> visible = visibleEntries();
        if (visible.isEmpty()) {
            return;
        }
        int current = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (ClassDumpViewerClient.isSelected(visible.get(i))) {
                current = i;
                break;
            }
        }
        int next = current < 0 ? (delta > 0 ? 0 : visible.size() - 1)
                : Math.max(0, Math.min(visible.size() - 1, current + delta));
        ClassDumpViewerClient.select(visible.get(next));
        scrollIntoView(next);
    }

    private static List<ClassDumpCatalogMessage.Entry> visibleEntries() {
        List<ClassDumpCatalogMessage.Entry> visible = new ArrayList<>();
        for (Map.Entry<String, List<ClassDumpCatalogMessage.Entry>> group : grouped().entrySet()) {
            if (ClassDumpViewerClient.matchesFilter(group.getKey())) {
                visible.addAll(group.getValue());
            }
        }
        return visible;
    }

    private static void scrollIntoView(int index) {
        ListWidget<IWidget, ?> list = dumpsList;
        if (list == null) {
            return;
        }
        int offset = ClassDumpViewerClient.isTruncated() ? HEADER_H : 0;
        int seen = 0;
        for (Map.Entry<String, List<ClassDumpCatalogMessage.Entry>> group : grouped().entrySet()) {
            if (!ClassDumpViewerClient.matchesFilter(group.getKey())) {
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
}
