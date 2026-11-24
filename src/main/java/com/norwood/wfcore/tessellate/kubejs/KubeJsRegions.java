package com.norwood.wfcore.tessellate.kubejs;

import com.mojang.logging.LogUtils;
import com.norwood.wfcore.tessellate.RegionThreads;
import com.norwood.wfcore.tessellate.rhino.CallDeferral;
import com.norwood.wfcore.tessellate.rhino.ContextLock;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@code patches/kubejs/0001}: JS runs on the main thread; a region worker hands events over, or runs them inline while the
 * script Context is free within a bounded wait (main may hold it while waiting for this region to go idle). KubeJS types
 * are bound by name, no compile dependency; mismatch => {@link IllegalStateException}.
 */
public final class KubeJsRegions {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long LOCK_WAIT_MS = 20L;
    private static final String K = "dev.latvian.mods.kubejs.";

    private static final Object PASS;
    private static final MethodHandle HAS_LISTENERS;
    private static final MethodHandle HAS_RESULT;
    private static final MethodHandle DISPATCH;
    private static final MethodHandle SCRIPT_TYPE;
    private static final MethodHandle CONSOLE;
    private static final MethodHandle CONTEXT_FACTORY;
    private static final MethodHandle LISTENERS;
    private static final MethodHandle PROXY_CONTEXT;

    /** Context that loaded the scripts: every handler proxy is bound to it, so its call lock serialises all JS. */
    private static final Map<Object, Object> LOAD_CONTEXT = new WeakHashMap<>();

