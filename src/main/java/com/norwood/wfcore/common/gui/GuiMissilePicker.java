package com.norwood.wfcore.common.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import brachy.modularui.api.GuiAxis;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.TextWidget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A reusable scrollable missile pick-list: a header, a fixed pool of row widgets each bound to entry <i>i</i>
 * of a live {@code id -> count} snapshot (item icon + "Name xCount", blue highlight on the selection), and a
 * centred empty-state hint. Clicking a row writes the missile's registry id through a {@link StringSyncValue}
 * (C2S). Shared by the Missile Launch Silo (strike missiles) and the Interceptor Battery (interceptors).
 * <p>
 * The icon and label are drawn directly in each row's draw lambda at explicit x — a child {@code TextWidget}
 * gets centred by the {@code ButtonWidget} layout regardless of its pos/alignment, so we bypass it to
 * guarantee left alignment.
 */
public final class GuiMissilePicker {

    public static final int ROW_H = 16;
    /** Fixed row-widget pool size; entries beyond this never show (far above any realistic missile mix). */
    public static final int MAX_ROWS = 16;

    private static final int COLOR_BORDER = 0xFF101010;
    private static final int COLOR_ROW = 0xFF1A1A1E;
    private static final int COLOR_ROW_SELECTED = 0xFF2F6BD8;

    private GuiMissilePicker() {}

    /**
     * Adds the header, list and empty-state hint to {@code panel}. The list is {@code w} wide with
     * {@code visibleH} of rows visible (the rest scrolls); {@code available} is the live snapshot,
     * {@code selectedId} the current selection, {@code selSync} the selection sync, {@code emptyHint} the
     * message shown centred over the list while the snapshot is empty.
     */
    public static void attach(ModularPanel<?> panel, int x, int headerY, int listY, int w, int visibleH,
                              Component header, Supplier<Map<String, Integer>> available,
                              Supplier<String> selectedId, StringSyncValue selSync,
                              Supplier<Component> emptyHint) {
        panel.child(new TextWidget<>(Text.of(header)).pos(x, headerY).name("picker_header"));

        ListWidget<IWidget, ?> list = pickerList();
        list.name("picker_list");
        list.pos(x, listY);
        list.size(w, visibleH);
        list.scrollDirection(GuiAxis.Y);
        list.collapseDisabledChildren(true);
        list.background((ctx, bx, by, bw, bh, theme) ->
                GuiDraw.drawRect(ctx.getGraphics(), bx, by, bw, bh, COLOR_ROW));

        List<IWidget> rows = new ArrayList<>();
        for (int i = 0; i < MAX_ROWS; i++) {
            rows.add(row(available, selectedId, selSync, i, w - 4));
        }
        list.children(rows);
        panel.child(list);

        panel.child(new TextWidget<>(Text.dynamic(emptyHint))
                .color(0xFFFFFFFF).alignment(Alignment.Center).maxWidth(w - 6)
                .pos(x, listY + 4).size(w, visibleH - 8).name("picker_hint")
                .setEnabledIf(wd -> available.get().isEmpty()));
    }

    /** Self-typed generics don't infer cleanly against a wildcard target, so build the list raw and widen. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static ListWidget<IWidget, ?> pickerList() {
        return new ListWidget();
    }

    private static IWidget row(Supplier<Map<String, Integer>> available, Supplier<String> selectedId,
                               StringSyncValue selSync, int index, int rowW) {
        ButtonWidget<?> row = new ButtonWidget<>();
        row.name("picker_row_" + index);
        row.size(rowW, ROW_H);
        row.setEnabledIf(w -> entryAt(available, index) != null);
        row.disableThemeBackground(true);
        ItemDrawable icon = new ItemDrawable();
        row.background((ctx, x, y, w, h, theme) -> {
            Map.Entry<String, Integer> entry = entryAt(available, index);
            boolean selected = entry != null && entry.getKey().equals(selectedId.get());
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, selected ? COLOR_ROW_SELECTED : COLOR_ROW);
            GuiDraw.drawRect(ctx.getGraphics(), x, y + h - 1, w, 1, COLOR_BORDER);
            if (entry != null) {
                // item() (not setItem, removed in the multi-stack ItemDrawable rework) replaces the drawn
                // stack in place, so reusing one drawable per row across frames stays correct.
                icon.item(stackOf(entry.getKey()));
                icon.draw(ctx, x + 1, y, 16, 16, theme);
                GuiDraw.drawText(ctx.getGraphics(), label(entry), x + 20, y + 4, 1f, 0xFFFFFFFF, false);
            }
        });
        row.onMousePressed((context, btn) -> {
            Map.Entry<String, Integer> entry = entryAt(available, index);
            if (entry != null) {
                selSync.setStringValue(entry.getKey(), true, true);
            }
            return true;
        });
        return row;
    }

    @Nullable
    private static Map.Entry<String, Integer> entryAt(Supplier<Map<String, Integer>> available, int index) {
        int i = 0;
        for (Map.Entry<String, Integer> entry : available.get().entrySet()) {
            if (i++ == index) {
                return entry;
            }
        }
        return null;
    }

    private static ItemStack stackOf(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id)));
    }

    private static Component label(Map.Entry<String, Integer> entry) {
        return stackOf(entry.getKey()).getHoverName().copy().append(Component.literal(" x" + entry.getValue()));
    }
}
