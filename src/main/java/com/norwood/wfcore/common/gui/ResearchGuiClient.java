package com.norwood.wfcore.common.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Client-only helpers for the Research Unit GUI. Kept in its own class so the dedicated server never resolves
 * the {@link Minecraft} reference: it is loaded lazily, only from code paths that run on the client (a
 * {@code data.isClient()} branch in the panel builder, or a tooltip lambda that only ever fires client-side) -
 * the same isolation the codebase uses elsewhere to keep client rendering off the server.
 */
final class ResearchGuiClient {

    private ResearchGuiClient() {}

    /** Total top+bottom breathing room left around the panel so it never touches the screen edges / JEI. */
    private static final int VERTICAL_MARGIN = 40;

    /**
     * The research panel's height for the current GUI scale: the fixed design height, grown to fill whatever
     * vertical space a smaller GUI scale frees up, clamped to {@code maxH} (and never below {@code designH}, so a
     * very small screen simply keeps today's size and overflows the same way it always did).
     */
    static int panelHeight(int designH, int maxH) {
        int available = Minecraft.getInstance().getWindow().getGuiScaledHeight() - VERTICAL_MARGIN;
        return Math.max(designH, Math.min(available, maxH));
    }

    /** A stack's full vanilla tooltip lines (name + description), exactly as shown when hovering it in a slot. */
    static List<Component> itemTooltip(ItemStack stack) {
        try {
            return stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL);
        } catch (RuntimeException ignored) {
            // A rare item that assumes a non-null player / render context - fall back to just its name.
            return List.of(stack.getHoverName());
        }
    }
}
