package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The casting molds the foundry blocks accept: GregTech's own {@code SHAPE_MOLD_*} items (the same ones the
 * Fluid Solidifier uses), each bound to the {@link TagPrefix} it casts. Nothing is registered here — this is
 * only the lookup that says "this GT mold casts that shape, and it fits the basin or the mold caster".
 * <p>
 * Cost is not hardcoded: it comes from GT itself via {@link TagPrefix#getMaterialAmount(Material)} (in units
 * of {@link GTValues#M}, one ingot) converted to millibuckets ({@link GTValues#L} = 144 mB per ingot). So a
 * nugget mold costs 16 mB, an ingot/plate mold 144 mB, a gear mold 576 mB and a block mold 1296 mB — and any
 * material whose shape GT gives a different amount is handled automatically.
 */
public final class FoundryMolds {

    /** Fits the small Foundry Mold Caster. */
    public static final int SIZE_CASTER = 0;
    /** Fits the large Foundry Casting Basin. */
    public static final int SIZE_BASIN = 1;

    /** Built lazily: GTItems' entries can only be resolved once item registration has run. */
    @Nullable
    private static Map<Item, Mold> molds;

    private FoundryMolds() {}

    /**
     * A mold: the shape it casts and which block accepts it. Small parts go in the mold caster; the bulky
     * casts (a full storage block, gears, rotors, the big pipes) need the basin.
     */
    public record Mold(TagPrefix prefix, int size) {

        /** What this mold casts from {@code material}, or EMPTY if that material has no item of the shape. */
        public ItemStack outputFor(@Nullable Material material) {
            return material == null ? ItemStack.EMPTY : ChemicalHelper.get(prefix, material);
        }

        /**
         * Millibuckets of {@code material} one casting consumes, or 0 when the material can't be cast in this
         * mold (no item of the shape). Derived from GT's own per-shape material amount.
         */
        public int costFor(@Nullable Material material) {
            if (material == null || outputFor(material).isEmpty()) {
                return 0;
            }
            return toMillibuckets(prefix.getMaterialAmount(material));
        }

        /**
         * The shape's nominal cost, ignoring any per-material tweak. This is what the tank advertises as its
         * capacity: it must be known <i>before</i> any metal is inside, or a pusher computing
         * {@code capacity - stored} would see no room and never offer us a pour at all.
         */
        public int baseCost() {
            return toMillibuckets(prefix.materialAmount());
        }

        private static int toMillibuckets(long materialAmount) {
            return materialAmount <= 0 ? 0 : (int) (materialAmount * GTValues.L / GTValues.M);
        }
    }

    /** The mold {@code stack} is, or null if it isn't a supported casting mold. */
    @Nullable
    public static Mold get(ItemStack stack) {
        return stack.isEmpty() ? null : molds().get(stack.getItem());
    }

    private static Map<Item, Mold> molds() {
        if (molds == null) {
            Map<Item, Mold> map = new IdentityHashMap<>();
            // Small parts -> Foundry Mold Caster
            map.put(GTItems.SHAPE_MOLD_NUGGET.get(), new Mold(TagPrefix.nugget, SIZE_CASTER));
            map.put(GTItems.SHAPE_MOLD_INGOT.get(), new Mold(TagPrefix.ingot, SIZE_CASTER));
            map.put(GTItems.SHAPE_MOLD_PLATE.get(), new Mold(TagPrefix.plate, SIZE_CASTER));
            map.put(GTItems.SHAPE_MOLD_GEAR_SMALL.get(), new Mold(TagPrefix.gearSmall, SIZE_CASTER));
            map.put(GTItems.SHAPE_MOLD_TINY_PIPE.get(), new Mold(TagPrefix.pipeTinyFluid, SIZE_CASTER));
            map.put(GTItems.SHAPE_MOLD_SMALL_PIPE.get(), new Mold(TagPrefix.pipeSmallFluid, SIZE_CASTER));
            map.put(GTItems.SHAPE_MOLD_NORMAL_PIPE.get(), new Mold(TagPrefix.pipeNormalFluid, SIZE_CASTER));
            // Bulky casts -> Foundry Casting Basin
            map.put(GTItems.SHAPE_MOLD_BLOCK.get(), new Mold(TagPrefix.block, SIZE_BASIN));
            map.put(GTItems.SHAPE_MOLD_GEAR.get(), new Mold(TagPrefix.gear, SIZE_BASIN));
            map.put(GTItems.SHAPE_MOLD_ROTOR.get(), new Mold(TagPrefix.rotor, SIZE_BASIN));
            map.put(GTItems.SHAPE_MOLD_LARGE_PIPE.get(), new Mold(TagPrefix.pipeLargeFluid, SIZE_BASIN));
            map.put(GTItems.SHAPE_MOLD_HUGE_PIPE.get(), new Mold(TagPrefix.pipeHugeFluid, SIZE_BASIN));
            for (WFMolds.Shape shape : WFMolds.Shape.values()) {
                map.put(WFMolds.FIRED.get(shape).get(), new Mold(shape.prefix, shape.size));
            }
            molds = map;
        }
        return molds;
    }
}
