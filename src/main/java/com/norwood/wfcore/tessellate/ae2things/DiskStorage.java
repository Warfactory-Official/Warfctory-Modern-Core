package com.norwood.wfcore.tessellate.ae2things;

import net.minecraft.nbt.ListTag;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/** AE2-Things {@code StorageManager.modifyDisk} body without a compile dependency; signature mismatch => {@link IllegalStateException}. */
public final class DiskStorage {

    private static final MethodHandle GET_OR_CREATE;
    private static final MethodHandle UPDATE;
    private static final MethodHandle KEYS;
    private static final MethodHandle AMOUNTS;
    private static final MethodHandle NEW;

    static {
        try {
            ClassLoader loader = DiskStorage.class.getClassLoader();
            Class<?> manager = Class.forName(Ae2ThingsTransformer.MANAGER, true, loader);
            Class<?> data = Class.forName(Ae2ThingsTransformer.DATA, true, loader);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            GET_OR_CREATE = lookup.findVirtual(manager, "getOrCreateDisk", MethodType.methodType(data, UUID.class))
                    .asType(MethodType.methodType(Object.class, Object.class, UUID.class));
            UPDATE = lookup.findVirtual(manager, "updateDisk", MethodType.methodType(void.class, UUID.class, data))
                    .asType(MethodType.methodType(void.class, Object.class, UUID.class, Object.class));
            KEYS = lookup.findGetter(data, "stackKeys", ListTag.class).asType(MethodType.methodType(ListTag.class, Object.class));
            AMOUNTS = lookup.findGetter(data, "stackAmounts", long[].class).asType(MethodType.methodType(long[].class, Object.class));
            NEW = lookup.findConstructor(data, MethodType.methodType(void.class, ListTag.class, long[].class, long.class))
                    .asType(MethodType.methodType(Object.class, ListTag.class, long[].class, long.class));
        } catch (ReflectiveOperationException mismatch) {
            throw new IllegalStateException("AE2-Things StorageManager API mismatch", mismatch);
        }
    }

    private DiskStorage() {
    }

    /** Caller holds the manager monitor. Replaces the disk's {@code DataStorage} instead of mutating it in place. */
    public static void modify(Object manager, UUID disk, ListTag keys, long[] amounts, long itemCount) {
        try {
            Object old = (Object) GET_OR_CREATE.invokeExact(manager, disk);
            boolean replace = keys != null && amounts != null;
            ListTag nextKeys = replace ? keys : (ListTag) KEYS.invokeExact(old);
            long[] nextAmounts = replace ? amounts : (long[]) AMOUNTS.invokeExact(old);
            Object next = (Object) NEW.invokeExact(nextKeys, nextAmounts, itemCount);
            UPDATE.invokeExact(manager, disk, next);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
