package com.norwood.wfcore.mixin;

import java.util.List;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(targets = "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory$1ProxyRecipeWidget",
        remap = false)
public class MultiblockInfoSlotBoundsMixin {


    private static final Widget WFCORE$OFFSCREEN =
            new Widget(Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, 0, 0);

    @Redirect(method = "lambda$getSlotUnderMouse$0",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"))
    private static Object wfcore$safeWidgetGet(List<?> widgets, int index) {
        if (index < 0 || index >= widgets.size()) {
            return WFCORE$OFFSCREEN;
        }
        return widgets.get(index);
    }
}
