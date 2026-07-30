package com.norwood.wfcore.common.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.TextWidget;
import com.norwood.wfcore.common.machine.InterceptorMachine;

/**
 * Status + selection GUI for the interceptor battery, in the same "separate panes" idiom as the launcher: an
 * invisible root, a {@link GuiTextures#TAB_TOP} title tab, an interceptor pick-list pane (choose which
 * interceptor type the battery fires) sharing the launcher's {@link GuiMissilePicker}, and a status pane with
 * the launcher's typing {@link GuiTerminal}. The battery targets autonomously, so the only control is the
 * missile picker.
 */
public final class InterceptorGui {

    private static final int MARGIN = 6;
    private static final int GAP = 4;
    private static final int INSET = 6;
    // Wider than the launcher's column so long interceptor names ("Supersonic Interceptor Missile x64")
    // fit on one line with a little breathing room before the pane edge.
    private static final int LEFT_W = 222;
    private static final int CONTENT_X = MARGIN + INSET;
    private static final int CONTENT_W = LEFT_W - 2 * INSET;

    private static final int TAB_INSET = 4;
    private static final int TITLE_W = 150;
    private static final int TITLE_H = 22;

    // Auto/Manual mode toggle: sits in the top strip to the right of the title tab, right edge flush with the panes.
    private static final int TOGGLE_W = 64;
    private static final int TOGGLE_H = 18;
    private static final int TOGGLE_X = MARGIN + LEFT_W - TOGGLE_W;
    private static final int TOGGLE_Y = MARGIN + (TITLE_H - TOGGLE_H) / 2;
    private static final int COLOR_TOGGLE_BG = 0xFF1A1A1E;
    private static final int COLOR_TOGGLE_BORDER = 0xFF101010;
    private static final int COLOR_AUTO = 0xFF54FF6A;   // green: sizing rounds to each threat
    private static final int COLOR_MANUAL = 0xFFFFC24C; // amber: firing the operator's fixed pick

    // picker pane
    private static final int PICK_Y = MARGIN + TITLE_H - TAB_INSET; // tab overlaps the pane top
    private static final int PICKER_Y = PICK_Y + INSET;             // header row
    private static final int LIST_Y = PICKER_Y + 11;
    private static final int PICKER_VISIBLE_H = 4 * GuiMissilePicker.ROW_H;
    private static final int LINKED_Y = LIST_Y + PICKER_VISIBLE_H + 4;
    private static final int PICK_H = LINKED_Y + 9 + INSET - PICK_Y;

    // status pane (terminal height matches the launcher's readout so the two GUIs read identically)
    private static final int STAT_Y = PICK_Y + PICK_H + GAP;
    private static final int STATUS_Y = STAT_Y + INSET;
    private static final int STATUS_H = 26;
    private static final int STAT_H = STATUS_Y + STATUS_H + INSET - STAT_Y;

    private static final int PANEL_W = MARGIN + LEFT_W + MARGIN;
    private static final int PANEL_H = STAT_Y + STAT_H + MARGIN;

    private InterceptorGui() {}

    public static ModularPanel<?> build(InterceptorMachine mte, PosGuiData data,
                                        PanelSyncManager sync, UISettings settings) {
        ModularPanel<?> panel = ModularPanel.defaultPanel("interceptor", PANEL_W, PANEL_H);
        panel.invisible();
        // No item slots here, so the JEI/EMI overlay (search + item list) is just clutter.
        GuiRecipeViewer.hideOverlay(settings);

        panel.child(pane("pane_picker", MARGIN, PICK_Y, LEFT_W, PICK_H));
        panel.child(pane("pane_status", MARGIN, STAT_Y, LEFT_W, STAT_H));
        panel.child(buildTitleTab(mte));
        panel.child(buildAutoToggle(mte, sync));

        // Interceptor selection: the row click writes the type's registry id, C2S runs selectInterceptor.
        StringSyncValue selSync = new StringSyncValue(mte::getSelectedInterceptorId, mte::selectInterceptor);
        selSync.allowC2S();
        sync.syncValue("selected_interceptor", 0, selSync);
        GuiMissilePicker.attach(panel, CONTENT_X, PICKER_Y, LIST_Y, CONTENT_W, PICKER_VISIBLE_H,
                Component.translatable("wfcore.gui.interceptor.header"),
                mte::getAvailableInterceptors, mte::getSelectedInterceptorId, selSync, () -> pickerHint(mte));

        panel.child(new TextWidget<>(Text.dynamic(() -> Component.translatable(
                "wfcore.gui.interceptor.linked_count", mte.getLinkCount(), InterceptorMachine.MAX_LINKS)))
                .pos(CONTENT_X, LINKED_Y).name("linked_count"));

        panel.child(GuiTerminal.build(CONTENT_X, STATUS_Y, CONTENT_W, STATUS_H,
                () -> mte.getDisplayState().ordinal(), mte::statusComponent,
                () -> terminalColor(mte.getDisplayState())));
        return panel;
    }

