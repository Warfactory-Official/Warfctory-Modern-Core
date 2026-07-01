package com.norwood.wfcore.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.Mth;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes overflow in forgematica lib. Otherwise it Stack overflows.
 * For fucks sake the fact I need to go to those lengths to FIX A MOD MAKING FUCKING MULTIS is insane
 */
@Mixin(EditBox.class)
public abstract class EditBoxCursorRecursionMixin {

    @Shadow
    private int cursorPos;
    @Shadow
    private String value;

    @Redirect(
              method = "moveCursorTo(I)V",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/components/EditBox;setCursorPosition(I)V"))
    private void wfcore$clampCursorWithoutVirtualDispatch(EditBox instance, int pos) {
        this.cursorPos = Mth.clamp(pos, 0, this.value.length());
    }
}
