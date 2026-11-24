package com.norwood.wfcore.tessellate.mixin.umc.transform;

import org.spongepowered.asm.mixin.Mixin;

/** Empty: anchors {@code TessellateMixinPlugin.postApply} => {@code UmcTransformer}. */
@Mixin(targets = "cam72cam.mod.entity.sync.EntitySync", remap = false)
public abstract class EntitySyncMixin {
}
