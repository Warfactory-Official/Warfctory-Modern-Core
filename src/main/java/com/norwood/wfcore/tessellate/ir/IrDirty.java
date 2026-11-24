package com.norwood.wfcore.tessellate.ir;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

/**
 * {@code patches/ir/0003} dirty queue: a rail change on any thread requests {@code SimulationState.dirty} on nearby stock;
 * applied on the next {@code Simulation.simulate} of that world. Handles bound at first use; mismatch => {@link IllegalStateException}.
 */
public final class IrDirty {

    private record Request(Object world, Predicate<Object> near) {
    }

    private static final Queue<Request> QUEUE = new ConcurrentLinkedQueue<>();
    private static final MethodHandle ENTITIES;
    private static final MethodHandle STATES;
    private static final MethodHandle DIRTY;
    private static final MethodHandle FORCE_QUICK;
    private static final Class<?> STOCK;

    static {
        try {
            ClassLoader loader = IrDirty.class.getClassLoader();
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> world = Class.forName("cam72cam.mod.world.World", true, loader);
            STOCK = Class.forName("cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock", true, loader);
            Class<?> state = Class.forName("cam72cam.immersiverailroading.entity.physics.SimulationState", true, loader);
            Class<?> sim = Class.forName("cam72cam.immersiverailroading.entity.physics.Simulation", true, loader);
            ENTITIES = lookup.findVirtual(world, "getEntities", MethodType.methodType(List.class, Class.class))
                    .asType(MethodType.methodType(List.class, Object.class, Class.class));
            STATES = lookup.findGetter(STOCK, "states", List.class).asType(MethodType.methodType(List.class, Object.class));
            DIRTY = lookup.findSetter(state, "dirty", boolean.class).asType(MethodType.methodType(void.class, Object.class, boolean.class));
            FORCE_QUICK = lookup.findStaticSetter(sim, "forceQuickUpdates", boolean.class);
        } catch (ReflectiveOperationException mismatch) {
            throw new IllegalStateException("Immersive Railroading simulation API mismatch", mismatch);
        }
    }

    private IrDirty() {
    }

    /** Replaces {@code world.getEntities(near, stockType)} + dirty loop: returns empty, the request is applied on main. */
    @SuppressWarnings("unchecked")
    public static List<Object> defer(Object world, Predicate<?> near, Class<?> type) {
        QUEUE.add(new Request(world, (Predicate<Object>) near));
        try {
            FORCE_QUICK.invokeExact(true);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        return List.of();
    }

    public static void apply(Object world) {
        QUEUE.removeIf(request -> {
            if (request.world != world) {
                return false;
            }
            try {
                for (Object stock : (List<?>) (List) ENTITIES.invokeExact(world, STOCK)) {
                    if (request.near.test(stock)) {
                        for (Object state : (List<?>) (List) STATES.invokeExact(stock)) {
                            DIRTY.invokeExact(state, true);
                        }
                    }
                }
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
            return true;
        });
    }

    /** {@code Map} copy for deserialised maps. */
    public static java.util.Map<Object, Object> concurrent(java.util.Map<Object, Object> map) {
        return new java.util.concurrent.ConcurrentHashMap<>(map);
    }
}
