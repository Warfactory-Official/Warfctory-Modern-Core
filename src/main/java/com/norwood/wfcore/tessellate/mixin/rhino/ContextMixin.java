package com.norwood.wfcore.tessellate.mixin.rhino;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.norwood.wfcore.tessellate.rhino.CallDeferral;
import com.norwood.wfcore.tessellate.rhino.ContextLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;

@Mixin(targets = "dev.latvian.mods.rhino.Context", remap = false)
public abstract class ContextMixin implements ContextLock {

    @Unique
    private final ReentrantLock tessellate$callLock = new ReentrantLock();

    @Unique
    private Object tessellate$factory;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$captureFactory(@Coerce Object factory, CallbackInfo ci) {
        tessellate$factory = factory;
    }

    @Override
    public ReentrantLock tessellate$callLock() {
        return tessellate$callLock;
    }

    @Override
    public BiPredicate<Method, Runnable> tessellate$callDeferrer() {
        return ((CallDeferral) tessellate$factory).tessellate$callDeferrer();
    }

    @WrapMethod(method = "callSync")
    private Object wfcore$callSync(@Coerce Object callable, @Coerce Object scope, @Coerce Object thisObj, Object[] args,
                                   Operation<Object> original) {
        tessellate$callLock.lock();
        try {
            return original.call(callable, scope, thisObj, args);
        } finally {
            tessellate$callLock.unlock();
        }
    }
}
