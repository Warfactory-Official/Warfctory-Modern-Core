package com.norwood.wfcore.tessellate.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** {@code patches/lithium-tessellate/0001}: {@code ObjectOpenHashSet} touched by every hopper (un)registration on any region worker. */
@Mixin(targets = "net.caffeinemc.mods.lithium.common.util.deduplication.LithiumInterner", remap = false)
public abstract class LithiumInternerMixin {

    @WrapMethod(method = "getCanonical")
    private Object wfcore$getCanonical(Object value, Operation<Object> original) {
        synchronized (this) {
            return original.call(value);
        }
    }

    @WrapMethod(method = "deleteCanonical")
    private void wfcore$deleteCanonical(Object value, Operation<Void> original) {
        synchronized (this) {
            original.call(value);
        }
    }
}
