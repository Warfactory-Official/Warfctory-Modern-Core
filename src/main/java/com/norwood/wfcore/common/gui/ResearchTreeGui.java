package com.norwood.wfcore.common.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.value.IValue;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.ScrollWidget;
import brachy.modularui.widget.scroll.HorizontalScrollData;
import brachy.modularui.widget.scroll.VerticalScrollData;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ItemDisplayWidget;
import brachy.modularui.widgets.PagedWidget;
import brachy.modularui.widgets.RichTextWidget;
import brachy.modularui.widgets.TextWidget;
import com.norwood.wfcore.api.research.*;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 320;

    private static final int TAB_Y = 2;
    private static final int TAB_SIZE = 24;
    private static final int TAB_H = 18;

    private static final int TREE_X = 5;
    private static final int TREE_Y = 21;
    private static final int TREE_W = 310;
    private static final int TREE_H = 194;

    private static final int DETAIL_X = 5;
    private static final int BOTTOM_Y = TREE_Y + TREE_H + 4;
    private static final int DETAIL_W = 196;
    private static final int DETAIL_H = 94;

    private static final int QUEUE_X = 205;
    private static final int QUEUE_W = 110;
    private static final int QUEUE_H = 94;
    private static final int QUEUE_SLOT = 24;

    private static final int NODE = 26;
    private static final int COL_SPACING = 46;
    private static final int ROW_SPACING = 40;
    private static final int MARGIN = 12;
    private static final int MAX_ITEM_SLOTS = 6;
    private static final int BAR_SEGMENTS = 20;
    private static final int ARROW_LEN = 5;

    private static final int COLOR_BORDER = 0xFF101010;
    private static final int COLOR_PANEL = 0xFF202024;
    private static final int COLOR_TAB_ACTIVE = 0xFF3A3A42;
    private static final int COLOR_TAB_INACTIVE = 0xFF18181C;
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

    // client-side selection (one GUI per client)
    private static final String[] SELECTED = { null };

    private ResearchTreeGui() {}

    public static ModularPanel<?> build(ResearchUnitMachine mte, PosGuiData data,
                                        PanelSyncManager syncManager, UISettings settings) {
        ModularPanel<?> panel = ModularPanel.defaultPanel("research_unit", PANEL_W, PANEL_H);
        panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.title")).pos(8, 6));
        panel.child(new TextWidget<>(Text.dynamic(
                () -> Component.literal(modeLabel(mte) + "  x" + mte.getJobCapacity()))).pos(238, 6));

        if (!mte.isFormed()) {
            panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.not_formed")).pos(TREE_X + 8, TREE_Y + 8));
            return panel;
        }
        if (mte.getMode() == ResearchUnitMachine.Mode.SLAVE) {
            panel.child(new TextWidget<>(Text.lang("wfcore.gui.research.slave_mode")).pos(TREE_X + 8, TREE_Y + 8));
            return panel;
        }

        buildTabsAndTree(panel, mte, syncManager);
        panel.child(buildDetail(mte, syncManager));
        panel.child(buildQueue(mte, syncManager));
        return panel;
    }

    //////////////////// tabs + per-category trees ////////////////////

    private static void buildTabsAndTree(ModularPanel<?> panel, ResearchUnitMachine mte, PanelSyncManager sync) {
        List<ResearchCategory> categories = categories();
        int[] nodeCounter = { 0 };

        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> paged = new PagedWidget<>();
        paged.controller(controller);
        paged.pos(TREE_X, TREE_Y).size(TREE_W, TREE_H);
        for (ResearchCategory category : categories) {
            paged.addPage(buildCategoryCanvas(mte, category, sync, nodeCounter));
        }
        paged.initialPage(0);
        panel.child(paged);

        for (int i = 0; i < categories.size(); i++) {
            panel.child(buildTab(controller, categories.get(i), i));
        }
    }

    /** An icon tab that switches the active page; the category name shows as a tooltip. */
    private static ButtonWidget<?> buildTab(PagedWidget.Controller controller, ResearchCategory category, int index) {
        ButtonWidget<?> tab = new ButtonWidget<>();
        tab.pos(TREE_X + index * (TAB_SIZE + 1), TAB_Y).size(TAB_SIZE, TAB_H);
        tab.background(new Rectangle().color(COLOR_TAB_INACTIVE));

        ParentWidget<?> highlight = new ParentWidget<>();
        highlight.pos(0, 0).size(TAB_SIZE, TAB_H).background(new Rectangle().color(COLOR_TAB_ACTIVE));
        highlight.setEnabledIf(w -> controller.isInitialised() && controller.getActivePageIndex() == index);
        tab.child(highlight);

        tab.child(new ItemDisplayWidget().item(tabIcon(category)).pos((TAB_SIZE - 16) / 2, (TAB_H - 16) / 2).size(16));
        tab.tooltipBuilder(t -> t.addLine(Text.lang(category.getNameKey())));
        tab.onMousePressed((context, button) -> {
            controller.setPage(index);
            return true;
        });
        return tab;
    }

    private static ScrollWidget<?> buildCategoryCanvas(ResearchUnitMachine mte, ResearchCategory category,
                                                       PanelSyncManager sync, int[] nodeCounter) {
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

        ScrollWidget<?> canvas = new ScrollWidget<>();
        canvas.getScrollArea().setScrollData(new HorizontalScrollData());
        canvas.getScrollArea().setScrollData(new VerticalScrollData());
        canvas.pos(0, 0).size(TREE_W, TREE_H);
        canvas.background(categoryBackground(category));
        canvas.getScrollArea().getScrollX().setScrollSize(MARGIN * 2 + (maxX - minX + 1) * COL_SPACING);
        canvas.getScrollArea().getScrollY().setScrollSize(MARGIN * 2 + (maxY - minY + 1) * ROW_SPACING);

        int connectorColor = category.getConnectorColor();
        for (Research research : nodes) {
            int[] to = layout.get(research.getId());
            for (String prereqId : research.getPrerequisites()) {
                Research prereq = ResearchRegistry.get(prereqId);
                if (prereq != null && prereq.getCategory().equals(id)) {
                    int[] from = layout.get(prereq.getId());
                    addConnector(canvas, from[0], from[1], to[0], to[1], ox, oy, connectorColor);
                }
            }
        }
        for (Research research : nodes) {
            int[] cell = layout.get(research.getId());
            canvas.child(buildNode(mte, research, nodeCounter[0]++, cell[0], cell[1], ox, oy, sync));
        }
        return canvas;
    }

    /** The themed canvas background: a tiled texture, else a solid colour, else the default stone tiles. */
    private static brachy.modularui.api.drawable.IDrawable categoryBackground(ResearchCategory category) {
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
        node.pos(nodeX(col, ox), nodeY(row, oy)).size(NODE, NODE);
        node.background(nodeBackground(mte, rid));
        node.backgroundOverlay(new Rectangle().color(COLOR_BORDER));
        if (!research.getIcon().isEmpty()) {
            node.child(new ItemDisplayWidget().item(research.getIcon()).pos(4, 4).size(18));
        }
        node.tooltipDynamic(t -> {
            t.titleMargin();
            t.addLine(Text.lang(research.getNameKey()));
            t.addLine(Text.lang(research.getDescKey()));
            t.spaceLine(2);
            t.addLine(Text.lang("wfcore.gui.research.runs", research.getRunsRequired()));
            t.addLine(Text.lang("wfcore.gui.research.cwu_per_run", research.getCwuPerRun()));
            t.addLine(Text.str(statusLine(mte, rid)));
            if (statusOf(mte, rid) == NodeStatus.LOCKED && !research.getPrerequisites().isEmpty()) {
                t.spaceLine(2);
                t.addLine(Text.lang("wfcore.gui.research.blocker_locked"));
                appendUnmetPrereqs(t, mte, research);
            }
        });
        node.onMousePressed((context, button) -> {
            SELECTED[0] = rid;
            return false; // let the sync handler fire the server-side selection
        });
        node.syncHandler("research_node", index);
        return node;
    }

    private static void addConnector(ScrollWidget<?> canvas, int fromCol, int fromRow, int toCol, int toRow,
                                     int ox, int oy, int color) {
        int x1 = nodeX(fromCol, ox) + NODE / 2;
        int y1 = nodeY(fromRow, oy) + NODE / 2;
        int x2 = nodeX(toCol, ox) + NODE / 2;
        int y2 = nodeY(toRow, oy) + NODE / 2;
        // Route vertical (at the parent's column) then horizontal (along the child's row) so the final approach
        // — and the arrowhead — points horizontally into the child, making the unlock direction obvious.
        if (y1 != y2) {
            canvas.child(line(x1 - 1, Math.min(y1, y2), 2, Math.abs(y2 - y1), color));
        }
        if (x1 != x2) {
            canvas.child(line(Math.min(x1, x2), y2 - 1, Math.abs(x2 - x1), 2, color));
        }
        boolean rightward = x2 >= x1;
        int tipX = rightward ? nodeX(toCol, ox) - 1 : nodeX(toCol, ox) + NODE;
        addArrowhead(canvas, tipX, y2, rightward ? 1 : -1, color);
    }

    /** A filled triangular arrowhead built from 1px slices (no client-only draw calls), tip at (tipX,tipY). */
    private static void addArrowhead(ScrollWidget<?> canvas, int tipX, int tipY, int dir, int color) {
        for (int i = 0; i < ARROW_LEN; i++) {
            int half = ARROW_LEN - 1 - i;
            canvas.child(line(tipX - dir * half, tipY - half, 1, 2 * half + 1, color));
        }
    }

    private static ParentWidget<?> line(int x, int y, int w, int h, int color) {
        return new ParentWidget<>().background(new Rectangle().color(color)).pos(x, y)
                .size(Math.max(1, w), Math.max(1, h));
    }

    //////////////////// bottom-left detail panel ////////////////////

    private static ParentWidget<?> buildDetail(ResearchUnitMachine mte, PanelSyncManager sync) {
        ParentWidget<?> detail = new ParentWidget<>();
        detail.pos(DETAIL_X, BOTTOM_Y).size(DETAIL_W, DETAIL_H);
        detail.background(new Rectangle().color(COLOR_PANEL));
        // Only visible while a research node is selected.
        detail.setEnabledIf(w -> selected() != null);

        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() : Component.translatable(r.getNameKey());
        })).pos(4, 3));

        RichTextWidget description = new RichTextWidget();
        description.pos(4, 14).size(DETAIL_W - 8, 18);
        description.autoUpdate(true);
        description.textBuilder(rt -> {
            Research r = selected();
            if (r != null) rt.add(Component.translatable(r.getDescKey()).withStyle(net.minecraft.ChatFormatting.GRAY));
        });
        detail.child(description);

        addItemRow(detail, 4, 34, ResearchTreeGui::inputPerRunAt);

        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() :
                    Component.translatable("wfcore.gui.research.cost_per_run", r.getCwuPerRun(), r.getEut());
        })).pos(4, 52));

        detail.child(buildActionButton(mte, sync));

        detail.child(new TextWidget<>(Text.lang("wfcore.gui.research.unlocks")).pos(118, 53));
        addItemRow(detail, 118, 64, ResearchTreeGui::unlockedAt, 4);

        addProgressBar(detail, mte, 4, 84, DETAIL_W - 8, 6);
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
        button.pos(4, 64).size(108, 16).syncHandler("research_action", 0);
        button.background(new Rectangle().color(COLOR_BUTTON_DISABLED));

        ParentWidget<?> activeFill = new ParentWidget<>();
        activeFill.background(new Rectangle().color(COLOR_AVAILABLE)).pos(0, 0).size(108, 16);
        activeFill.setEnabledIf(w -> canAct(mte));
        button.child(activeFill);

        ParentWidget<?> pressedShade = new ParentWidget<>();
        pressedShade.background(new Rectangle().color(COLOR_BUTTON_SHADE)).pos(0, 0).size(108, 16);
        pressedShade.setEnabledIf(w -> !canAct(mte));
        button.child(pressedShade);

        button.backgroundOverlay(new Rectangle().color(COLOR_BORDER).hollow(1f));
        button.child(new TextWidget<>(Text.dynamic(() -> actionLabel(mte))).pos(6, 4));
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
        });
        return button;
    }

    private static Component actionLabel(ResearchUnitMachine mte) {
        return switch (startState(mte)) {
            case CANCEL -> Component.translatable("wfcore.gui.research.cancel");
            case COMPLETE -> Component.translatable("wfcore.gui.research.completed");
            default -> Component.translatable("wfcore.gui.research.start");
        };
    }

    /** Why the selected research can (or can't) be acted on; drives the button label, colour and tooltip. */
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

    /** True when the button does something on click (start a ready research, or cancel a queued one). */
    private static boolean canAct(ResearchUnitMachine mte) {
        StartState s = startState(mte);
        return s == StartState.START || s == StartState.CANCEL;
    }

    private enum StartState {
        START,
        CANCEL,
        COMPLETE,
        LOCKED,
        QUEUE_FULL
    }

    private static void addProgressBar(ParentWidget<?> detail, ResearchUnitMachine mte, int x, int y, int w, int h) {
        ParentWidget<?> bar = new ParentWidget<>();
        bar.pos(x, y).size(w, h);
        bar.background(new Rectangle().color(COLOR_BAR_BG));
        int segW = Math.max(1, w / BAR_SEGMENTS);
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            final float threshold = (i + 1) / (float) BAR_SEGMENTS;
            ParentWidget<?> seg = new ParentWidget<>();
            seg.pos(i * segW, 0).size(Math.max(1, segW - 1), h);
            seg.background(new Rectangle().color(COLOR_BAR_FILL));
            seg.setEnabledIf(s -> barProgress(mte) >= threshold);
            bar.child(seg);
        }
        bar.tooltipDynamic(t -> t.addLine(Text.str(Math.round(barProgress(mte) * 100f) + "%")));
        detail.child(bar);
    }

    //////////////////// bottom-right queue strip ////////////////////

    private static ParentWidget<?> buildQueue(ResearchUnitMachine mte, PanelSyncManager sync) {
        ParentWidget<?> queue = new ParentWidget<>();
        queue.pos(QUEUE_X, BOTTOM_Y).size(QUEUE_W, QUEUE_H);
        queue.background(new Rectangle().color(COLOR_PANEL));
        queue.child(new TextWidget<>(Text.lang("wfcore.gui.research.queue")).pos(4, 4));

        int slots = ResearchUnitMachine.QUEUE_SIZE;
        for (int i = 0; i < slots; i++) {
            final int slot = i;
            InteractionSyncHandler remove = new InteractionSyncHandler().setOnMousePressed(d -> mte.dequeueAt(slot));
            sync.syncValue("research_dequeue", slot, remove);

            ButtonWidget<?> qb = new ButtonWidget<>();
            qb.pos(6 + i * (QUEUE_SLOT + 8), 22).size(QUEUE_SLOT, QUEUE_SLOT);
            qb.background(new Rectangle().color(COLOR_SLOT));
            qb.backgroundOverlay(new Rectangle().color(COLOR_BORDER).hollow(1f));
            qb.child(new ItemDisplayWidget().item(itemValue(() -> queueIcon(mte, slot))).pos(3, 3).size(18));
            qb.tooltipDynamic(t -> {
                List<String> q = mte.getClientQueue();
                if (slot >= q.size()) return;
                Research r = ResearchRegistry.get(q.get(slot));
                t.titleMargin();
                if (r != null) t.addLine(Text.lang(r.getNameKey()));
                t.addLine(Text.str(Math.round(mte.getClientProgress(q.get(slot)) * 100f) + "%"));
                t.addLine(Text.lang(slot < mte.getJobCapacity() ? "wfcore.gui.research.running" :
                        "wfcore.gui.research.waiting"));
                t.spaceLine(2);
                t.addLine(Text.lang("wfcore.gui.research.remove_hint"));
            });
            qb.syncHandler("research_dequeue", slot);
            queue.child(qb);
        }
        return queue;
    }

    //////////////////// item rows ////////////////////

    private static void addItemRow(ParentWidget<?> detail, int x, int y, IntFunction<ItemStack> provider) {
        addItemRow(detail, x, y, provider, MAX_ITEM_SLOTS);
    }

    private static void addItemRow(ParentWidget<?> detail, int x, int y, IntFunction<ItemStack> provider, int count) {
        for (int i = 0; i < count; i++) {
            final int idx = i;
            ItemDisplayWidget sprite = new ItemDisplayWidget()
                    .item(itemValue(() -> provider.apply(idx)))
                    .displayAmount(true);
            sprite.pos(x + i * 18, y).size(18);
            sprite.tooltipDynamic(t -> {
                ItemStack s = provider.apply(idx);
                if (!s.isEmpty()) t.addLine(Text.of(s.getHoverName()));
            });
            detail.child(sprite);
        }
    }

    // ------------------------------------------------------------------ helpers

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

    private static Research selected() {
        return SELECTED[0] == null ? null : ResearchRegistry.get(SELECTED[0]);
    }

    private static float barProgress(ResearchUnitMachine mte) {
        Research r = selected();
        return r == null ? 0f : mte.getResearchState().getProgress(r.getId());
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
        List<ItemStack> items = r.getItemsPerRun();
        return i < items.size() ? items.get(i) : ItemStack.EMPTY;
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

    /** Adds one greyed line per still-incomplete prerequisite (incl. cross-category ones) to a tooltip. */
    private static void appendUnmetPrereqs(RichTooltip t, ResearchUnitMachine mte, Research r) {
        ResearchState state = mte.getResearchState();
        for (String prereqId : r.getPrerequisites()) {
            if (state.isComplete(prereqId)) continue;
            Research p = ResearchRegistry.get(prereqId);
            Component name = p != null ? Component.translatable(p.getNameKey()) : Component.literal(prereqId);
            t.addLine(Text.of(Component.literal(" - ").append(name).withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    private static String statusLine(ResearchUnitMachine mte, String rid) {
        int pct = Math.round(mte.getResearchState().getProgress(rid) * 100f);
        return switch (statusOf(mte, rid)) {
            case COMPLETE -> Component.translatable("wfcore.gui.research.status_complete").getString();
            case RESEARCHING -> Component.translatable("wfcore.gui.research.status_running", pct).getString();
            case QUEUED -> Component.translatable("wfcore.gui.research.status_queued", pct).getString();
            case READY -> Component.translatable("wfcore.gui.research.status_ready", pct).getString();
            case LOCKED -> Component.translatable("wfcore.gui.research.status_locked").getString();
        };
    }

    private enum NodeStatus {
        LOCKED,
        READY,
        QUEUED,
        RESEARCHING,
        COMPLETE
    }

    private static String modeLabel(ResearchUnitMachine mte) {
        return Component.translatable(mte.getMode() == ResearchUnitMachine.Mode.CONTROL ?
                "wfcore.gui.research.control_mode" : "wfcore.gui.research.slave_mode").getString();
    }

    private static int nodeX(int col, int ox) {
        return MARGIN + (col - ox) * COL_SPACING;
    }

    private static int nodeY(int row, int oy) {
        return MARGIN + (row - oy) * ROW_SPACING;
    }

    /** A live, status-driven background for a node tile: re-evaluates {@link #statusOf} every frame. */
    private static brachy.modularui.api.drawable.IDrawable nodeBackground(ResearchUnitMachine mte, String rid) {
        return (ctx, x, y, w, h, theme) -> STATUS_RECTS.get(statusOf(mte, rid)).draw(ctx, x, y, w, h, theme);
    }

    private static final java.util.Map<NodeStatus, Rectangle> STATUS_RECTS = new java.util.EnumMap<>(NodeStatus.class);

    static {
        STATUS_RECTS.put(NodeStatus.LOCKED, new Rectangle().color(COLOR_LOCKED));
        STATUS_RECTS.put(NodeStatus.READY, new Rectangle().color(COLOR_AVAILABLE));
        STATUS_RECTS.put(NodeStatus.QUEUED, new Rectangle().color(COLOR_QUEUED));
        STATUS_RECTS.put(NodeStatus.RESEARCHING, new Rectangle().color(COLOR_ACTIVE));
        STATUS_RECTS.put(NodeStatus.COMPLETE, new Rectangle().color(COLOR_COMPLETE));
    }
}
