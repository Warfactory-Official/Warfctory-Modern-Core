package com.norwood.wfcore.integration.replaymod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.fml.ModList;

/**
 * Bridge that lets our ReplayMod / ReForgedPlay mixin ask FancyMenu whether the title screen currently
 * has customization enabled, without a hard compile- or run-time dependency on FancyMenu.
 *
 * <p>Why this exists: FancyMenu identifies every vanilla widget it customizes by that widget's on-screen
 * {@code (x, y)} position (see FancyMenu's {@code ScreenWidgetDiscoverer#generateBaseId}, which builds the
 * widget id from {@code abs(x) + "" + abs(y)}). ReplayMod's {@code BIG} main-menu-button placement pushes
 * the vanilla title-screen buttons upward by 24px (via {@code GuiHandler#moveAllButtonsInRect}) to make
 * room for its own button. That shift changes the vanilla buttons' positions, so the ids FancyMenu saved
 * no longer match and the user's saved title-screen layout partially reverts on the next launch. The mixin
 * uses this class to detect the situation and force ReplayMod's button into a fixed corner that never moves
 * the vanilla buttons, keeping FancyMenu's ids - and therefore the saved customizations - stable.
 *
 * <p>FancyMenu is accessed reflectively for two reasons: Curse Maven cannot serve FancyMenu (third-party
 * sharing is disabled), and this keeps FancyMenu an entirely optional dependency. Any failure - FancyMenu
 * absent, API changed, called too early - falls back to {@code false}, i.e. ReplayMod behaves exactly as
 * it does without this patch.
 */
public final class ReplayFancyMenuCompat {

    private static final String FANCYMENU_MOD_ID = "fancymenu";
    private static final String SCREEN_CUSTOMIZATION_CLASS = "de.keksuccino.fancymenu.customization.ScreenCustomization";
    private static final String IS_CUSTOMIZATION_ENABLED_METHOD = "isCustomizationEnabledForScreen";

    /** null until first queried; cached afterwards (title-screen init is single-threaded on the client). */
    private static Boolean fancyMenuLoaded;
    private static boolean handleResolved;
    private static MethodHandle isCustomizationEnabledForScreen;

    private ReplayFancyMenuCompat() {}

    /**
     * @return {@code true} only when {@code screen} is the vanilla title screen AND FancyMenu is installed
     *         with customization currently enabled for it. Returns the safe default {@code false} on any
     *         error or when FancyMenu is not present.
     */
    public static boolean shouldStabilizeMainMenuButton(Screen screen) {
        if (!(screen instanceof TitleScreen)) {
            return false;
        }
        if (!isFancyMenuLoaded()) {
            return false;
        }
        MethodHandle handle = resolveHandle();
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invoke(screen);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isFancyMenuLoaded() {
        Boolean loaded = fancyMenuLoaded;
        if (loaded == null) {
            try {
                loaded = ModList.get().isLoaded(FANCYMENU_MOD_ID);
            } catch (Throwable t) {
                loaded = Boolean.FALSE;
            }
            fancyMenuLoaded = loaded;
        }
        return loaded;
    }

    private static MethodHandle resolveHandle() {
        if (!handleResolved) {
            handleResolved = true;
            try {
                Class<?> screenCustomization = Class.forName(SCREEN_CUSTOMIZATION_CLASS);
                isCustomizationEnabledForScreen = MethodHandles.lookup().findStatic(
                        screenCustomization,
                        IS_CUSTOMIZATION_ENABLED_METHOD,
                        MethodType.methodType(boolean.class, Screen.class));
            } catch (Throwable t) {
                isCustomizationEnabledForScreen = null;
            }
        }
        return isCustomizationEnabledForScreen;
    }
}
