package com.norwood.wfcore.tessellate.mixin.ae2things.transform;

import org.spongepowered.asm.mixin.Mixin;

/** Empty: anchors {@code TessellateMixinPlugin.postApply} => {@code Ae2ThingsTransformer}. */
@Mixin(targets = "io.github.projectet.ae2things.util.StorageManager", remap = false)
public abstract class StorageManagerMixin {
}
