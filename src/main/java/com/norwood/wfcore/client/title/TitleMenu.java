package com.norwood.wfcore.client.title;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.pda.client.PdaShell;
import com.wf.wflib.pda.client.theme.PdaThemeState.Token;
import com.wf.wflib.pda.client.theme.ThemeScope;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.Signals;
import com.wf.wflib.ui.Subject;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.UiLayout;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;
import com.wf.wflib.ui.input.UiAction;
import com.wf.wflib.ui.widget.Point;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ModListScreen;

import java.util.ArrayList;
import java.util.List;

import static com.wf.wflib.ui.Ui.at;
import static com.wf.wflib.ui.Ui.button;
import static com.wf.wflib.ui.Ui.column;
import static com.wf.wflib.ui.Ui.place;
import static com.wf.wflib.ui.Ui.rect;
import static com.wf.wflib.ui.Ui.stack;
import static com.wf.wflib.ui.Ui.text;

import com.norwood.wfcore.WFCore;

/** Title screen as a PDA app on the {@link PdaShell} palette, {@link PdaShell#WIDTH} x {@link PdaShell#HEIGHT} units. */
final class TitleMenu {
    private static final float BUTTON_WIDTH = 176;
    private static final float BUTTON_HEIGHT = 24;
    private static final int CURSOR_EDGE = 0xFF141614;

    private static final TitleMenu INSTANCE = new TitleMenu();
    private static final Subject<TitleMenu> TITLE = Subject.register("wf_title", TitleMenu.class, () -> INSTANCE);
    private static final int SPLASH_MAX = 96;
    private static final String SPLASH_COLOUR = "#ffffff00";

    private static String splash = "";
    private static UiLayout layout;

    static {
        Signals.text(TITLE, "splash", SPLASH_MAX, s -> splash);
    }

    private TitleMenu() {
    }

    static void splash(String text) {
        splash = text;
    }

    static UiLayout layout() {
        if (layout == null) {
            List<WidgetDef> buttons = new ArrayList<>();
            buttons.add(entry("TESTING ENVIRONMENT", () -> new SelectWorldScreen(title())));
            buttons.add(entry("MULTIPLAYER", () -> new JoinMultiplayerScreen(title())));
            buttons.add(entry("OPTIONS", () -> new OptionsScreen(title(), Minecraft.getInstance().options)));
            buttons.add(entry("MODS", () -> new ModListScreen(title())));
            buttons.add(action("QUIT", () -> Minecraft.getInstance().stop()));
            layout = new UiLayout(WFCore.id("title_menu"), new ThemeScope(stack(
                    rect(PdaShell.WIDTH, PdaShell.HEIGHT, PdaShell.BACK),
                    rect(PdaShell.WIDTH, PdaShell.BAR, PdaShell.BAR_BACK),
                    at("0", Float.toString(PdaShell.BAR - 1), rect(PdaShell.WIDTH, 1, PdaShell.EDGE)),
                    at("8", "5", PdaShell.fit("{wf_title.splash}", SPLASH_COLOUR, 300)),
                    place(Float.toString(PdaShell.WIDTH - 8), "5", Point.TOP_RIGHT,
                            text(SharedConstants.getCurrentVersion().getName(), PdaShell.DIM)),
                    at("16", "34", column(6, buttons.toArray(new WidgetDef[0]))),
                    at("208", "34", PdaShell.panel(160, 120)),
                    at("218", "42", column(6,
                            text("STATUS", PdaShell.DIM),
                            text("MODS LOADED  " + ModList.get().size(), PdaShell.TEXT),
                            text("UPLINK  STANDBY", PdaShell.TEXT),
                            text("OPERATOR  " + Minecraft.getInstance().getUser().getName(), PdaShell.TEXT))),
                    place(Float.toString(PdaShell.WIDTH - 16), Float.toString(PdaShell.HEIGHT - 12), Point.BOTTOM_RIGHT,
                            text("ALL SYSTEMS NOMINAL", PdaShell.ACCENT)),
                    new Cursor())), 0);
        }
        return layout;
    }

    private static net.minecraft.client.gui.screens.Screen title() {
        return Minecraft.getInstance().screen;
    }

    private static WidgetDef entry(String label, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
        return action(label, () -> Minecraft.getInstance().setScreen(screen.get()));
    }

    private static WidgetDef action(String label, Runnable run) {
        UiAction deferred = item -> WFTitleScreen.defer(run);
        return button(BUTTON_WIDTH, BUTTON_HEIGHT, PdaShell.PANEL, PdaShell.HOVER, PdaShell.EDGE, deferred,
                text(label, PdaShell.TEXT));
    }

    /** Arrow at {@link TitlePda}'s cursor while it is over the screen. */
    private record Cursor() implements WidgetDef {
        @Override
        public Widget create(Binder binder) {
            return new Widget() {
                @Override
                public void measure(UiEval ev) {
                    width = 0;
                    height = 0;
                }

                @Override
                public void draw(UiEval ev, float x, float y, float w, float h) {
                    if (!TitlePda.cursorOnScreen()) {
                        return;
                    }
                    Canvas canvas = ev.canvas;
                    float cx = TitlePda.cursorX();
                    float cy = TitlePda.cursorY();
                    canvas.triangle(cx - 1, cy - 2, cx - 1, cy + 14, cx + 10, cy + 10, CURSOR_EDGE);
                    canvas.triangle(cx, cy, cx, cy + 11, cx + 7.5f, cy + 8.5f, Token.ACCENT.argb());
                }
            };
        }
    }
}
