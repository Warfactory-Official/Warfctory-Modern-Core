package com.norwood.wfcore.common.gui;

import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.ItemDrawable;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.api.value.IValue;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.utils.Color;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.RichTooltip;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ItemDisplayWidget;
import brachy.modularui.widgets.PagedWidget;
import brachy.modularui.widgets.RichTextWidget;
import brachy.modularui.widgets.TextWidget;
import com.norwood.wfcore.api.research.*;
import com.norwood.wfcore.common.gui.widget.PanViewport;
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

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 320;

    /** Gap between sections (and the panel's outer margin). */
    private static final int PAD = 4;
    /**
     * Inner content inset within a section. Applied directly to child coordinates rather than via
     * {@code padding()}: in this ModularUI fork padding only offsets relatively-positioned children, so a
     * statically-positioned {@code pos(x, y)}/{@code right(x)} child ignores it and would sit on the edge.
     */
    private static final int INSET = 5;

    // header row: machine nameplate (block + name) + the working-enabled toggle
    private static final int HEADER_X = PAD;
    private static final int HEADER_Y = PAD;
    private static final int HEADER_W = PANEL_W - 2 * PAD;
    private static final int HEADER_H = 20;
    private static final int TOGGLE_SIZE = 16;

    // bottom row (detail + queue), anchored to the panel's bottom edge
    private static final int DETAIL_X = PAD;
    private static final int DETAIL_W = 260;
    private static final int DETAIL_H = 104;
    private static final int BOTTOM_Y = PANEL_H - PAD - DETAIL_H;

    private static final int QUEUE_X = DETAIL_X + DETAIL_W + PAD;
    private static final int QUEUE_W = PANEL_W - PAD - QUEUE_X;
    private static final int QUEUE_H = 48;
    private static final int QUEUE_SLOT = 24;

    // category tabs run along the bottom edge of the tree pane, between it and the bottom row
    private static final int TAB_SIZE = 24;
    private static final int TAB_H = 18;
    private static final int TAB_Y = BOTTOM_Y - TAB_H - 2;

    // tree pane fills from just below the header down to the tabs
    private static final int TREE_X = PAD;
    private static final int TREE_W = PANEL_W - 2 * PAD;
    private static final int TREE_Y = HEADER_Y + HEADER_H + 2;
    private static final int TREE_H = TAB_Y - TREE_Y;

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
    private static final int COLOR_NODE_HOVER = 0x60FFFFFF;

    // client-side selection (one GUI per client)
    private static final String[] SELECTED = { null };

    private ResearchTreeGui() {}

    public static ModularPanel<?> build(ResearchUnitMachine mte, PosGuiData data,
                                        PanelSyncManager syncManager, UISettings settings) {
        ModularPanel<?> panel = ModularPanel.defaultPanel("research_unit", PANEL_W, PANEL_H);
        panel.invisible();
        panel.child(buildHeader(mte, syncManager));

        if (!mte.isFormed()) {
            panel.child(notice("wfcore.gui.research.not_formed"));
            return panel;
        }
        if (mte.getMode() == ResearchUnitMachine.Mode.SLAVE) {
            panel.child(notice("wfcore.gui.research.slave_mode"));
            return panel;
        }

        buildTabsAndTree(panel, mte, syncManager);
        panel.child(buildDetail(mte, syncManager));
        panel.child(buildQueue(mte, syncManager));
        return panel;
    }

    private static TextWidget<?> notice(String langKey) {
        return new TextWidget<>(Text.lang(langKey)).pos(TREE_X + 8, TREE_Y + 8).name("notice");
    }

    //////////////////// header: nameplate + working-enabled toggle ////////////////////

    /**
     * The GT-multiblock-style header: a nameplate showing the controller's block model (rendered as its item)
     * and display name on the left, the mode/capacity readout and a working-enabled play/stop toggle on the
     * right. Padded parent so its children inset cleanly.
     */
    private static ParentWidget<?> buildHeader(ResearchUnitMachine mte, PanelSyncManager sync) {
        ParentWidget<?> header = new ParentWidget<>();
        header.name("header");
        header.pos(HEADER_X, HEADER_Y).size(HEADER_W, HEADER_H);
        header.background(GuiTextures.MC_BACKGROUND);

        ItemStack block = mte.getDefinition().asStack();
        header.child(new ItemDisplayWidget().item(itemValue(() -> block))
                .pos(INSET, 2).size(16).name("nameplate_icon"));
        header.child(new TextWidget<>(Text.of(block.getHoverName()))
                .pos(INSET + 20, 6).name("nameplate_name"));

        header.child(new TextWidget<>(Text.dynamic(
                () -> Component.literal(modeLabel(mte) + " x" + mte.getJobCapacity())))
                .top(6).right(INSET + TOGGLE_SIZE + 6).name("mode_label"));

        header.child(buildWorkingButton(mte, sync));
        return header;
    }

    /** Play (green) while working is enabled, stop (red) while paused; click toggles, GT-multiblock style. */
    private static ButtonWidget<?> buildWorkingButton(ResearchUnitMachine mte, PanelSyncManager sync) {
        InteractionSyncHandler toggle = new InteractionSyncHandler()
                .setOnMousePressed(d -> mte.toggleWorkingEnabled());
        sync.syncValue("working_enabled", 0, toggle);

        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("working_toggle");
        button.size(TOGGLE_SIZE, TOGGLE_SIZE).right(INSET).top(2).syncHandler("working_enabled", 0);
        button.background(GuiTextures.MC_BUTTON);

        ParentWidget<?> play = new ParentWidget<>();
        play.name("working_play").pos(3, 3).size(TOGGLE_SIZE - 6, TOGGLE_SIZE - 6).background(GuiTextures.PLAY);
        play.setEnabledIf(w -> mte.isWorkingEnabled());
        button.child(play);

        ParentWidget<?> stop = new ParentWidget<>();
        stop.name("working_stop").pos(3, 3).size(TOGGLE_SIZE - 6, TOGGLE_SIZE - 6).background(GuiTextures.STOP);
        stop.setEnabledIf(w -> !mte.isWorkingEnabled());
        button.child(stop);

        button.tooltipDynamic(t -> t.addLine(Text.lang(mte.isWorkingEnabled() ?
                "wfcore.gui.research.working_enabled" : "wfcore.gui.research.working_disabled")))
                .tooltipAutoUpdate(true);
        return button;
    }

    //////////////////// tabs + per-category trees ////////////////////

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

        for (int i = 0; i < categories.size(); i++) {
            panel.child(buildTab(controller, categories.get(i), i));
        }
    }

    /** An icon tab that switches the active page; the category name shows as a tooltip. */
    private static ButtonWidget<?> buildTab(PagedWidget.Controller controller, ResearchCategory category, int index) {
        ButtonWidget<?> tab = new ButtonWidget<>();
        tab.name("tab_" + category.getId());
        tab.pos(TREE_X + index * (TAB_SIZE + 1), TAB_Y).size(TAB_SIZE, TAB_H);
        tab.background(GuiTextures.MC_BACKGROUND);

        ParentWidget<?> highlight = new ParentWidget<>();
        highlight.name("tab_highlight_" + index);
        highlight.pos(0, 0).size(TAB_SIZE, TAB_H).background(new Rectangle().color(COLOR_TAB_ACTIVE));
        highlight.setEnabledIf(w -> controller.isInitialised() && controller.getActivePageIndex() == index);
        tab.child(highlight);

        tab.overlay(new ItemDrawable(tabIcon(category)).asIcon().size(16));
        tab.tooltipBuilder(t -> t.addLine(Text.lang(category.getNameKey())));
        tab.onMousePressed((context, button) -> {
            controller.setPage(index);
            return true;
        });
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

        PanViewport<?> canvas = new PanViewport<>();
        canvas.name("tree_canvas_" + id);
        canvas.pos(INSET, INSET).size(viewW, viewH);
        canvas.background(categoryBackground(category));
        canvas.contentSize(MARGIN * 2 + (maxX - minX + 1) * COL_SPACING,
                MARGIN * 2 + (maxY - minY + 1) * ROW_SPACING);

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
        if (!research.getIcon().isEmpty()) {
            node.background(nodeBackground(mte, research, node));
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
            if (statusOf(mte, rid) == NodeStatus.LOCKED && !research.getPrerequisites().isEmpty()) {
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

    private static void addConnector(ParentWidget<?> canvas, int fromCol, int fromRow, int toCol, int toRow,
                                     int ox, int oy, int color) {
        int px = nodeX(fromCol, ox) + NODE / 2;
        int py = nodeY(fromRow, oy) + NODE / 2;
        int cx = nodeX(toCol, ox) + NODE / 2;
        int cy = nodeY(toRow, oy) + NODE / 2;

        // Same column: a straight vertical run into the child's near (top/bottom) edge.
        if (px == cx) {
            int dir = cy >= py ? 1 : -1;
            int tipY = dir > 0 ? nodeY(toRow, oy) - 1 : nodeY(toRow, oy) + NODE;
            verticalLeg(canvas, px, py + dir * (NODE / 2), tipY, color);
            addArrowhead(canvas, px, tipY, 0, dir, color);
            return;
        }

        // Route vertical (at the parent's column) then horizontal (along the child's row) so the final approach
        // — and the arrowhead — points horizontally into the child. Each leg stops at a node edge, so the
        // connector never runs under the node tiles.
        boolean rightward = cx > px;
        int tipX = rightward ? nodeX(toCol, ox) - 1 : nodeX(toCol, ox) + NODE;
        int startX = px;
        if (py != cy) {
            int dir = cy > py ? 1 : -1;
            verticalLeg(canvas, px, py + dir * (NODE / 2), cy, color);
        } else {
            startX = rightward ? nodeX(fromCol, ox) + NODE : nodeX(fromCol, ox); // leave the parent at its edge
        }
        horizontalLeg(canvas, startX, tipX, cy, color);
        addArrowhead(canvas, tipX, cy, rightward ? 1 : -1, 0, color);
    }

    private static void verticalLeg(ParentWidget<?> canvas, int x, int y1, int y2, int color) {
        canvas.child(line(x - 1, Math.min(y1, y2), 2, Math.abs(y2 - y1), color));
    }

    private static void horizontalLeg(ParentWidget<?> canvas, int x1, int x2, int y, int color) {
        canvas.child(line(Math.min(x1, x2), y - 1, Math.abs(x2 - x1), 2, color));
    }

    /** A filled triangular arrowhead built from 1px slices, tip at (tipX,tipY) pointing along (dx,dy). */
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

    //////////////////// bottom-left detail panel ////////////////////

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
        })).pos(INSET, 4).name("detail_name"));

        RichTextWidget description = new RichTextWidget();
        description.name("detail_description");
        description.pos(INSET, 15).size(innerW, 18);
        description.autoUpdate(true);
        description.textBuilder(rt -> {
            Research r = selected();
            if (r != null) rt.add(Component.translatable(r.getDescKey()).withStyle(net.minecraft.ChatFormatting.GRAY));
        });
        detail.child(description);

        addItemRow(detail, INSET, 35, ResearchTreeGui::inputPerRunAt, "input_item");

        detail.child(new TextWidget<>(Text.dynamic(() -> {
            Research r = selected();
            return r == null ? Component.empty() :
                    Component.translatable("wfcore.gui.research.cost_per_run", r.getCwuPerRun(), r.getEut());
        })).pos(INSET, 53).name("detail_cost"));

        detail.child(buildActionButton(mte, sync));

        detail.child(new TextWidget<>(Text.lang("wfcore.gui.research.unlocks")).pos(INSET + 110, 53).name("unlocks_label"));
        addItemRow(detail, INSET + 110, 64, ResearchTreeGui::unlockedAt, 4, "unlock_item");

        addProgressBar(detail, mte, INSET, 85, innerW, 6);
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
        button.pos(INSET, 64).size(108, 16).syncHandler("research_action", 0);
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
        bar.name("progress_bar");
        bar.pos(x, y).size(w, h);
        bar.background(new Rectangle().color(COLOR_BAR_BG));
        int segW = Math.max(1, w / BAR_SEGMENTS);
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            final float threshold = (i + 1) / (float) BAR_SEGMENTS;
            ParentWidget<?> seg = new ParentWidget<>();
            seg.name("progress_seg_" + i);
            seg.pos(i * segW, 0).size(Math.max(1, segW - 1), h);
            seg.background(new Rectangle().color(COLOR_BAR_FILL));
            seg.setEnabledIf(s -> barProgress(mte) >= threshold);
            bar.child(seg);
        }
        bar.tooltipDynamic(t -> t.addLine(Text.str(Math.round(barProgress(mte) * 100f) + "%"))).tooltipAutoUpdate(true);
        detail.child(bar);
    }

    //////////////////// bottom-right queue strip ////////////////////

    private static ParentWidget<?> buildQueue(ResearchUnitMachine mte, PanelSyncManager sync) {
        ParentWidget<?> queue = new ParentWidget<>();
        queue.name("queue");
        queue.pos(QUEUE_X, BOTTOM_Y).size(QUEUE_W, QUEUE_H);
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
            qb.child(new ItemDisplayWidget().item(itemValue(() -> queueIcon(mte, slot)))
                    .pos(3, 3).size(18).name("queue_item_" + i));
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
            }).tooltipAutoUpdate(true);
            qb.syncHandler("research_dequeue", slot);
            queue.child(qb);
        }
        return queue;
    }

    //////////////////// item rows ////////////////////

    private static void addItemRow(ParentWidget<?> detail, int x, int y, IntFunction<ItemStack> provider,
                                   String nameTag) {
        addItemRow(detail, x, y, provider, MAX_ITEM_SLOTS, nameTag);
    }

    private static void addItemRow(ParentWidget<?> detail, int x, int y, IntFunction<ItemStack> provider, int count,
                                   String nameTag) {
        for (int i = 0; i < count; i++) {
            final int idx = i;
            ItemDisplayWidget sprite = new ItemDisplayWidget()
                    .item(itemValue(() -> provider.apply(idx)))
                    .displayAmount(true);
            sprite.name(nameTag + "_" + idx).pos(x + i * 18, y).size(18);
            sprite.tooltipDynamic(t -> {
                ItemStack s = provider.apply(idx);
                if (!s.isEmpty()) t.addLine(Text.of(s.getHoverName()));
            }).tooltipAutoUpdate(true);
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

    /** The tile tint: a research's own {@link Research#getNodeColor() colour} when ready, else status colours. */
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

    /** Empty slots stay dim; a running job glows active, a waiting one shows the queued colour. */
    private static int queueTint(ResearchUnitMachine mte, int slot) {
        if (slot >= mte.getClientQueue().size()) return COLOR_SLOT;
        return slot < mte.getJobCapacity() ? COLOR_ACTIVE : COLOR_QUEUED;
    }

    /** Tints the nine-slice {@code MC_BACKGROUND} with an ARGB colour fetched each frame (achievement-tile look). */
    private static IDrawable mcBackground(java.util.function.IntSupplier color) {
        return (ctx, x, y, w, h, theme) -> {
            Color.setGlColor(color.getAsInt());
            GuiTextures.MC_BACKGROUND.draw(ctx, x, y, w, h);
            Color.resetGlColor();
        };
    }
}
