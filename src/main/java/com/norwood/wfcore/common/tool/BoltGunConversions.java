package com.norwood.wfcore.common.tool;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The bolt gun's conversion map: which block state the {@link com.norwood.wfcore.common.item.BoltToolItem
 * bolt gun} acts on, what it consumes, and what it turns the block into. Entirely data-driven from KubeJS
 * (the {@code WFBoltGun} startup binding) — the mod ships no built-in conversions.
 *
 * <p>
 * Operations are enqueued and replayed in common setup (KubeJS startup runs before the block/item registries
 * are usable for parsing state and item ids), mirroring
 * {@link com.norwood.wfcore.common.compute.WFComputeScripts}.
 */
public final class BoltGunConversions {

    /** A single conversion: consume {@code cost}, then replace the input state with {@code output}. */
    public record Conversion(BlockState output, List<ItemStack> cost) {}

    private static final Map<BlockState, Conversion> REGISTRY = new HashMap<>();
    private static final List<Runnable> PENDING = new ArrayList<>();
    private static boolean applied = false;

    private BoltGunConversions() {}

    /** Queue a registry op; runs immediately if the queue has already been flushed. */
    public static synchronized void enqueue(Runnable op) {
        if (op == null) {
            return;
        }
        if (applied) {
            op.run();
        } else {
            PENDING.add(op);
        }
    }

    /** Replay every queued op. Called once in common setup after registries are frozen. */
    public static synchronized void apply() {
        applied = true;
        for (Runnable op : PENDING) {
            op.run();
        }
        PENDING.clear();
    }

    public static void register(BlockState input, Conversion conversion) {
        REGISTRY.put(input, conversion);
    }

    public static void unregister(BlockState input) {
        REGISTRY.remove(input);
    }

    /** The conversion keyed on {@code state}, or {@code null} if the bolt gun does nothing to it. */
    public static Conversion get(BlockState state) {
        return REGISTRY.get(state);
    }

    /**
     * The items spent to reach {@code output}, so breaking that block can refund them. Empty if no
     * conversion produces {@code output}.
     */
    public static List<ItemStack> costForOutput(BlockState output) {
        for (Conversion conversion : REGISTRY.values()) {
            if (conversion.output().equals(output)) {
                return conversion.cost();
            }
        }
        return List.of();
    }


    public static BlockState inputForOutput(BlockState output) {
        for (Map.Entry<BlockState, Conversion> entry : REGISTRY.entrySet()) {
            if (entry.getValue().output().equals(output)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static int size() {
        return REGISTRY.size();
    }
}
