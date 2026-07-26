package com.norwood.wfcore.integration.kubejs;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.tool.BoltGunConversions;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * KubeJS binding exposed as {@code WFBoltGun} in <b>startup scripts</b>. It defines the bolt gun's conversion
 * map — which block state the gun acts on, what it consumes, and what it produces — replacing the old
 * hardcoded boron-casing bolting. The mod ships no built-in conversions; the pack owns the whole map.
 *
 * <p>
 * Operations are recorded and replayed <em>after</em> the block/item registries are ready (KubeJS startup runs
 * before common setup), so referencing blocks/items from any loaded mod is fine even from startup.
 *
 * <pre>{@code
 * // startup_scripts/wfcore_bolt_gun.js
 * WFBoltGun
 *     .conversion('wfcore:boltable_casing[bolted=false]')   // block the gun acts on
 *         .result('wfcore:boltable_casing[bolted=true]')     // block it becomes
 *         .cost('gtceu:stainless_steel_bolt', 8)             // items consumed (repeatable; count defaults to 1)
 *         .register()
 *     // remove a conversion by its input state:
 *     .remove('wfcore:boltable_casing[bolted=false]')
 * }</pre>
 *
 * <p>
 * Block states use vanilla syntax ({@code namespace:id[prop=value,...]}); item ids default to
 * {@code minecraft}. Invalid/unknown ids are skipped with a warning in the log rather than crashing. When a
 * bolted block is later broken, its conversion's cost is refunded (see {@link BoltGunConversions#costForOutput}).
 */
public class WFBoltGunBindings {

    /** Begin defining a conversion for the given input block state; finish with {@code .register()}. */
    public ConversionBuilder conversion(String inputState) {
        return new ConversionBuilder(this, inputState);
    }

    /** Remove the conversion registered for the given input block state. */
    public WFBoltGunBindings remove(String inputState) {
        BoltGunConversions.enqueue(() -> {
            BlockState input = resolveState(inputState);
            if (input != null) {
                BoltGunConversions.unregister(input);
            }
        });
        return this;
    }

    @Nullable
    static BlockState resolveState(String state) {
        if (state == null) {
            WFCore.LOGGER.warn("[WFBoltGun] null block state");
            return null;
        }
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            WFCore.LOGGER.warn("[WFBoltGun] invalid block state '{}': {}", state, e.getMessage());
            return null;
        }
    }

    @Nullable
    static ItemStack resolveStack(String itemId, int count) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            WFCore.LOGGER.warn("[WFBoltGun] invalid item id '{}'", itemId);
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            WFCore.LOGGER.warn("[WFBoltGun] unknown item '{}' - is the providing mod loaded?", itemId);
            return null;
        }
        return new ItemStack(item, Math.max(1, count));
    }

    /** Fluent builder for a single conversion: input state -> (item cost, output state). */
    public static final class ConversionBuilder {

        private final WFBoltGunBindings owner;
        private final String input;
        private final List<CostSpec> costs = new ArrayList<>();
        @Nullable
        private String output;

        private ConversionBuilder(WFBoltGunBindings owner, String input) {
            this.owner = owner;
            this.input = input;
        }

        /** The block state the input turns into once bolted. Required. */
        public ConversionBuilder result(String outputState) {
            this.output = outputState;
            return this;
        }

        /** Add one item to the cost (repeatable). Count defaults to 1. */
        public ConversionBuilder cost(String itemId) {
            return cost(itemId, 1);
        }

        public ConversionBuilder cost(String itemId, int count) {
            costs.add(new CostSpec(itemId, count));
            return this;
        }

        /** Register the conversion. Returns the {@code WFBoltGun} binding so calls can keep chaining. */
        public WFBoltGunBindings register() {
            String in = input;
            String out = output;
            List<CostSpec> specs = List.copyOf(costs);
            BoltGunConversions.enqueue(() -> {
                if (out == null) {
                    WFCore.LOGGER.warn("[WFBoltGun] conversion for '{}' has no .result(); skipping", in);
                    return;
                }
                BlockState inState = resolveState(in);
                BlockState outState = resolveState(out);
                if (inState == null || outState == null) {
                    return;
                }
                List<ItemStack> cost = new ArrayList<>();
                for (CostSpec spec : specs) {
                    ItemStack stack = resolveStack(spec.itemId(), spec.count());
                    if (stack != null) {
                        cost.add(stack);
                    }
                }
                BoltGunConversions.register(inState, new BoltGunConversions.Conversion(outState, List.copyOf(cost)));
            });
            return owner;
        }
    }

    private record CostSpec(String itemId, int count) {}
}
