package com.norwood.wfcore.common.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.widget.ParentWidget;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A reusable terminal-style status readout: a black CRT box with a green prompt, the status text typed out one
 * character at a time whenever the state changes, a blinking block cursor, and two-line word-wrapping. Shared
 * by the Missile Launch Silo and the Interceptor Battery so both read identically.
 * <p>
 * All animation state lives client-side in a captured {@link Anim}, driven by the widget's per-tick
 * {@code onUpdateListener} and painted in its {@code background} draw lambda — both run only on the client, so
 * the client-only {@link Minecraft}/{@link Font} references never load server-side.
 */
public final class GuiTerminal {

    private static final int TERM_BG = 0xFF07100A;     // near-black with a faint green cast
    private static final int TERM_BORDER = 0xFF1C5E2A; // dim phosphor-green frame
    private static final int TERM_PROMPT = 0xFF2E8B3A; // ">" prompt, one shade under the text
    /** Characters revealed per client tick while a new status types itself out (20/s at 1/tick). */
    private static final int CHARS_PER_TICK = 1;
    /** Cursor blink half-period in ticks (on for 6, off for 6 => ~0.6s cycle). */
    private static final int BLINK = 6;

    private GuiTerminal() {}

    /**
     * Builds a terminal readout at (x, y) of the given size. {@code stateKey} identifies the current status
     * category — the text re-types itself from scratch whenever it changes, while an in-place text change with
     * the same key (e.g. a countdown) updates without a retype. {@code status} supplies the line to show and
     * {@code color} its colour.
     */
    public static IWidget build(int x, int y, int w, int h, IntSupplier stateKey,
                                Supplier<Component> status, IntSupplier color) {
        Anim st = new Anim();
        return new ParentWidget<>().name("status_terminal").pos(x, y).size(w, h)
                .onUpdateListener(wd -> tick(st, stateKey, status, color))
                .background((ctx, bx, by, bw, bh, theme) -> draw(ctx, bx, by, bw, bh, st));
    }

    private static void tick(Anim st, IntSupplier stateKey, Supplier<Component> status, IntSupplier color) {
        String target = status.get().getString();
        int key = stateKey.getAsInt();
        if (key != st.lastState) {
            st.lastState = key;
            st.text = target;
            st.revealed = 0; // a genuinely new status: type it out from scratch
        } else if (!target.equals(st.text)) {
            st.text = target; // same status, only a detail changed (e.g. a countdown) -> no retype
            st.revealed = target.length();
        }
        st.color = color.getAsInt();
        if (st.revealed < 200) {
            st.revealed += CHARS_PER_TICK;
        }
        st.blink++;
    }

    private static void draw(GuiContext ctx, int x, int y, int w, int h, Anim st) {
        var g = ctx.getGraphics();
        GuiDraw.drawRect(g, x, y, w, h, TERM_BG);
        GuiDraw.drawRect(g, x, y, w, 1, TERM_BORDER);
        GuiDraw.drawRect(g, x, y + h - 1, w, 1, TERM_BORDER);
        GuiDraw.drawRect(g, x, y, 1, h, TERM_BORDER);
        GuiDraw.drawRect(g, x + w - 1, y, 1, h, TERM_BORDER);

        Font font = Minecraft.getInstance().font;
        int pad = 4;
        int promptW = font.width("> ");
        int w1 = w - 2 * pad - promptW; // line 1 shares its row with the prompt
        int w2 = w - 2 * pad;           // line 2 spans the full inner width
        String[] lines = wrap(st.text, w1, w2, font);
        int len1 = lines[0].length();
        int len2 = lines[1].length();
        int total = len1 + len2;
        int r = Math.min(st.revealed, total);

        int line1x = x + pad + promptW;
        int line2x = x + pad;
        int line1y = y + 5;
        int line2y = y + 15;

        GuiDraw.drawText(g, "> ", x + pad, line1y, 1f, TERM_PROMPT, false);
        String vis1 = lines[0].substring(0, Math.min(r, len1));
        GuiDraw.drawText(g, vis1, line1x, line1y, 1f, st.color, false);
        String vis2 = "";
        if (r > len1 && len2 > 0) {
            vis2 = lines[1].substring(0, Math.min(r - len1, len2));
            GuiDraw.drawText(g, vis2, line2x, line2y, 1f, st.color, false);
        }

        boolean typing = st.revealed < total;
        if (typing || (st.blink % (BLINK * 2)) < BLINK) {
            int cx = r <= len1 ? line1x + font.width(vis1) : line2x + font.width(vis2);
            int cy = r <= len1 ? line1y : line2y;
            GuiDraw.drawRect(g, cx + 1, cy - 1, 5, 9, st.color);
        }
    }

    /**
     * Greedy word-wrap into at most two lines: line 1 fits {@code w1} (it shares its row with the prompt),
     * line 2 fits {@code w2} and is ellipsised if the remainder still overflows.
     */
    private static String[] wrap(String text, int w1, int w2, Font font) {
        if (text.isEmpty() || font.width(text) <= w1) {
            return new String[] { text, "" };
        }
        String[] words = text.split(" ");
        StringBuilder l1 = new StringBuilder();
        int i = 0;
        while (i < words.length) {
            String cand = l1.length() == 0 ? words[i] : l1 + " " + words[i];
            if (l1.length() > 0 && font.width(cand) > w1) {
                break;
            }
            l1.setLength(0);
            l1.append(cand);
            i++;
            if (font.width(l1.toString()) > w1) {
                break; // a lone word already overflows the line; keep it and move on
            }
        }
        StringBuilder l2 = new StringBuilder();
        while (i < words.length) {
            if (l2.length() > 0) {
                l2.append(' ');
            }
            l2.append(words[i]);
            i++;
        }
        String line2 = l2.toString();
        if (font.width(line2) > w2) {
            while (line2.length() > 1 && font.width(line2 + "…") > w2) {
                line2 = line2.substring(0, line2.length() - 1);
            }
            line2 = line2 + "…";
        }
        return new String[] { l1.toString(), line2 };
    }

    /** Client-side animation state (typed-out progress + cursor blink). */
    private static final class Anim {
        String text = "";
        int color = 0xFFFF5555;
        int revealed = 0;   // characters currently typed out
        int blink = 0;      // free-running tick counter for the cursor
        int lastState = -1; // last state key, to detect when to restart the type-out
    }
}
