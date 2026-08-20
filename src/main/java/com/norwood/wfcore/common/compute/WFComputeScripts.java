package com.norwood.wfcore.common.compute;

import java.util.ArrayList;
import java.util.List;

/**
 * Queue of compute-registry / config operations contributed by KubeJS startup scripts.
 *
 * <p>
 * KubeJS startup scripts run <em>before</em> {@code FMLCommonSetupEvent}, which is where WFCore registers its
 * built-in CPU/RAM/coolant defaults. If the {@code WFCompute} binding mutated the registries immediately, the
 * defaults would run afterwards and clobber a pack's overrides (and undo its removals). So the binding instead
 * {@linkplain #enqueue(Runnable) enqueues} each operation here, and {@link #apply()} replays them
 * <em>after</em> the defaults are in place — letting scripts reliably add, override or remove built-ins.
 *
 * <p>
 * Startup scripts execute exactly once per game launch, so {@link #apply()} is a one-shot flush. If an op is
 * somehow enqueued after the flush (e.g. a reload path), it is applied immediately so nothing is silently lost.
 */
public final class WFComputeScripts {

    private static final List<Runnable> PENDING = new ArrayList<>();
    private static boolean applied = false;

    private WFComputeScripts() {}

    /** Record an operation to run right after the built-in defaults, or immediately if the flush already ran. */
    public static synchronized void enqueue(Runnable op) {
        if (op == null) return;
        if (applied) {
            op.run();
        } else {
            PENDING.add(op);
        }
    }

    /** Replay every queued script operation, in script order. Called once, after defaults are registered. */
    public static synchronized void apply() {
        applied = true;
        for (Runnable op : PENDING) {
            op.run();
        }
        PENDING.clear();
    }
}