    /**
     * The Auto/Manual mode toggle. Auto (default) fires the best-fit interceptor for each incoming missile;
     * Manual fires only the type picked in the list below. Click flips the mode server-side; the label + colour
     * track {@link InterceptorMachine#isAutoBestFit()} (synced).
     */
    private static ButtonWidget<?> buildAutoToggle(InterceptorMachine mte, PanelSyncManager sync) {
        InteractionSyncHandler toggle = new InteractionSyncHandler().setOnMousePressed(d -> mte.toggleAutoBestFit());
        sync.syncValue("auto_best_fit", 0, toggle);

        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("auto_toggle");
        button.pos(TOGGLE_X, TOGGLE_Y).size(TOGGLE_W, TOGGLE_H).syncHandler("auto_best_fit", 0);
        button.background((ctx, x, y, w, h, theme) -> {
            boolean auto = mte.isAutoBestFit();
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, h, COLOR_TOGGLE_BG);
            GuiDraw.drawRect(ctx.getGraphics(), x, y, w, 1, COLOR_TOGGLE_BORDER);
            GuiDraw.drawRect(ctx.getGraphics(), x, y + h - 1, w, 1, COLOR_TOGGLE_BORDER);
            GuiDraw.drawText(ctx.getGraphics(), Component.translatable(auto
                            ? "wfcore.gui.interceptor.mode_auto" : "wfcore.gui.interceptor.mode_manual"),
                    x + 6, y + 5, 1f, auto ? COLOR_AUTO : COLOR_MANUAL, false);
        });
        button.tooltipDynamic(t -> t.addLine(Text.lang(mte.isAutoBestFit()
                        ? "wfcore.gui.interceptor.mode_auto_tip" : "wfcore.gui.interceptor.mode_manual_tip")))
                .tooltipAutoUpdate(true);
        return button;
    }

    private static Component pickerHint(InterceptorMachine mte) {
        return mte.getLinkCount() == 0
                ? Component.translatable("wfcore.gui.interceptor.no_links")
                : Component.translatable("wfcore.gui.interceptor.no_interceptors_hint");
    }

    private static int terminalColor(InterceptorMachine.State state) {
        return switch (state) {
            case SCANNING -> 0xFF54FF6A;                       // green: armed and watching
            case RELOADING, NO_INTERCEPTORS -> 0xFFFFC24C;     // amber
            default -> 0xFFFF5555;                             // red: blocked
        };
    }

    //////////////////// panes + title tab ////////////////////

    private static ParentWidget<?> pane(String name, int x, int y, int w, int h) {
        return new ParentWidget<>().name(name).pos(x, y).size(w, h).background(GuiTextures.MC_BACKGROUND);
    }

    private static ParentWidget<?> buildTitleTab(InterceptorMachine mte) {
        ItemStack block = mte.getDefinition().asStack();
        ParentWidget<?> title = new ParentWidget<>();
        title.name("title").pos(MARGIN, MARGIN).size(TITLE_W, TITLE_H).background(titleTabBackground());
        title.child(new ParentWidget<>().name("title_icon").pos(INSET, 3).size(16)
                .background(new ItemDrawable(block).asIcon().size(16)));
        title.child(new TextWidget<>(Text.of(block.getHoverName())).pos(INSET + 20, 6).name("title_name"));
        return title;
    }

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
}
