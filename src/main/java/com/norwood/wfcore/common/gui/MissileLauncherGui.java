package com.norwood.wfcore.common.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.utils.Alignment;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.StringSyncValue;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.norwood.wfcore.common.machine.MissileLauncherMachine;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.gui.ChunkMapSection;

import java.util.Locale;

/**
 * Launch-silo GUI on the ModularUI (brachy) fork. Left column: the missile pick-list (everything stored
 * across the factories linked to this silo, with counts), X/Y/Z target fields (Y defaults to "auto" =
 * surface height at launch), the Launch button and a status line. Right side: the pannable chunk map
 * (WarForge's async map-tile textures) where clicking a chunk sets the target. The map degrades to a hint
 * text when WarForge isn't installed; typed coordinates always work.
 */
public final class MissileLauncherGui {

    // Root panel is invisible; content is grouped into separate MC_BACKGROUND panes (research-GUI style):
    // a missiles pane, a targeting pane and a launch/status pane down the left, the chunk map on the right,
    // with a GT-multiblock title tab attached over the top of the missiles pane.
    private static final int PANEL_W = 580;
    private static final int PANEL_H = 300;
    private static final int MARGIN = 6; // panel outer margin
    private static final int GAP = 4;    // gap between adjacent panes
    private static final int INSET = 6;  // content inset inside a pane

    // ---- left column geometry ----
    private static final int PANE_X = MARGIN;
    private static final int LEFT_W = 176;
    private static final int CONTENT_X = PANE_X + INSET;     // left edge of pane content
    private static final int CONTENT_W = LEFT_W - 2 * INSET; // content width within a left pane

    // title tab: a TAB_TOP nameplate (block icon + name) attached over the missiles pane's top edge
    private static final int TAB_INSET = 4;
    private static final int TITLE_W = 150;
    private static final int TITLE_H = 22;
    private static final int TITLE_X = PANE_X;
    private static final int TITLE_Y = MARGIN;

    // missiles pane: header + scrollable pick-list + linked-factory line
    private static final int MISS_Y = TITLE_Y + TITLE_H - TAB_INSET; // tab inset overlaps the pane top
    private static final int MISS_H = 100;
    private static final int PICKER_Y = MISS_Y + INSET;              // header row
    private static final int PICKER_VISIBLE_H = 4 * GuiMissilePicker.ROW_H; // 4 rows visible, rest scrolls
    private static final int LIST_Y = PICKER_Y + 11;
    private static final int LINKED_Y = LIST_Y + PICKER_VISIBLE_H + 4;

    // targeting pane: X / Y / Z rows (+ the auto-Y button beside Y)
    private static final int TGT_Y = MISS_Y + MISS_H + GAP;
    private static final int TGT_H = 68;
    private static final int COORDS_Y = TGT_Y + INSET + 2; // first coord row (X)
    private static final int ROW_H = 20;
    private static final int LABEL_X = CONTENT_X;
    private static final int FIELD_X = CONTENT_X + 18;
    private static final int FIELD_W = 126;
    private static final int FIELD_H = 14;

    // guidance pane: attack profile, final approach bearing and the distance available to join that bearing
    private static final int SETTINGS_Y = TGT_Y + TGT_H + GAP;
    private static final int SETTINGS_H = 68;
    private static final int SETTING_ROW_H = 18;
    private static final int SETTING_VALUE_X = CONTENT_X + 64;
    private static final int SETTING_VALUE_W = CONTENT_W - 64;

    // map pane (right column): its own "Map" title tab + hint line + drag-pannable chunk map, sized to fit
    // the 9x9 chunk grid (180px) snugly instead of stretching to the left column's full height.
    private static final int MAP_SIZE = 180;   // 9 chunks x 20px cells (ChunkMapSection's grid)
    private static final int MAP_HINT_H = 11;  // the "Click to target, drag to pan" line above the map
    private static final int MAP_PANE_X = PANE_X + LEFT_W + GAP;
    private static final int MAP_PANE_Y = MISS_Y; // tab overlaps the pane top, like the missiles pane
    private static final int MAP_PANE_W = MAP_SIZE + 2 * INSET;
    private static final int MAP_PANE_H = MAP_HINT_H + MAP_SIZE + 2 * INSET;
    private static final int MAP_TAB_W = 56;