    static {
        try {
            ClassLoader loader = KubeJsRegions.class.getClassLoader();
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> handler = Class.forName(K + "event.EventHandler", true, loader);
            Class<?> holder = Class.forName(K + "script.ScriptTypeHolder", true, loader);
            Class<?> scriptType = Class.forName(K + "script.ScriptType", true, loader);
            Class<?> console = Class.forName(K + "script.ConsoleJS", true, loader);
            Class<?> result = Class.forName(K + "event.EventResult", true, loader);
            Class<?> event = Class.forName(K + "event.KubeEvent", true, loader);
            Class<?> listeners = Class.forName(K + "plugin.builtin.wrapper.NativeEventWrapper$Listeners", true, loader);
            Class<?> adapter = Class.forName("dev.latvian.mods.rhino.VMBridge$AdapterInvocationHandler", true, loader);
            PASS = lookup.findStaticGetter(result, "PASS", result).invoke();
            HAS_LISTENERS = lookup.findVirtual(handler, "hasListeners", MethodType.methodType(boolean.class))
                    .asType(MethodType.methodType(boolean.class, Object.class));
            HAS_RESULT = lookup.findVirtual(handler, "tessellate$hasResult", MethodType.methodType(boolean.class))
                    .asType(MethodType.methodType(boolean.class, Object.class));
            DISPATCH = lookup.findVirtual(handler, "tessellate$dispatch", MethodType.methodType(result, holder, Object.class, event))
                    .asType(MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object.class));
            SCRIPT_TYPE = lookup.findVirtual(holder, "kjs$getScriptType", MethodType.methodType(scriptType))
                    .asType(MethodType.methodType(Object.class, Object.class));
            CONSOLE = lookup.findGetter(scriptType, "console", console).asType(MethodType.methodType(Object.class, Object.class));
            CONTEXT_FACTORY = lookup.findGetter(console, "contextFactory", java.lang.ref.WeakReference.class)
                    .asType(MethodType.methodType(java.lang.ref.WeakReference.class, Object.class));
            LISTENERS = lookup.findVirtual(listeners, "listeners", MethodType.methodType(Collection.class))
                    .asType(MethodType.methodType(Collection.class, Object.class));
            PROXY_CONTEXT = lookup.findVirtual(adapter, "cx", MethodType.methodType(Class.forName("dev.latvian.mods.rhino.Context", true, loader)))
                    .asType(MethodType.methodType(Object.class, Object.class));
        } catch (Throwable mismatch) {
            throw new IllegalStateException("KubeJS API mismatch", mismatch);
        }
    }

    private KubeJsRegions() {
    }

    public static boolean onRegionThread() {
        return RegionThreads.isRegionThread();
    }

    /** Console source-line lookup enters a Context: skipped on region threads (null factory => no enter). */
    public static Object unlessRegionThread(Object factory) {
        return RegionThreads.isRegionThread() ? null : factory;
    }

    public static Object loadContext(Object factory, Object context) {
        synchronized (LOAD_CONTEXT) {
            LOAD_CONTEXT.put(factory, context);
        }
        return context;
    }

    private static Object loadContextOf(Object factory) {
        synchronized (LOAD_CONTEXT) {
            return LOAD_CONTEXT.get(factory);
        }
    }

    /** Called from the {@code KubeJSContextFactory} constructor. */
    public static void install(Object factory) {
        ((CallDeferral) factory).tessellate$setCallDeferrer((method, call) -> {
            Object cx = loadContextOf(factory);
            if (!RegionThreads.isRegionThread() || cx != null && ((ContextLock) cx).tessellate$callLock().isHeldByCurrentThread()) {
                return false;
            }
            RegionThreads.executeOnMain("KubeJS callback " + method.getDeclaringClass().getSimpleName() + "." + method.getName(), () -> {
                try {
                    call.run();
                } catch (Throwable ex) {
                    LOGGER.error("Deferred KubeJS callback {}.{} failed", method.getDeclaringClass().getSimpleName(), method.getName(), ex);
                }
            });
            return true;
        });
    }

    private static boolean tryLock(Object context) {
        try {
            return context != null && ((ContextLock) context).tessellate$callLock().tryLock(LOCK_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean deferrableToMain(Object event) {
        return !event.getClass().getName().equals(K + "level.ExplosionKubeEvent$After");
    }

    /** {@code EventHandler.postInternal} on a region thread. */
    public static Object post(Object handler, Object type, Object extraId, Object event) {
        try {
            if (!(boolean) HAS_LISTENERS.invokeExact(handler)) {
                return PASS;
            }
            if (!(boolean) HAS_RESULT.invokeExact(handler) && deferrableToMain(event)) {
                RegionThreads.executeOnMain("KubeJS event " + handler, () -> dispatch(handler, type, extraId, event));
                return PASS;
            }
            Object console = CONSOLE.invokeExact(SCRIPT_TYPE.invokeExact(type));
            java.lang.ref.WeakReference<?> ref = (java.lang.ref.WeakReference<?>) CONTEXT_FACTORY.invokeExact(console);
            Object factory = ref == null ? null : ref.get();
            Object context = factory == null ? null : loadContextOf(factory);
            if (tryLock(context)) {
                try {
                    return dispatch(handler, type, extraId, event);
                } finally {
                    ((ContextLock) context).tessellate$callLock().unlock();
                }
            }
            LOGGER.warn("KubeJS event '{}' fired on a region thread while scripts were busy; result ignored", handler);
            RegionThreads.executeOnMain("KubeJS event " + handler, () -> dispatch(handler, type, extraId, event));
            return PASS;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static Object dispatch(Object handler, Object type, Object extraId, Object event) {
        try {
            return (Object) DISPATCH.invokeExact(handler, type, extraId, event);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** {@code NativeEventWrapper.Listeners.accept} head: true => handled here. */
    @SuppressWarnings("unchecked")
    public static boolean nativeEvent(Object listeners, Object event) {
        if (!RegionThreads.isRegionThread()) {
            return false;
        }
        try {
            Collection<Consumer<Object>> all = (Collection<Consumer<Object>>) (Collection<?>) (Collection) LISTENERS.invokeExact(listeners);
            Object context = null;
            for (Consumer<Object> l : all) {
                if (Proxy.isProxyClass(l.getClass())) {
                    Object h = Proxy.getInvocationHandler(l);
                    if (h.getClass().getName().equals("dev.latvian.mods.rhino.VMBridge$AdapterInvocationHandler")) {
                        context = (Object) PROXY_CONTEXT.invokeExact(h);
                        break;
                    }
                }
            }
            if (context == null) {
                for (Consumer<Object> l : all) {
                    l.accept(event);
                }
                return true;
            }
            if (tryLock(context)) {
                try {
                    for (Consumer<Object> l : all) {
                        l.accept(event);
                    }
                } finally {
                    ((ContextLock) context).tessellate$callLock().unlock();
                }
            } else {
                LOGGER.warn("Native event {} fired on a region thread while scripts were busy; changes ignored", event.getClass().getSimpleName());
                RegionThreads.executeOnMain("KubeJS native event", () -> {
                    for (Consumer<Object> l : all) {
                        l.accept(event);
                    }
                });
            }
            return true;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
