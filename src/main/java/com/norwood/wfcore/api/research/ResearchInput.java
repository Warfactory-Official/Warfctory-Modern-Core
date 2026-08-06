package com.norwood.wfcore.api.research;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import net.minecraftforge.common.crafting.StrictNBTIngredient;

/**
 * One item cost of a {@link Research} run: a Forge {@link Ingredient} plus a required {@link #count()}.
 *
 * <p>
 * A cost can be either an <em>exact</em> item — NBT-strict, so e.g. a {@code tacz:ammo} cost still only
 * accepts the matching {@code AmmoId} (preserving the old {@code ItemStack.isSameItemSameTags} behaviour) —
 * or a <em>tag</em>, where any member satisfies it (e.g. {@code #gtceu:circuits/lv} accepts any LV circuit,
 * instead of hard-coding one specific circuit item). The Research Unit matches input-bus items via
 * {@link #test(ItemStack)}; the research GUI shows {@link #displayStack()} (the first tag member for a tag).
 */
public final class ResearchInput {

    private final Ingredient ingredient;
    private final int count;
    private ItemStack[] displays; // GUI candidates (each with count), resolved lazily (tags bind after data load)

    private ResearchInput(Ingredient ingredient, int count) {
        this.ingredient = ingredient;
        this.count = Math.max(1, count);
    }

    /** Exact item cost, NBT-strict (matches item + full NBT, like the old {@code isSameItemSameTags} path). */
    public static ResearchInput of(ItemStack stack) {
        return new ResearchInput(StrictNBTIngredient.of(stack), Math.max(1, stack.getCount()));
    }

    /** Tag cost: any item in {@code tag} satisfies it, {@code count} required per run. */
    public static ResearchInput ofTag(TagKey<Item> tag, int count) {
        return new ResearchInput(Ingredient.of(tag), count);
    }

    /** True if {@code stack} satisfies this cost (item+NBT for exact costs, tag membership for tag costs). */
    public boolean test(ItemStack stack) {
        return ingredient.test(stack);
    }

    /** How many matching items one research run consumes. */
    public int count() {
        return count;
    }

    /**
     * Writes this cost to a packet buffer (the {@link Ingredient} + its count) for the research-registry sync.
     * Forge's {@link Ingredient#toNetwork} preserves both exact ({@link StrictNBTIngredient}) and tag costs, so
     * the client rebuilds the same {@link #displayStacks()} the GUI draws — the only thing the client needs, as
     * cost matching ({@link #test}) is server-side.
     */
    public void writeToNetwork(FriendlyByteBuf buf) {
        ingredient.toNetwork(buf);
        buf.writeVarInt(count);
    }

    /** Reads a cost written by {@link #writeToNetwork} (client side of the registry sync). */
    public static ResearchInput fromNetwork(FriendlyByteBuf buf) {
        Ingredient ingredient = Ingredient.fromNetwork(buf);
        return new ResearchInput(ingredient, buf.readVarInt());
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    /**
     * All stacks that satisfy this cost, each carrying {@link #count()} — one element for an exact cost, every
     * tag member for a tag cost. Resolved lazily because tag contents are only bound after data load, whereas
     * research nodes are constructed during script evaluation.
     */
    public ItemStack[] displayStacks() {
        if (displays == null) {
            ItemStack[] items = ingredient.getItems();
            ItemStack[] out = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) out[i] = items[i].copyWithCount(count);
            displays = out;
        }
        return displays;
    }

    /** The first candidate (or empty) — a stable representative stack. */
    public ItemStack displayStack() {
        ItemStack[] items = displayStacks();
        return items.length > 0 ? items[0] : ItemStack.EMPTY;
    }

    /**
     * The stack to show in the research GUI right now: a single stack for an exact cost, or — for a tag cost —
     * one that cycles through every tag member about once a second (JEI-style), so all valid circuits/plates
     * are visible over time.
     */
    public ItemStack cyclingStack() {
        ItemStack[] items = displayStacks();
        if (items.length <= 1) return items.length == 1 ? items[0] : ItemStack.EMPTY;
        int idx = (int) ((System.currentTimeMillis() / 1000L) % items.length);
        return items[idx];
    }
}