    // launch pane: placed directly below the map, leaving the left controls as one clean-height stack
    private static final int LP_X = MAP_PANE_X;
    private static final int LP_Y = MAP_PANE_Y + MAP_PANE_H + GAP;
    private static final int LP_W = MAP_PANE_W;
    private static final int LP_H = 62;
    private static final int LAUNCH_X = LP_X + INSET;
    private static final int LAUNCH_W = LP_W - 2 * INSET;
    private static final int LAUNCH_Y = LP_Y + INSET;
    private static final int LAUNCH_H = 20;
    private static final int STATUS_Y = LAUNCH_Y + LAUNCH_H + 4;
    private static final int STATUS_H = 26; // two text lines of headroom

    // telemetry pane: live state and the most recent WF-Ballistics lifecycle events for this silo's last launch
    private static final int TELEMETRY_X = MAP_PANE_X + MAP_PANE_W + GAP;
    private static final int TELEMETRY_Y = MISS_Y;
    private static final int TELEMETRY_W = 192;
    private static final int TELEMETRY_H = SETTINGS_Y + SETTINGS_H - TELEMETRY_Y;

    private static final int COLOR_BORDER = 0xFF101010;
    private static final int COLOR_FIELD_BG = 0xFF000000; // coordinate input fields: solid black
    // Vivid red so an armed silo clearly reads as ready-to-fire; blocked stays a neutral grey.
    private static final int COLOR_LAUNCH = 0xFFE01414;
    private static final int COLOR_DISABLED = 0xFF3A3A40;

    // Terminal status readout colours (the GuiTerminal widget owns the box/prompt/cursor styling).
    private static final int TERM_READY = 0xFF54FF6A;    // bright green: armed / good
    private static final int TERM_WARN = 0xFFFFC24C;     // amber: reloading / no missile
    private static final int TERM_ERROR = 0xFFFF5555;    // red: blocked

    /** World border-ish clamp for target coordinates. */
    private static final int COORD_LIMIT = 30_000_000;

    private MissileLauncherGui() {}

