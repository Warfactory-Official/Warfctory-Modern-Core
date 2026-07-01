package com.norwood.wfcore.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * mafglib's GuiTextFieldGeneric#getCursorPosition is just {@code return vanillaGetCursor()}.
 * Remapped to official names in dev, that inner call collides with this same-named override
 * and self-dispatches into a StackOverflow. Read the EditBox field directly instead.
 */
@Mixin(targets = "fi.dy.masa.malilib.gui.GuiTextFieldGeneric", remap = false)
public abstract class GuiTextFieldGenericCursorMixin {

    /**
     * @author MrNorwood
     * @reason Break mafglib's self-recursive cursor getter under official mappings.
     */
    @Overwrite
    public int getCursorPosition() {
        return ((EditBoxAccessor) (Object) this).wfcore$getCursorPos();
    }
}
