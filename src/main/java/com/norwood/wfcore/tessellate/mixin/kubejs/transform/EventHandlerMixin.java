package com.norwood.wfcore.tessellate.mixin.kubejs.transform;

import org.spongepowered.asm.mixin.Mixin;

/** Empty: anchors {@code TessellateMixinPlugin.postApply} => {@code KubeJsTransformer}. */
@Mixin(targets = "dev.latvian.mods.kubejs.event.EventHandler", remap = false)
public abstract class EventHandlerMixin {
}