    public static ModularPanel<?> build(MissileLauncherMachine mte, PosGuiData data,
                                        PanelSyncManager sync, UISettings settings) {
        ModularPanel<?> panel = ModularPanel.defaultPanel("missile_launcher", PANEL_W, PANEL_H);
        panel.invisible(); // no single flat background; each section is its own MC_BACKGROUND pane
        sync.addCloseListener(mte::endCreativeAccess);
        // No item slots here, so the JEI/EMI overlay (search + item list) is just clutter over the map.
        GuiRecipeViewer.hideOverlay(settings);

        // ---- coordinate sync values (also the conduit for map clicks; see ChunkMapSection) ----
        // allowC2S is required for the client-set value to reach the server: without it SyncHandler
        // silently drops the packet (only WARN-logs), so launches would always use the stale server
        // coords. InteractionSyncHandler enables it in its constructor; value syncs must opt in.
        IntSyncValue xSync = new IntSyncValue(mte::getTargetX, mte::setTargetX).allowC2S();
        IntSyncValue ySync = new IntSyncValue(mte::getTargetY, mte::setTargetY).allowC2S();
        IntSyncValue zSync = new IntSyncValue(mte::getTargetZ, mte::setTargetZ).allowC2S();
        sync.syncValue("target_x", 0, xSync);
        sync.syncValue("target_y", 0, ySync);
        sync.syncValue("target_z", 0, zSync);

        // ---- launch state, synced through the panel (guaranteed fresh while the GUI is open) ----
        // The machine's @DescSynced displayState covers the general case, but the panel's own value sync
        // pushes every server tick the menu is open, so the button/status react immediately. The getter is
        // side-aware: on the client it echoes the cache so no C2S sync is ever attempted.
        int[] clientState = { mte.getDisplayState().ordinal() };
        IntSyncValue stateSync = new IntSyncValue(
                () -> mte.isRemote() ? clientState[0] : mte.computeLaunchState(data.getPlayer()).ordinal(),
                v -> clientState[0] = v);
        sync.syncValue("launch_state", 0, stateSync);

        // ---- section panes (added first so their content draws on top) ----
        panel.child(pane("pane_missiles", PANE_X, MISS_Y, LEFT_W, MISS_H));
        panel.child(pane("pane_target", PANE_X, TGT_Y, LEFT_W, TGT_H));
        panel.child(pane("pane_settings", PANE_X, SETTINGS_Y, LEFT_W, SETTINGS_H));
        panel.child(pane("pane_launch", LP_X, LP_Y, LP_W, LP_H));
        panel.child(pane("pane_map", MAP_PANE_X, MAP_PANE_Y, MAP_PANE_W, MAP_PANE_H));
        panel.child(pane("pane_telemetry", TELEMETRY_X, TELEMETRY_Y, TELEMETRY_W, TELEMETRY_H));
        // Tabs last of the backgrounds, so their 4px inset overlaps and merges into their pane's top edge.
        ItemStack block = mte.getDefinition().asStack();
        panel.child(tab("title", TITLE_X, TITLE_W, block, block.getHoverName()));
        panel.child(tab("map_tab", MAP_PANE_X, MAP_TAB_W, new ItemStack(Items.FILLED_MAP),
                Component.translatable("wfcore.gui.launcher.map_tab")));

        // ---- missile pick-list (stock aggregated across linked factories) ----
        // Selection travels through a StringSyncValue: the row click writes the missile's registry id
        // client-side, C2S sync runs the server-side selectMissile (allowC2S mandatory in this fork).
        StringSyncValue selSync = new StringSyncValue(mte::getSelectedMissileId, mte::selectMissile);
        selSync.allowC2S();
        sync.syncValue("selected_missile", 0, selSync);
        buildMissilePicker(panel, mte, selSync);

        // ---- X/Y/Z fields ----
        panel.child(coordLabel("X", COORDS_Y));
        panel.child(coordField("target_x", COORDS_Y));
        panel.child(coordLabel("Y", COORDS_Y + ROW_H));
        panel.child(buildYField(mte, sync, COORDS_Y + ROW_H));
        panel.child(buildAutoYButton(mte, ySync, COORDS_Y + ROW_H));
        panel.child(coordLabel("Z", COORDS_Y + 2 * ROW_H));
        panel.child(coordField("target_z", COORDS_Y + 2 * ROW_H));

        // ---- terminal approach settings ----
        buildGuidanceSettings(panel, mte, sync);

        // ---- launch button + terminal status readout ----
        panel.child(buildLaunchButton(mte, data, sync, clientState));
        panel.child(buildStatusTerminal(mte, clientState));
        buildTelemetry(panel, mte);

        // ---- chunk map (WarForge only; all com.flansmod.* stays inside ChunkMapSection) ----
        if (WarforgeIntegration.isLoaded()) {
            ChunkMapSection.attach(panel, mte, data, MAP_PANE_X + INSET, MAP_PANE_Y + INSET,
                    MAP_PANE_W - 2 * INSET, MAP_PANE_H - 2 * INSET, xSync, ySync, zSync);
        } else {
            panel.child(new TextWidget<>(Text.lang("wfcore.gui.launcher.no_warforge"))
                    .alignment(Alignment.Center).maxWidth(MAP_PANE_W - 2 * INSET)
                    .pos(MAP_PANE_X + INSET, MAP_PANE_Y + INSET)
                    .size(MAP_PANE_W - 2 * INSET, MAP_PANE_H - 2 * INSET).name("no_map"));
        }
        return panel;
    }

    //////////////////// panes + title tab (research-GUI-style separate sections) ////////////////////

    /** A section pane: the nine-slice {@code MC_BACKGROUND} tile used as a standalone framed background. */
    private static ParentWidget<?> pane(String name, int x, int y, int w, int h) {
        return new ParentWidget<>().name(name).pos(x, y).size(w, h).background(GuiTextures.MC_BACKGROUND);
    }

