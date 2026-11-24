package com.norwood.wfcore.tessellate.ldlib2;

import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BooleanSupplier;

/** LDLib2 {@code AsyncThreadData.isThreadService()} without a compile dependency. */
public final class AsyncThreads {

    private static final BooleanSupplier ON_ASYNC_THREAD;

    static {
        try {
            Class<?> data = Class.forName("com.lowdragmc.lowdraglib2.async.AsyncThreadData", true, AsyncThreads.class.getClassLoader());
            MethodType type = MethodType.methodType(boolean.class);
            ON_ASYNC_THREAD = (BooleanSupplier) LambdaMetafactory.metafactory(MethodHandles.lookup(), "getAsBoolean",
                    MethodType.methodType(BooleanSupplier.class), type,
                    MethodHandles.publicLookup().findStatic(data, "isThreadService", type), type).getTarget().invoke();
        } catch (ReflectiveOperationException | LambdaConversionException mismatch) {
            throw new IllegalStateException("LDLib2 async API mismatch", mismatch);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private AsyncThreads() {
    }

    public static boolean onAsyncThread() {
        return ON_ASYNC_THREAD.getAsBoolean();
    }
}
