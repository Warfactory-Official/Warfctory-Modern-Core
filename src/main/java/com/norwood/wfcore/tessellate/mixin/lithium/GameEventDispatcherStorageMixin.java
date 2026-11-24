package com.norwood.wfcore.tessellate.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.StampedLock;

/**
 * {@code patches/lithium-tessellate/0003}: writes (chunk load/unload on main, listener registration on any thread) take
 * the lock; {@code get} (every game event, on workers) is an optimistic read with locked fallback.
 */
@Mixin(targets = "net.caffeinemc.mods.lithium.common.world.GameEventDispatcherStorage", remap = false)
public abstract class GameEventDispatcherStorageMixin {

    @Unique
    private final StampedLock tessellate$lock = new StampedLock();

    @WrapMethod(method = "addChunk")
    private void wfcore$addChunk(long pos, Int2ObjectMap<GameEventListenerRegistry> dispatchers, Operation<Void> original) {
        long stamp = tessellate$lock.writeLock();
        try {
            original.call(pos, dispatchers);
        } finally {
            tessellate$lock.unlockWrite(stamp);
        }
    }

    @WrapMethod(method = "removeChunk")
    private void wfcore$removeChunk(long pos, Operation<Void> original) {
        long stamp = tessellate$lock.writeLock();
        try {
            original.call(pos);
        } finally {
            tessellate$lock.unlockWrite(stamp);
        }
    }

    @WrapMethod(method = "replace")
    private void wfcore$replace(long pos, Int2ObjectMap<GameEventListenerRegistry> dispatchers, Operation<Void> original) {
        long stamp = tessellate$lock.writeLock();
        try {
            original.call(pos, dispatchers);
        } finally {
            tessellate$lock.unlockWrite(stamp);
        }
    }

    @WrapMethod(method = "get")
    private Int2ObjectMap<GameEventListenerRegistry> wfcore$get(long pos, Operation<Int2ObjectMap<GameEventListenerRegistry>> original) {
        long stamp = tessellate$lock.tryOptimisticRead();
        Int2ObjectMap<GameEventListenerRegistry> result;
        try {
            result = original.call(pos);
        } catch (RuntimeException racedWithWriter) {
            result = null;
        }
        if (tessellate$lock.validate(stamp)) {
            return result;
        }
        stamp = tessellate$lock.readLock();
        try {
            return original.call(pos);
        } finally {
            tessellate$lock.unlockRead(stamp);
        }
    }
}