    /**
     * A GT-multiblock-style pane title: an item icon + label on a {@link GuiTextures#TAB_TOP} tab, so it reads
     * as a tab attached to the pane below it (added after that pane, its connecting inset overlaps and merges
     * into the pane's top edge). Used for the controller title over the missiles pane and the "Map" tab over
     * the map pane. Children are tab-local.
     */
    private static ParentWidget<?> tab(String name, int x, int w, ItemStack icon, Component label) {
        ParentWidget<?> tab = new ParentWidget<>();
        tab.name(name).pos(x, TITLE_Y).size(w, TITLE_H).background(titleTabBackground());
        tab.child(new ParentWidget<>().name(name + "_icon").pos(INSET, 3).size(16)
                .background(new ItemDrawable(icon).asIcon().size(16)));
        tab.child(new TextWidget<>(Text.of(label)).pos(INSET + 20, 6).name(name + "_label"));
        return tab;
    }

    /**
     * Draws one active TAB_TOP tab as the title bar by slicing it horizontally — crisp left/right caps with the
     * centre stretched — so it reads as one seamless rounded tab (mirrors the research GUI's title).
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

    //////////////////// missile pick-list ////////////////////

    /**
     * Header, scrollable pick-list and linked-factory line. Rows are a fixed pool of {@link #MAX_ROWS}
     * widgets each bound to entry <i>i</i> of the synced availability snapshot (dynamic icon/label,
     * collapsed past the list size) — the fork's ListWidget isn't exercised with dynamic child add/remove
     * anywhere in this codebase, so the pool avoids that entirely.
     */
    private static void buildMissilePicker(ModularPanel<?> panel, MissileLauncherMachine mte,
                                           StringSyncValue selSync) {
        GuiMissilePicker.attach(panel, CONTENT_X, PICKER_Y, LIST_Y, CONTENT_W, PICKER_VISIBLE_H,
                Component.translatable("wfcore.gui.launcher.missiles_header"),
                mte::getAvailableMissiles, mte::getSelectedMissileId, selSync, () -> pickerHint(mte));

        panel.child(new TextWidget<>(Text.dynamic(() -> Component.translatable(
                "wfcore.gui.launcher.linked_count", mte.getLinkCount(), MissileLauncherMachine.MAX_LINKS)))
                .pos(CONTENT_X, LINKED_Y).name("linked_count"));
    }

    private static Component pickerHint(MissileLauncherMachine mte) {
        return mte.getLinkCount() == 0
                ? Component.translatable("wfcore.gui.launcher.no_links")
                : Component.translatable("wfcore.gui.launcher.no_missiles");
    }

    private static TextWidget<?> coordLabel(String axis, int y) {
        return new TextWidget<>(Text.str(axis)).pos(LABEL_X, y + 3).name("label_" + axis);
    }

    /**
     * A coordinate field's box: solid-black fill + 1px border, painted with {@link GuiDraw} rather than a
     * {@link Rectangle} — {@code Rectangle.draw} overwrites its colour with the widget theme's (the same bug
     * that kept the launch button grey), so it must be drawn explicitly to stay black in every widget state.
     */
    private static IDrawable fieldBox() {
        return (ctx, x, y, w, h, theme) -> {
            var g = ctx.getGraphics();
            GuiDraw.drawRect(g, x, y, w, h, COLOR_FIELD_BG);
            GuiDraw.drawRect(g, x, y, w, 1, COLOR_BORDER);
            GuiDraw.drawRect(g, x, y + h - 1, w, 1, COLOR_BORDER);
            GuiDraw.drawRect(g, x, y, 1, h, COLOR_BORDER);
            GuiDraw.drawRect(g, x + w - 1, y, 1, h, COLOR_BORDER);
        };
    }

    private static TextFieldWidget coordField(String key, int y) {
        TextFieldWidget field = new TextFieldWidget();
        field.name("field_" + key);
        field.pos(FIELD_X, y).size(FIELD_W, FIELD_H);
        field.setNumbers(-COORD_LIMIT, COORD_LIMIT);
        // Do NOT enable autoUpdateOnChange here: setNumbers reformats via DecimalFormat, and reformatting
        // mid-edit (e.g. a lone "-" reparsed to a shorter string) leaves the cursor past the text end, so
        // the next character crashes the fork's TextFieldHandler with a substring out-of-bounds. Committing
        // on focus loss (the widget default) keeps text and cursor in sync; clicking Launch or any other
        // widget defocuses the field first, so the typed value still lands before the launch fires.
        field.background(fieldBox());
        field.hoverBackground(fieldBox());
        field.syncHandler(key, 0);
        return field;
    }

