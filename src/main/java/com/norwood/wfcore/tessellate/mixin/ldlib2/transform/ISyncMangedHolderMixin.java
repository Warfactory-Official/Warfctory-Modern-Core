package com.norwood.wfcore.tessellate.mixin.ldlib2.transform;

import org.spongepowered.asm.mixin.Mixin;

/** Empty: anchors {@code TessellateMixinPlugin.postApply} => {@code Ldlib2Transformer}. */
@Mixin(targets = "com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder", remap = false)
public interface ISyncMangedHolderMixin {
}
