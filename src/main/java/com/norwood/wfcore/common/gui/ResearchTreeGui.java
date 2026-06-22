package com.norwood.wfcore.common.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.value.IValue;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.scroll.HorizontalScrollData;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ItemDisplayWidget;
import brachy.modularui.widgets.TextWidget;
import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchRegistry;
import com.norwood.wfcore.api.research.ResearchState;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;

import java.util.List;
import java.util.function.Supplier;

/**
 * Minecraft-advancements-styled research tree, built on the ModularUI (brachy) fork. Left: a 2D drag-to-pan
 * node graph (clicking a node selects it). Right: a detail panel with per-run + total CWU/EU/item costs (item
 * sprites with tooltips), unlocked-item sprites, a start/cancel button, and the 3-slot research queue.
 */
public final class ResearchTreeGui {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 230;

    private static final int CANVAS_X = 6;
    private static final int CANVAS_Y = 18;
    private static final int CANVAS_W = 196;
    private static final int CANVAS_H = 206;

    private static final int DETAIL_X = 206;
    private static final int DETAIL_W = PANEL_W - DETAIL_X - 6;

    private static final int NODE = 26;
    private static final int COL_SPACING = 42;
    private static final int ROW_SPACING = 36;
    private static final int MARGIN = 10;
    private static final int MAX_ITEM_SLOTS = 6;

    private static final int COLOR_LINE = 0xFF8A8A8A;
    private static final int COLOR_BORDER = 0xFF101010;
    private static final int COLOR_PANEL = 0xFF202024;
    private static final int COLOR_LOCKED = 0xFF555555;
    private static final int COLOR_AVAILABLE = 0xFF2F6BD8;
    private static final int COLOR_QUEUED = 0xFFB07818;
    private static final int COLOR_ACTIVE = 0xFFE0A020;
    private static final int COLOR_COMPLETE = 0xFF44A050;

    // client-side selection (one GUI per client)
    private static final String[] SELECTED = { null };

    private ResearchTreeGui() {}

    public static ModularPanel<?> build(ResearchUnitMachine mte, PosGuiData data,
                                        PanelSyncManager syncManager, UISettings settings) {
        ModularPanel<?> panel = ModularPanel.defaultPanel("research_unit", PANEL_W, PANEL_H);
        panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.title")).pos(8, 6));
        panel.child(new TextWidget<>(Text.dynamic(
                () -> Component.literal(modeLabel(mte) + "  x" + mte.getJobCapacity()))).pos(150, 6));

        if (!mte.isFormed()) {
            panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.not_formed")).pos(CANVAS_X + 8, CANVAS_Y + 8));
            return panel;
        }
        if (mte.getMode() == ResearchUnitMachine.Mode.SLAVE) {
            panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.slave_mode")).pos(CANVAS_X + 8, CANVAS_Y + 8));
            return panel;
        }

        panel.child(buildCanvas(mte, syncManager));
        panel.child(buildDetail(mte, syncManager));
        return panel;
    }

    private static ScrollWidget<?> buildCanvas(ResearchUnitMachine mte, PanelSyncManager syncManager) {
        ScrollWidget<?> canvas = new ScrollWidget<>();
        canvas.getScrollArea().setScrollData(new HorizontalScrollData());
        canvas.getScrollArea().setScrollData(new VerticalScrollData());
        canvas.pos(CANVAS_X, CANVAS_Y).size(CANVAS_W, CANVAS_H);
        canvas.background(UITexture.builder()
                .location("minecraft:gui/advancements/backgrounds/stone")
                .imageSize(16, 16)
                .tiled()
                .build());

        int maxX = 0, maxY = 0;
        for (Research research : ResearchRegistry.all()) {
            maxX = Math.max(maxX, research.getGridX());
            maxY = Math.max(maxY, research.getGridY());
        }
        canvas.getScrollArea().getScrollX().setScrollSize(MARGIN * 2 + (maxX + 1) * COL_SPACING);
        canvas.getScrollArea().getScrollY().setScrollSize(MARGIN * 2 + (maxY + 1) * ROW_SPACING);

        ResearchState state = mte.getResearchState();
        for (Research research : ResearchRegistry.all()) {
            for (String prereqId : research.getPrerequisites()) {
                Research prereq = ResearchRegistry.get(prereqId);
                if (prereq != null) addConnector(canvas, prereq, research);
            }
        }
        int index = 0;
        for (Research research : ResearchRegistry.all()) {
            canvas.child(buildNode(mte, state, research, index++, syncManager));
        }
        return canvas;
    }

