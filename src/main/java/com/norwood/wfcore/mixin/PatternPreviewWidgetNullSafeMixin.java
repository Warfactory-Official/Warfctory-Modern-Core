package com.norwood.wfcore.mixin;

import java.util.Map;

import com.gregtechceu.gtceu.api.gui.widget.PatternPreviewWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(value = PatternPreviewWidget.class, remap = false)
public class PatternPreviewWidgetNullSafeMixin {

    @Redirect(method = "onPosSelected",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object wfcore$nullSafePredicateMapGet(Map<Object, Object> map, Object key) {
        return map == null ? null : map.get(key);
    }
}
