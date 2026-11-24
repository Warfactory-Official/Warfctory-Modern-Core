package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.google.common.collect.Table;
import com.google.common.primitives.UnsignedLong;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.timers.TimerCallback;
import net.minecraft.world.level.timers.TimerQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * {@code /schedule} from region workers vs {@code tick} on the server thread: PriorityQueue + HashBasedTable mutated
 * under the instance monitor; callbacks run outside it.
 */
@Mixin(TimerQueue.class)
public abstract class TimerQueueMixin<T> {

    @Shadow
    @Final
    private Queue<TimerQueue.Event<T>> queue;

    @Shadow
    @Final
    private Table<String, Long, TimerQueue.Event<T>> events;

    @Shadow
    private UnsignedLong sequentialId;

    /**
     * @author wfcore
     * @reason pop under the monitor, run callback outside it (callbacks reschedule)
     */
    @Overwrite
    public void tick(T obj, long gameTime) {
        while (true) {
            TimerQueue.Event<T> event;
            synchronized (this) {
                event = queue.peek();
                if (event == null || event.triggerTime > gameTime) {
                    return;
                }
                queue.remove();
                events.remove(event.id, gameTime);
            }
            event.callback.handle(obj, (TimerQueue<T>) (Object) this, gameTime);
        }
    }

    @WrapMethod(method = "schedule")
    private void wfcore$schedule(String id, long triggerTime, TimerCallback<T> callback, Operation<Void> original) {
        synchronized (this) {
            original.call(id, triggerTime, callback);
        }
    }

    @WrapMethod(method = "remove")
    private int wfcore$remove(String eventId, Operation<Integer> original) {
        synchronized (this) {
            return original.call(eventId);
        }
    }

    @WrapMethod(method = "getEventsIds")
    private Set<String> wfcore$ids(Operation<Set<String>> original) {
        synchronized (this) {
            return new HashSet<>(original.call());
        }
    }

    @WrapMethod(method = "store")
    private ListTag wfcore$store(Operation<ListTag> original) {
        synchronized (this) {
            return original.call();
        }
    }
}