    /**
     * The Y target field. Unlike X/Z it isn't a plain number field: the sentinel {@link
     * MissileLauncherMachine#Y_AUTO} shows as "Auto" (surface height at launch) rather than the raw -10000,
     * and typing "Auto"/"A"/blank re-selects it. Bound through a {@link StringSyncValue} (allowC2S) that maps
     * the display string to the same server {@code targetY} the map picker and the A button write.
     */
    private static TextFieldWidget buildYField(MissileLauncherMachine mte, PanelSyncManager sync, int y) {
        StringSyncValue yStr = new StringSyncValue(
                () -> mte.getTargetY() == MissileLauncherMachine.Y_AUTO
                        ? "Auto" : Integer.toString(mte.getTargetY()),
                s -> applyYText(mte, s));
        yStr.allowC2S();
        sync.syncValue("target_y_str", 0, yStr);

        TextFieldWidget field = new TextFieldWidget();
        field.name("field_target_y");
        field.pos(FIELD_X, y).size(FIELD_W, FIELD_H);
        field.background(fieldBox());
        field.hoverBackground(fieldBox());
        field.syncHandler("target_y_str", 0);
        field.tooltipDynamic(t -> t.addLine(Text.lang("wfcore.gui.launcher.y_hint"))).tooltipAutoUpdate(true);
        return field;
    }

