package com.norwood.wfcore.tessellate.mixin.umc;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.Set;

/** {@code patches/umc/0002}: {@code valueToKeys} sets are replaced on write, never mutated after publication. */
@Mixin(targets = "cam72cam.mod.world.WorldEntityTracker$LongBiMultiMap", remap = false)
public abstract class LongBiMultiMapMixin {

    @Shadow
    private Long2ObjectOpenHashMap<LongOpenHashSet> keyToValues;

    @Shadow
    private Long2ObjectOpenHashMap<LongArraySet> valueToKeys;

    @Overwrite
    public void put(long key, long value) {
        keyToValues.computeIfAbsent(key, k -> new LongOpenHashSet(245)).add(value);
        LongArraySet keys = valueToKeys.get(value);
        LongArraySet replaced = keys == null ? new LongArraySet(4) : new LongArraySet(keys);
        replaced.add(key);
        valueToKeys.put(value, replaced);
    }

    @Overwrite
    public Set<Long> removeKey(long key) {
        LongOpenHashSet values = keyToValues.remove(key);
        if (values == null) {
            return Collections.emptySet();
        }
        for (long value : values) {
            LongArraySet keys = valueToKeys.get(value);
            if (keys != null) {
                LongArraySet replaced = new LongArraySet(keys);
                replaced.remove(key);
                if (replaced.isEmpty()) {
                    valueToKeys.remove(value);
                } else {
                    valueToKeys.put(value, replaced);
                }
            }
        }
        return values;
    }
}
