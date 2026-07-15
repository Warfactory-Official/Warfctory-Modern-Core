package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.data.tag.TagPrefix;

import net.minecraft.world.item.Item;

import com.norwood.wfcore.WFCore;
import com.tterrag.registrate.util.entry.ItemEntry;

import java.util.EnumMap;
import java.util.Map;

import static com.norwood.wfcore.WFCore.WF_MACHINES;
import static com.norwood.wfcore.common.data.FoundryMolds.SIZE_BASIN;
import static com.norwood.wfcore.common.data.FoundryMolds.SIZE_CASTER;

/**
 * Fire clay casting molds: WFCore's own recolored take on GregTech's ceramic {@code SHAPE_MOLD_*} items, made
 * of fire clay so the foundry can be bootstrapped without machine-made GT molds. Each shape has two items: a
 * {@link #UNFIRED} raw-clay mold shaped from clay balls with a Soft Mallet (GregTech tool-crafts its own molds
 * the same way, e.g. its {@code shape_empty} blank), which is then fired in a furnace into the reusable
 * {@link #FIRED} fire clay mold. The fired molds are wired into {@link FoundryMolds} alongside the GT molds and
 * are reusable, never consumed.
 *
 * <p>
 * The crafting {@link Shape#pattern} is a per-shape grid where {@code 'C'} is a clay ball and {@code 'r'} is the
 * Soft Mallet ({@code VanillaRecipeHelper} auto-maps the tool symbol). Each pattern is deliberately distinct —
 * its clay count scaling with the cast's size — so the ten shaped recipes never collide (see
 * {@code WFRecipeTypes.addMoldRecipes}).
 */
public final class WFMolds {

    public enum Shape {
        // Small parts -> Foundry Mold Caster
        INGOT("ingot", "Ingot", TagPrefix.ingot, SIZE_CASTER, "rC"),
        PLATE("plate", "Plate", TagPrefix.plate, SIZE_CASTER, "rCC"),
        RING("ring", "Ring", TagPrefix.ring, SIZE_CASTER, "rC", "CC"),
        PIPE_TINY("pipe_tiny", "Tiny Pipe", TagPrefix.pipeTinyFluid, SIZE_CASTER, "r", "C"),
        PIPE_SMALL("pipe_small", "Small Pipe", TagPrefix.pipeSmallFluid, SIZE_CASTER, "r", "C", "C"),
        PIPE_NORMAL("pipe_normal", "Pipe", TagPrefix.pipeNormalFluid, SIZE_CASTER, "rCC", "CC "),
        GEAR_SMALL("gear_small", "Small Gear", TagPrefix.gearSmall, SIZE_CASTER, "rC", "CC", "C "),
        // Bulky casts -> Foundry Casting Basin
        PIPE_LARGE("pipe_large", "Large Pipe", TagPrefix.pipeLargeFluid, SIZE_BASIN, "rCC", "CCC", "C  "),
        GEAR("gear", "Gear", TagPrefix.gear, SIZE_BASIN, "rCC", "CCC"),
        BLOCK("block", "Block", TagPrefix.block, SIZE_BASIN, "rCC", "CCC", "CCC");

        public final String id;
        public final String display;
        public final TagPrefix prefix;
        public final int size;
        public final String[] pattern;

        Shape(String id, String display, TagPrefix prefix, int size, String... pattern) {
            this.id = id;
            this.display = display;
            this.prefix = prefix;
            this.size = size;
            this.pattern = pattern;
        }

        public String unfiredId() {
            return "clay_" + id + "_mold";
        }

        public String firedId() {
            return "fireclay_" + id + "_mold";
        }
    }

    public static final Map<Shape, ItemEntry<Item>> UNFIRED = new EnumMap<>(Shape.class);
    public static final Map<Shape, ItemEntry<Item>> FIRED = new EnumMap<>(Shape.class);

    private WFMolds() {}

    public static void init() {
        if (!FIRED.isEmpty()) return; // idempotent
        for (Shape shape : Shape.values()) {
            UNFIRED.put(shape, WF_MACHINES.item(shape.unfiredId(), Item::new)
                    .lang("Clay " + shape.display + " Mold")
                    .model((ctx, prov) -> prov.generated(ctx, WFCore.id("item/mold/" + shape.unfiredId())))
                    .register());
            FIRED.put(shape, WF_MACHINES.item(shape.firedId(), Item::new)
                    .lang("Fire Clay " + shape.display + " Mold")
                    .model((ctx, prov) -> prov.generated(ctx, WFCore.id("item/mold/" + shape.firedId())))
                    .register());
        }
    }
}
