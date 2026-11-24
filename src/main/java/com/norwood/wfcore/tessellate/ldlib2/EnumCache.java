package com.norwood.wfcore.tessellate.ldlib2;

import net.minecraft.util.StringRepresentable;

import java.util.HashMap;
import java.util.Map;

/** Replaces LDLib2 {@code EnumAccessor}'s static {@code WeakHashMap}s. Name rule == {@code EnumAccessor.getEnumName}. */
public final class EnumCache {

    private static final ClassValue<Enum<?>[]> VALUES = new ClassValue<>() {
        @Override
        protected Enum<?>[] computeValue(Class<?> type) {
            return (Enum<?>[]) type.getEnumConstants();
        }
    };

    private static final ClassValue<Map<String, Enum<?>>> BY_NAME = new ClassValue<>() {
        @Override
        protected Map<String, Enum<?>> computeValue(Class<?> type) {
            Map<String, Enum<?>> map = new HashMap<>();
            for (Enum<?> value : (Enum<?>[]) type.getEnumConstants()) {
                map.put(name(value), value);
            }
            return map;
        }
    };

    private EnumCache() {
    }

    public static Enum<?> byName(Class<Enum<?>> type, String name) {
        Enum<?> value = BY_NAME.get(type).get(name);
        return value == null ? null : type.cast(value);
    }

    public static Enum<?> byOrdinal(Class<Enum<?>> type, int ordinal) {
        Enum<?>[] values = VALUES.get(type);
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid ordinal for enum type " + type.getName() + ": " + ordinal);
        }
        return type.cast(values[ordinal]);
    }

    private static String name(Enum<?> value) {
        return value instanceof StringRepresentable provider ? provider.getSerializedName() : value.name();
    }
}
