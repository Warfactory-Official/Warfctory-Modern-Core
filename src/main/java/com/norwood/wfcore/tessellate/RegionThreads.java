package com.norwood.wfcore.tessellate;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;

/**
 * Tessellate region access by reflection, no compile dependency. Loaded only from mixins that the plugin applies
 * with Tessellate present; signature mismatch => {@link IllegalStateException} at first use.
 */
public final class RegionThreads {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TRACKER = "org.texboobcat.tessellate.region.RegionTracker";
    private static final String API = "org.texboobcat.tessellate.api.TessellateApi";
    private static final String WORKERS = "org.texboobcat.tessellate.region.RegionWorkers";
    private static final String INDEX = "org.texboobcat.tessellate.region.LevelRegionIndex";

    /** Linked lambda classes, not MethodHandle fields: handle targets are not constants for the JIT. */
    private static final Function<ServerLevel, Object> INDEX_OF;
    private static final ObjLongConsumer<Object> LEASE_CHUNK;
    private static final Consumer<Runnable> EXECUTE_ON_MAIN;
    private static final BooleanSupplier IS_REGION_THREAD;
    private static final BooleanSupplier IS_WORKER_THREAD;
    private static final BiPredicate<ServerLevel, BlockPos> OWNS;
    private static final RegionWork EXECUTE_ON_REGION;

    @FunctionalInterface
    private interface RegionWork {
        void run(ServerLevel level, BlockPos pos, Runnable work);
    }

    static {
        try {
            ClassLoader loader = RegionThreads.class.getClassLoader();
            Class<?> tracker = Class.forName(TRACKER, true, loader);
            Class<?> index = Class.forName(INDEX, true, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType indexOf = MethodType.methodType(index, ServerLevel.class);
            MethodType leaseChunk = MethodType.methodType(void.class, index, long.class);
            INDEX_OF = link(Function.class, "apply", MethodType.methodType(Object.class, Object.class),
                    lookup.findStatic(tracker, "index", indexOf), indexOf);
            LEASE_CHUNK = link(ObjLongConsumer.class, "accept", MethodType.methodType(void.class, Object.class, long.class),
                    lookup.findVirtual(index, "leaseChunk", MethodType.methodType(void.class, long.class)), leaseChunk);
            Class<?> api = Class.forName(API, true, loader);
            MethodType onMain = MethodType.methodType(void.class, Runnable.class);
            EXECUTE_ON_MAIN = link(Consumer.class, "accept", MethodType.methodType(void.class, Object.class),
                    lookup.findStatic(api, "executeOnMainThread", onMain), onMain);
            MethodType isRegion = MethodType.methodType(boolean.class);
            IS_REGION_THREAD = link(BooleanSupplier.class, "getAsBoolean", isRegion,
                    lookup.findStatic(api, "isRegionThread", isRegion), isRegion);
            Class<?> workers = Class.forName(WORKERS, true, loader);
            IS_WORKER_THREAD = link(BooleanSupplier.class, "getAsBoolean", isRegion,
                    lookup.findStatic(workers, "isWorkerThread", isRegion), isRegion);
            MethodType owns = MethodType.methodType(boolean.class, ServerLevel.class, BlockPos.class);
            OWNS = link(BiPredicate.class, "test", MethodType.methodType(boolean.class, Object.class, Object.class),
                    lookup.findStatic(api, "ownsCurrentRegion", owns), owns);
            MethodType onRegion = MethodType.methodType(void.class, ServerLevel.class, BlockPos.class, Runnable.class);
            EXECUTE_ON_REGION = link(RegionWork.class, "run", onRegion,
                    lookup.findStatic(api, "executeOnRegion", onRegion), onRegion);
        } catch (ReflectiveOperationException | LambdaConversionException mismatch) {
            throw new IllegalStateException("Tessellate region API mismatch", mismatch);
        }
    }

    private RegionThreads() {
    }

    /** Blocks until the region owning {@code chunkPos} is not ticking; null index (level without regions) => no-op. */
    public static void lease(ServerLevel level, long chunkPos) {
        Object index = INDEX_OF.apply(level);
        if (index != null) {
            LEASE_CHUNK.accept(index, chunkPos);
        }
    }

    /**
     * Queues {@code op} for the server thread (replayed at the next level tick head). A throwing op is logged, not
     * propagated: it would otherwise unwind the tick that drains it.
     */
    public static void executeOnMain(String what, Runnable op) {
        LOGGER.debug("{} -> server thread", what);
        EXECUTE_ON_MAIN.accept(() -> {
            try {
                op.run();
            } catch (RuntimeException e) {
                LOGGER.error("Deferred {} failed", what, e);
            }
        });
    }

    public static boolean isRegionThread() {
        return IS_REGION_THREAD.getAsBoolean();
    }

    /** Calling thread is a Tessellate region worker (bound to a region or not). */
    public static boolean isWorkerThread() {
        return IS_WORKER_THREAD.getAsBoolean();
    }

    /** False outside a region scope. */
    public static boolean owns(ServerLevel level, BlockPos pos) {
        return OWNS.test(level, pos);
    }

    /** Runs {@code op} on the region owning {@code pos}: inline when the caller owns it; throwing op logged, not propagated. */
    public static void onRegion(String what, ServerLevel level, BlockPos pos, Runnable op) {
        EXECUTE_ON_REGION.run(level, pos.immutable(), () -> {
            try {
                op.run();
            } catch (RuntimeException e) {
                LOGGER.error("Deferred {} failed", what, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <F> F link(Class<?> type, String method, MethodType erased, MethodHandle target, MethodType instantiated)
            throws LambdaConversionException {
        try {
            return (F) LambdaMetafactory.metafactory(MethodHandles.lookup(), method, MethodType.methodType(type),
                    erased, target, instantiated).getTarget().invoke();
        } catch (LambdaConversionException e) {
            throw e;
        } catch (Throwable t) {
            if (t instanceof RuntimeException r) {
                throw r;
            }
            if (t instanceof Error e) {
                throw e;
            }
            throw new IllegalStateException(t);
        }
    }
}
