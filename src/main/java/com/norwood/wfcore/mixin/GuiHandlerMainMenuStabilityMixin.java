package com.norwood.wfcore.mixin;

import com.norwood.wfcore.integration.replaymod.ReplayFancyMenuCompat;
import com.replaymod.replay.handler.GuiHandler;
import com.replaymod.replay.handler.GuiHandler.MainMenuButtonPosition;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps FancyMenu's title-screen customizations stable across game restarts when ReplayMod / ReForgedPlay
 * is installed. See {@link ReplayFancyMenuCompat} for the full rationale.
 *
 * <p>ReplayMod's only main-menu-button placement that displaces the vanilla buttons is {@code BIG}: it
 * calls {@code moveAllButtonsInRect} to shove the vanilla title-screen buttons up by 24px. FancyMenu keys
 * each vanilla widget's saved customization by that widget's {@code (x, y)}, so the shift invalidates the
 * user's saved layout and it reverts on the next launch. Here we downgrade {@code BIG} to a fixed corner
 * ({@code TOP_RIGHT}) - which never moves the other buttons and lands at a deterministic position - but
 * ONLY while FancyMenu customization is actually enabled for the title screen. In every other situation
 * (FancyMenu absent, title screen not customized, any non-{@code BIG} position) ReplayMod behaves exactly
 * as before.
 *
 * <p>{@code remap = false}: the target is a mod class, and {@code properInjectIntoMainMenu} plus the
 * {@code Screen} in its descriptor keep their names at runtime, so no Minecraft refmap remapping applies.
 *
 * <p>This mixin is applied against the shipped ReForgedPlay jar. When that mod is absent (e.g. the dev
 * runtime, where ReForgedPlay is only a compile-time dependency) the target class is missing and Mixin
 * silently skips this class, exactly like the other optional-mod mixins in {@code wfcore.mixins.json}.
 *
 * <p>Not covered: the legacy button path ({@code legacyInjectIntoMainMenu}, active only when ReplayMod's
 * {@code legacyMainMenuButton} setting is on, which is off by default) has its own {@code BIG} handling and
 * is intentionally left untouched.
 */
@Mixin(value = GuiHandler.class, remap = false)
public abstract class GuiHandlerMainMenuStabilityMixin {

    @ModifyVariable(
            method = "properInjectIntoMainMenu(Lnet/minecraft/client/gui/screens/Screen;)V",
            at = @At("STORE"),
            ordinal = 0)
    private MainMenuButtonPosition wfcore$stabilizeMainMenuButton(MainMenuButtonPosition buttonPosition, Screen screen) {
        if (buttonPosition == MainMenuButtonPosition.BIG
                && ReplayFancyMenuCompat.shouldStabilizeMainMenuButton(screen)) {
            return MainMenuButtonPosition.TOP_RIGHT;
        }
        return buttonPosition;
    }
}