    private static ButtonWidget<?> buildNode(ResearchUnitMachine mte, ResearchState state, Research research,
                                             int index, PanelSyncManager syncManager) {
        final String rid = research.getId();

        InteractionSyncHandler select = new InteractionSyncHandler().setOnMousePressed(d -> mte.setSelected(rid));
        syncManager.syncValue("research_node", index, select);

        ButtonWidget<?> node = new ButtonWidget<>();
        node.pos(nodeX(research), nodeY(research)).size(NODE, NODE);
        node.background(new Rectangle().color(nodeColor(mte, state, rid)));
        node.backgroundOverlay(new Rectangle().color(COLOR_BORDER).hollow(1f));
        if (!research.getIcon().isEmpty()) {
            node.child(new ItemDisplayWidget().item(research.getIcon()).pos(4, 4).size(18));
        }
        node.tooltipBuilder(t -> {
            t.addLine(Text.lang(research.getNameKey()));
            t.addLine(Text.lang("wfcore.gui.research.runs", research.getRunsRequired()));
            t.addLine(Text.lang("wfcore.gui.research.cwu_per_run", research.getCwuPerRun()));
            t.addLine(Text.lang("wfcore.gui.research.select_hint"));
        });
        node.onMousePressed((context, button) -> {
            SELECTED[0] = rid;
            return false; // let the sync handler fire the server-side selection
        });
        node.syncHandler("research_node", index);
        return node;
    }

    private static void addConnector(ScrollWidget<?> canvas, Research from, Research to) {
        int x1 = nodeX(from) + NODE / 2;
        int y1 = nodeY(from) + NODE / 2;
        int x2 = nodeX(to) + NODE / 2;
        int y2 = nodeY(to) + NODE / 2;
        int hx = Math.min(x1, x2);
        int hw = Math.max(2, Math.abs(x2 - x1));
        canvas.child(new ParentWidget<>().background(new Rectangle().color(COLOR_LINE)).pos(hx, y1 - 1).size(hw, 2));
        int vy = Math.min(y1, y2);
        int vh = Math.max(2, Math.abs(y2 - y1));
        canvas.child(new ParentWidget<>().background(new Rectangle().color(COLOR_LINE)).pos(x2 - 1, vy).size(2, vh));
    }

