package com.norwood.wfcore.tessellate.mixin.rhino;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.wfcore.tessellate.rhino.ContextLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;

/** Sole caller of {@code InterfaceAdapter.invoke}: serialises it on the Context call lock; void calls may be deferred. */
@Mixin(targets = "dev.latvian.mods.rhino.VMBridge$AdapterInvocationHandler", remap = false)
public abstract class AdapterInvocationHandlerMixin {

    @WrapOperation(method = "invoke", at = @At(value = "INVOKE", target =
            "Ldev/latvian/mods/rhino/InterfaceAdapter;invoke(Ldev/latvian/mods/rhino/Context;Ljava/lang/Object;Ldev/latvian/mods/rhino/Scriptable;Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object wfcore$serialisedInvoke(@Coerce Object adapter, @Coerce Object cx, Object target, @Coerce Object topScope,
                                           Object proxy, Method method, Object[] args, Operation<Object> original) {
        ContextLock context = (ContextLock) cx;
        BiPredicate<Method, Runnable> deferrer = context.tessellate$callDeferrer();
        if (deferrer != null && method.getReturnType() == void.class
                && deferrer.test(method, () -> locked(context, original, adapter, cx, target, topScope, proxy, method, args))) {
            return null;
        }
        return locked(context, original, adapter, cx, target, topScope, proxy, method, args);
    }

    private static Object locked(ContextLock context, Operation<Object> original, Object adapter, Object cx, Object target,
                                 Object topScope, Object proxy, Method method, Object[] args) {
        ReentrantLock lock = context.tessellate$callLock();
        lock.lock();
        try {
            return original.call(adapter, cx, target, topScope, proxy, method, args);
        } finally {
            lock.unlock();
        }
    }
}