    /** Commits the Y field: blank/"Auto"/"A" -> auto sentinel, otherwise a clamped integer (bad input kept). */
    private static void applyYText(MissileLauncherMachine mte, String s) {
        if (s == null) {
            return;
        }
        String t = s.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("auto") || t.equalsIgnoreCase("a")) {
            mte.setTargetY(MissileLauncherMachine.Y_AUTO);
            return;
        }
        try {
            mte.setTargetY(Math.max(-COORD_LIMIT, Math.min(COORD_LIMIT, Integer.parseInt(t))));
        } catch (NumberFormatException ignored) {
            // unparseable (e.g. a lone "-" mid-edit): leave the current value; the field re-displays it
        }
    }

    /** Small button beside the Y field that resets it to "auto" (surface height at launch time). */
    private static ButtonWidget<?> buildAutoYButton(MissileLauncherMachine mte, IntSyncValue ySync, int y) {
        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("y_auto");
        button.pos(FIELD_X + FIELD_W + 3, y).size(FIELD_H, FIELD_H);
        button.background(new Rectangle().color(COLOR_DISABLED));
        button.backgroundOverlay(new Rectangle().color(COLOR_BORDER).hollow(1f));
        button.overlay(Text.str("A"));
        button.tooltipDynamic(t -> t.addLine(Text.lang("wfcore.gui.launcher.y_auto"))).tooltipAutoUpdate(true);
        button.onMousePressed((context, btn) -> {
            ySync.setIntValue(MissileLauncherMachine.Y_AUTO, true, true);
            return true;
        });
        return button;
    }

    private static ButtonWidget<?> buildLaunchButton(MissileLauncherMachine mte, PosGuiData data,
                                                       PanelSyncManager sync, int[] clientState) {
        InteractionSyncHandler launch = new InteractionSyncHandler()
                .setOnMousePressed(d -> mte.requestLaunch(data.getPlayer()));
        sync.syncValue("launch", 0, launch);

        ButtonWidget<?> button = new ButtonWidget<>();
        button.name("launch_button");
        button.pos(LAUNCH_X, LAUNCH_Y).size(LAUNCH_W, LAUNCH_H).syncHandler("launch", 0);
        // Paint the face with GuiDraw.drawRect (an explicit ARGB, NOT a Rectangle — Rectangle.draw
        // overwrites its colour with the widget theme's, which is why every red fill came out grey). This is
        // the exact call the map tiles use for their placeholder, so it's proven to render on a button face.
        // Structure mirrors the map tiles: background + overlay only, no child/backgroundOverlay.
        button.background((ctx, bx, by, bw, bh, theme) -> {
            int face = shownState(clientState) == MissileLauncherMachine.LaunchState.READY
                    ? COLOR_LAUNCH : COLOR_DISABLED;
            GuiDraw.drawRect(ctx.getGraphics(), bx, by, bw, bh, face);
            // 1px border
            GuiDraw.drawRect(ctx.getGraphics(), bx, by, bw, 1, COLOR_BORDER);
            GuiDraw.drawRect(ctx.getGraphics(), bx, by + bh - 1, bw, 1, COLOR_BORDER);
            GuiDraw.drawRect(ctx.getGraphics(), bx, by, 1, bh, COLOR_BORDER);
            GuiDraw.drawRect(ctx.getGraphics(), bx + bw - 1, by, 1, bh, COLOR_BORDER);
        });
        button.overlay(Text.lang("wfcore.gui.launcher.launch").color(0xFFFFFFFF).shadow(true));
        button.tooltipDynamic(t -> t.addLine(Text.dynamic(() -> statusText(mte, shownState(clientState)))))
                .tooltipAutoUpdate(true);
        return button;
    }

    /** The panel-synced launch state (falls back to UNFORMED on a bogus ordinal). */
    private static MissileLauncherMachine.LaunchState shownState(int[] clientState) {
        var values = MissileLauncherMachine.LaunchState.values();
        int i = clientState[0];
        return i >= 0 && i < values.length ? values[i] : MissileLauncherMachine.LaunchState.UNFORMED;
    }

    private static Component statusText(MissileLauncherMachine mte, MissileLauncherMachine.LaunchState state) {
        return switch (state) {
            case UNFORMED -> Component.translatable("wfcore.gui.launcher.status_unformed")
                    .withStyle(net.minecraft.ChatFormatting.RED);
            case LOW_TIER -> Component.translatable("wfcore.gui.launcher.status_low_tier")
                    .withStyle(net.minecraft.ChatFormatting.RED);
            case NO_MISSILE -> Component.translatable("wfcore.gui.launcher.status_no_missile")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW);
            case NO_ENERGY -> Component.translatable("wfcore.gui.launcher.status_no_energy")
                    .withStyle(net.minecraft.ChatFormatting.RED);
            case COOLDOWN -> Component.translatable("wfcore.gui.launcher.status_cooldown",
                    (mte.getCooldown() + 19) / 20).withStyle(net.minecraft.ChatFormatting.YELLOW);
            case READY -> Component.translatable("wfcore.gui.launcher.status_ready")
                    .withStyle(net.minecraft.ChatFormatting.GREEN);
            case MAINTENANCE -> Component.translatable("wfcore.gui.launcher.status_maintenance")
                    .withStyle(net.minecraft.ChatFormatting.RED);
            case NO_TARGET -> Component.translatable("wfcore.gui.launcher.status_no_target")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW);
        };
    }

    //////////////////// terminal-style status readout ////////////////////

    /**
     * The status line rendered as a little CRT terminal: black box, a green prompt, the status text typed
     * out one character at a time whenever it changes, and a blinking block cursor. All animation state lives
     * client-side in a captured {@link TermState}, driven by the widget's per-tick {@code onUpdateListener}
     * and painted in its {@code background} draw lambda (both run only on the client, so the client-only
     * {@link Minecraft}/{@link Font} references never load server-side).
     */
    private static IWidget buildStatusTerminal(MissileLauncherMachine mte, int[] clientState) {
        return GuiTerminal.build(LAUNCH_X, STATUS_Y, LAUNCH_W, STATUS_H,
                () -> shownState(clientState).ordinal(),
                () -> statusText(mte, shownState(clientState)),
                () -> terminalColor(shownState(clientState)));
    }

    private static int terminalColor(MissileLauncherMachine.LaunchState state) {
        return switch (state) {
            case READY -> TERM_READY;
            case NO_TARGET, COOLDOWN, NO_MISSILE -> TERM_WARN;
            default -> TERM_ERROR; // UNFORMED / LOW_TIER / NO_ENERGY / MAINTENANCE
        };
    }

    //////////////////// guidance settings ////////////////////

    private static void buildGuidanceSettings(ModularPanel<?> panel, MissileLauncherMachine mte,
                                              PanelSyncManager sync) {
        StringSyncValue profile = new StringSyncValue(mte::getAttackProfileName, mte::setAttackProfileName);
        profile.allowC2S();
        sync.syncValue("attack_profile", 0, profile);
        StringSyncValue direction = new StringSyncValue(mte::getAttackDirectionName, mte::setAttackDirectionName);
        direction.allowC2S();
        sync.syncValue("attack_direction", 0, direction);
        IntSyncValue joinCap = new IntSyncValue(mte::getApproachJoinCap, mte::setApproachJoinCap).allowC2S();
        sync.syncValue("approach_join_cap", 0, joinCap);

        panel.child(settingLabel("wfcore.gui.launcher.attack_profile", SETTINGS_Y + 6));
        panel.child(cycleButton("attack_profile", SETTINGS_Y + 4, profile,
                new String[] { "AUTO", "SPEED", "BALANCED", "LOFT" },
                () -> profileLabel(mte.getAttackProfileName()), "wfcore.gui.launcher.attack_profile_tip"));
        panel.child(settingLabel("wfcore.gui.launcher.attack_direction", SETTINGS_Y + 6 + SETTING_ROW_H));
        panel.child(cycleButton("attack_direction", SETTINGS_Y + 4 + SETTING_ROW_H, direction,
                new String[] { "AUTO", "N", "NE", "E", "SE", "S", "SW", "W", "NW" },
                () -> directionLabel(mte.getAttackDirectionName()), "wfcore.gui.launcher.attack_direction_tip"));
        panel.child(settingLabel("wfcore.gui.launcher.approach_distance", SETTINGS_Y + 6 + 2 * SETTING_ROW_H));

        TextFieldWidget distance = new TextFieldWidget();
        distance.name("field_approach_join_cap").pos(SETTING_VALUE_X, SETTINGS_Y + 4 + 2 * SETTING_ROW_H)
                .size(SETTING_VALUE_W, FIELD_H);
        distance.setNumbers(MissileLauncherMachine.MIN_APPROACH_JOIN_CAP,
                MissileLauncherMachine.MAX_APPROACH_JOIN_CAP);
        distance.background(fieldBox()).hoverBackground(fieldBox()).syncHandler("approach_join_cap", 0);
        distance.tooltipDynamic(t -> t.addLine(Text.lang("wfcore.gui.launcher.approach_distance_tip")))
                .tooltipAutoUpdate(true);
        panel.child(distance);
    }

    private static TextWidget<?> settingLabel(String key, int y) {
        return new TextWidget<>(Text.lang(key)).pos(CONTENT_X, y).name("label_" + key);
    }

    private static ButtonWidget<?> cycleButton(String name, int y, StringSyncValue value, String[] values,
                                                java.util.function.Supplier<Component> label, String tooltip) {
        ButtonWidget<?> button = new ButtonWidget<>();
        button.name(name).pos(SETTING_VALUE_X, y).size(SETTING_VALUE_W, FIELD_H);
        button.background(fieldBox()).overlay(Text.dynamic(label));
        button.tooltipDynamic(t -> t.addLine(Text.lang(tooltip))).tooltipAutoUpdate(true);
        button.onMousePressed((context, mouseButton) -> {
            String current = value.getStringValue();
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(current)) {
                    index = i;
                    break;
                }
            }
            int step = mouseButton == 1 ? values.length - 1 : 1;
            value.setStringValue(values[(index + step) % values.length], true, true);
            return true;
        });
        return button;
    }

    private static Component profileLabel(String name) {
        return Component.translatable("wfcore.gui.launcher.profile_" + name.toLowerCase(Locale.ROOT));
    }

    private static Component directionLabel(String name) {
        return Component.translatable("wfcore.gui.launcher.direction_" + name.toLowerCase(Locale.ROOT));
    }

    //////////////////// telemetry ////////////////////

    private static void buildTelemetry(ModularPanel<?> panel, MissileLauncherMachine mte) {
        int x = TELEMETRY_X + INSET;
        int width = TELEMETRY_W - 2 * INSET;
        panel.child(GuiTerminal.buildMultiline(x, TELEMETRY_Y + INSET, width,
                TELEMETRY_H - 2 * INSET, () -> 0, () -> telemetryText(mte),
                () -> telemetryColor(mte)));
    }

    private static int telemetryColor(MissileLauncherMachine mte) {
        CompoundTag tag = mte.getTelemetrySnapshot();
        return tag.getBoolean("Active") && !tag.getBoolean("CanReach") ? TERM_WARN : TERM_READY;
    }

    private static Component telemetryText(MissileLauncherMachine mte) {
        StringBuilder text = new StringBuilder(Component.translatable("wfcore.gui.launcher.telemetry").getString());
        for (int i = 0; i < 8; i++) {
            String line = telemetryLine(mte, i).getString();
            if (!line.isEmpty()) text.append('\n').append(line);
        }
        text.append("\n\n").append(Component.translatable("wfcore.gui.launcher.event_log").getString());
        ListTag events = mte.getTelemetrySnapshot().getList("Events", Tag.TAG_COMPOUND);
        if (events.isEmpty()) {
            text.append('\n').append(Component.translatable("wfcore.gui.launcher.event_none").getString());
        } else {
            int first = Math.max(0, events.size() - 5);
            for (int i = first; i < events.size(); i++) {
                text.append("\n- ").append(eventLine(events.getCompound(i)).getString());
            }
        }
        return Component.literal(text.toString());
    }

    private static Component telemetryLine(MissileLauncherMachine mte, int line) {
        CompoundTag tag = mte.getTelemetrySnapshot();
        if (!tag.hasUUID("Id")) {
            return line == 0 ? Component.translatable("wfcore.gui.launcher.telemetry_idle") : Component.empty();
        }
        return switch (line) {
            case 0 -> Component.literal("ID " + tag.getUUID("Id").toString().substring(0, 8));
            case 1 -> tag.getBoolean("Active")
                    ? Component.translatable(tag.getBoolean("Simulated")
                            ? "wfcore.gui.launcher.telemetry_simulated" : "wfcore.gui.launcher.telemetry_real")
                    : Component.translatable("wfcore.gui.launcher.telemetry_complete");
            case 2 -> tag.getBoolean("Active") ? Component.literal("Phase: " + tag.getString("Phase"))
                    : Component.empty();
            case 3 -> tag.getBoolean("Active") ? Component.literal(String.format(Locale.ROOT, "Pos %.0f, %.0f, %.0f",
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"))) : Component.empty();
            case 4 -> tag.getBoolean("Active") ? Component.literal(String.format(Locale.ROOT, "Speed %.2f b/t",
                    tag.getDouble("Speed"))) : Component.empty();
            case 5 -> tag.getBoolean("Active") ? Component.literal("Fuel " + tag.getInt("Fuel") + "/" +
                    tag.getInt("FuelCapacity")) : Component.empty();
            case 6 -> tag.getBoolean("Active") && tag.getInt("Eta") >= 0
                    ? Component.literal(String.format(Locale.ROOT, "ETA %.1fs", tag.getInt("Eta") / 20.0))
                    : Component.empty();
            case 7 -> tag.getBoolean("Active") && !tag.getBoolean("CanReach")
                    ? Component.translatable("wfcore.gui.launcher.telemetry_unreachable") : Component.empty();
            default -> Component.empty();
        };
    }

    private static Component eventLine(CompoundTag event) {
        String type = event.getString("Type").toLowerCase(Locale.ROOT);
        String suffix = event.getBoolean("Simulated") ? " [sim]" : "";
        String detail = event.getString("Detail");
        Component message = Component.translatable("wfcore.gui.launcher.event_" + type);
        return message.copy().append(suffix).append(detail.isEmpty() ? "" : ": " + detail);
    }

}
