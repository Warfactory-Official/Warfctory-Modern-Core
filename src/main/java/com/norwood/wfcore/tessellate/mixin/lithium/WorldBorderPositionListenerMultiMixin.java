package com.norwood.wfcore.tessellate.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/** {@code patches/lithium-tessellate/0004}: {@code WeakHashMap} iterated on main while workers {@code add}. */
@Mixin(targets = "net.caffeinemc.mods.lithium.common.world.listeners.WorldBorderPositionListenerMulti", remap = false)
public abstract class WorldBorderPositionListenerMultiMixin {

    @WrapMethod(method = "add")
    private void wfcore$add(@Coerce Object listener, Operation<Void> original) {
        synchronized (this) {
            original.call(listener);
        }
    }

    @WrapMethod(method = "onAreaReplaced")
    private void wfcore$onAreaReplaced(WorldBorder border, Operation<Void> original) {
        synchronized (this) {
            original.call(border);
        }
    }

    @WrapMethod(method = "onBorderSizeSet")
    private void wfcore$onBorderSizeSet(WorldBorder border, double size, Operation<Void> original) {
        synchronized (this) {
            original.call(border, size);
        }
    }

    @WrapMethod(method = "onBorderSizeLerping")
    private void wfcore$onBorderSizeLerping(WorldBorder border, double fromSize, double toSize, long time, Operation<Void> original) {
        synchronized (this) {
            original.call(border, fromSize, toSize, time);
        }
    }

    @WrapMethod(method = "onBorderCenterSet")
    private void wfcore$onBorderCenterSet(WorldBorder border, double centerX, double centerZ, Operation<Void> original) {
        synchronized (this) {
            original.call(border, centerX, centerZ);
        }
    }
}