    private static ParentWidget<?> buildDetail(ResearchUnitMachine mte, PanelSyncManager syncManager) {
        ParentWidget<?> detail = new ParentWidget<>();
        detail.pos(DETAIL_X, CANVAS_Y).size(DETAIL_W, CANVAS_H);
        detail.background(new Rectangle().color(COLOR_PANEL));

        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Text.lang("wfcore.gui.research.select_hint") : Text.lang(r.getNameKey());
        })).pos(4, 3));

        detail.child(new TextWidget<>(Text.lang("wfcore.gui.research.per_run")).pos(4, 15));
        addItemRow(detail, 4, 25, ResearchTreeGui::inputPerRunAt);
        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() :
                    Component.literal("CWU " + r.getCwuPerRun() + " | EU/t " + r.getEut());
        })).pos(4, 45));

        detail.child(new TextWidget<>(Text.lang("wfcore.gui.research.total")).pos(4, 57));
        addItemRow(detail, 4, 67, ResearchTreeGui::inputTotalAt);
        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() :
                    Component.literal("CWU " + r.getTotalCWU() + " | EU " + r.getTotalEU());
        })).pos(4, 87));

        detail.child(new TextWidget<>(Text.lang("wfcore.gui.research.unlocks")).pos(4, 99));
        addItemRow(detail, 4, 109, ResearchTreeGui::unlockedAt);

        detail.child(new TextWidget<>(Text.dynamic(() -> Component.literal(statusLine(mte)))).pos(4, 130));

        InteractionSyncHandler action = new InteractionSyncHandler().setOnMousePressed(d -> mte.toggleSelected());
        syncManager.syncValue("research_action", 0, action);
        ButtonWidget<?> button = new ButtonWidget<>();
        button.pos(4, 142).size(DETAIL_W - 8, 14).syncHandler("research_action", 0);
        button.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return (r != null && mte.isQueuedClient(r.getId())) ? Text.lang("wfcore.gui.research.cancel") :
                    Text.lang("wfcore.gui.research.start");
        })).pos(4, 3));
        detail.child(button);

        detail.child(new TextWidget<>(Text.lang("wfcore.gui.research.queue")).pos(4, 162));
        detail.child(new TextWidget<>(Text.dynamic(() -> Component.literal(queueLine(mte, 0)))).pos(4, 174));
        detail.child(new TextWidget<>(Text.dynamic(() -> Component.literal(queueLine(mte, 1)))).pos(4, 184));
        detail.child(new TextWidget<>(Text.dynamic(() -> Component.literal(queueLine(mte, 2)))).pos(4, 194));
        return detail;
    }

    private static void addItemRow(ParentWidget<?> detail, int x, int y,
                                   java.util.function.IntFunction<ItemStack> provider) {
        for (int i = 0; i < MAX_ITEM_SLOTS; i++) {
            final int idx = i;
            ItemDisplayWidget sprite = new ItemDisplayWidget()
                    .item(itemValue(() -> provider.apply(idx)))
                    .displayAmount(true);
            sprite.pos(x + i * 18, y).size(18);
            sprite.tooltipBuilder(t -> {
                ItemStack s = provider.apply(idx);
                if (!s.isEmpty()) t.addLine(s.getHoverName());
            });
            detail.child(sprite);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Research selected() {
        return SELECTED[0] == null ? null : ResearchRegistry.get(SELECTED[0]);
    }

    private static ItemStack inputPerRunAt(int i) {
        Research r = selected();
        if (r == null) return ItemStack.EMPTY;
        List<ItemStack> items = r.getItemsPerRun();
        return i < items.size() ? items.get(i) : ItemStack.EMPTY;
    }

    private static ItemStack inputTotalAt(int i) {
        Research r = selected();
        if (r == null) return ItemStack.EMPTY;
        List<ItemStack> items = r.getItemsPerRun();
        if (i >= items.size()) return ItemStack.EMPTY;
        ItemStack total = items.get(i).copy();
        total.setCount(total.getCount() * r.getRunsRequired());
        return total;
    }

    private static ItemStack unlockedAt(int i) {
        Research r = selected();
        if (r == null) return ItemStack.EMPTY;
        List<ItemStack> items = r.getUnlockedItems();
        return i < items.size() ? items.get(i) : ItemStack.EMPTY;
    }

    private static IValue<ItemStack> itemValue(Supplier<ItemStack> supplier) {
        return new IValue<>() {

            @Override
            public ItemStack getValue() {
                ItemStack s = supplier.get();
                return s == null ? ItemStack.EMPTY : s;
            }

            @Override
            public void setValue(ItemStack value) {}

            @Override
            public Class<ItemStack> getValueType() {
                return ItemStack.class;
            }
        };
    }

    private static String statusLine(ResearchUnitMachine mte) {
        Research r = selected();
        if (r == null) return "";
        ResearchState state = mte.getResearchState();
        String id = r.getId();
        int pct = Math.round(state.getProgress(id) * 100f);
        if (state.isComplete(id)) return "COMPLETE";
        if (mte.isActiveClient(id)) return "Researching " + pct + "%";
        if (mte.isQueuedClient(id)) return "Queued " + pct + "%";
        if (!state.isUnlocked(id)) return "Locked";
        return "Ready (" + pct + "%)";
    }

    private static String queueLine(ResearchUnitMachine mte, int slot) {
        List<String> queue = mte.getClientQueue();
        if (slot >= queue.size()) return (slot + 1) + ". -";
        String id = queue.get(slot);
        Research r = ResearchRegistry.get(id);
        int pct = Math.round(mte.getClientProgress(id) * 100f);
        String marker = slot < mte.getJobCapacity() ? "*" : " "; // * = actively running
        String name = r != null ? Component.translatable(r.getNameKey()).getString() : id;
        return (slot + 1) + marker + " " + name + " " + pct + "%";
    }

    private static String modeLabel(ResearchUnitMachine mte) {
        return mte.getMode() == ResearchUnitMachine.Mode.CONTROL ? "CONTROL" : "SLAVE";
    }

    private static int nodeX(Research research) {
        return MARGIN + research.getGridX() * COL_SPACING;
    }

    private static int nodeY(Research research) {
        return MARGIN + research.getGridY() * ROW_SPACING;
    }

    private static int nodeColor(ResearchUnitMachine mte, ResearchState state, String rid) {
        if (state.isComplete(rid)) return COLOR_COMPLETE;
        if (mte.isActiveClient(rid)) return COLOR_ACTIVE;
        if (mte.isQueuedClient(rid)) return COLOR_QUEUED;
        if (state.isUnlocked(rid)) return COLOR_AVAILABLE;
        return COLOR_LOCKED;
    }
}
