package com.norwood.wfcore.tessellate.mixin.ldlib2;

import com.norwood.wfcore.tessellate.ldlib2.EnumCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "com.lowdragmc.lowdraglib2.syncdata.accessor.direct.EnumAccessor", remap = false)
public abstract class EnumAccessorMixin {

    /**
     * @author wfcore
     * @reason static WeakHashMap.computeIfAbsent from any thread
     */
    @Overwrite
    public static Enum<?> getEnum(Class<Enum<?>> type, String name) {
        return EnumCache.byName(type, name);
    }

    /**
     * @author wfcore
     * @reason static WeakHashMap.computeIfAbsent from any thread
     */
    @Overwrite
    public static Enum<?> getEnum(Class<Enum<?>> type, int ordinal) {
        return EnumCache.byOrdinal(type, ordinal);
    }
}
