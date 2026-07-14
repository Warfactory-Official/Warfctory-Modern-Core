package com.norwood.wfcore.common.gui;

import brachy.modularui.screen.UISettings;
import brachy.modularui.utils.Rectangle;

/**
 * Hides the recipe-viewer overlay (search bar + item list) on screens that have no item slots. One call is
 * not enough for every viewer, because ModularUI's integrations differ:
 * <ul>
 * <li><b>JEI</b>: {@code RecipeViewerSettings.disable()} works — the fork's {@code JeiScreenHandler.apply}
 * checks {@code isEnabled} and returns null gui properties, hiding the whole overlay.</li>
 * <li><b>EMI / REI</b>: the fork only forwards <i>exclusion areas</i> to them and never consults
 * {@code isEnabled} for panel visibility, so EMI's sidebars stay up on any container screen. A
 * screen-covering exclusion area starves the panels of layout space, which hides them.</li>
 * </ul>
 */
public final class GuiRecipeViewer {

    /** Far larger than any real screen, but small enough that x+width arithmetic can't overflow. */
    private static final int COVER = 1_000_000;

    private GuiRecipeViewer() {}

    /** Hides the JEI/EMI/REI overlay for the screen built with {@code settings} (see class docs for how). */
    public static void hideOverlay(UISettings settings) {
        settings.getRecipeViewerSettings().disable();
        settings.getRecipeViewerSettings().addExclusionArea(new Rectangle(-COVER / 2, -COVER / 2, COVER, COVER));
    }
}
