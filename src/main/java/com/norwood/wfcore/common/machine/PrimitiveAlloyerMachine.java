package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.trait.multiblock.MultiblockFluidRendererTrait;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PrimitiveAlloyerMachine extends WorkableMultiblockMachine {

    /** Lava consumed per fuelled tick (per-tick consumption mode). */
    public static final int LAVA_PER_TICK = 10;

    /** Remaining burn ticks from the last-lit solid fuel. Lava is drawn per-tick and never buffered. */
    @SaveField
    private int fuelBurnTime;

    /**
     * The molten-pool render. GT 8.0.0 replaced {@code IFluidRenderMulti} (an interface the machine
     * implemented, carrying its own {@code @SyncToClient} offset set) with a trait that owns the state and
     * syncs it — so the offsets, the sync annotations and the structure-formed/invalid overrides all move
     * into the trait and out of this class.
     */
    private final MultiblockFluidRendererTrait fluidRenderer;

    public PrimitiveAlloyerMachine(BlockEntityCreationInfo info) {
        super(info, new PrimitiveAlloyerRecipeLogic());
        // The interior chamber block directly behind the controller: the molten pool renders on its top face.
        this.fluidRenderer = attachTrait(new MultiblockFluidRendererTrait(
                () -> Collections.singleton(new BlockPos(getFrontFacing().getOpposite().getNormal()))));
    }

    public @NotNull Set<BlockPos> getFluidBlockOffsets() {
        return fluidRenderer.getFluidOffsets();
    }

    /** @return true if this tick can be fuelled without actually consuming anything yet. */
    public boolean hasFuelAvailable() {
        if (fuelBurnTime > 0) return true;
        if (drainLava(true)) return true;
        return findSolidFuel(true) > 0;
    }

    /** Spend one tick of fuel: burn buffer first, then a per-tick lava draw, then light a fresh solid fuel. */
    public void consumeFuelTick() {
        if (fuelBurnTime > 0) {
            fuelBurnTime--;
            return;
        }
        if (drainLava(false)) {
            return;
        }
        int burn = findSolidFuel(false);
        if (burn > 0) {
            // One unit is spent powering this very tick.
            fuelBurnTime = Math.max(0, burn - 1);
        }
    }

    public int getFuelBurnTime() {
        return fuelBurnTime;
    }

    /** Drain {@link #LAVA_PER_TICK} mb of lava from the input fluid hatches. {@code simulate} peeks only. */
    @SuppressWarnings("unchecked")
    private boolean drainLava(boolean simulate) {
        // GT dropped its own FluidIngredient for NeoForge's SizedFluidIngredient in 8.0.0;
        // FluidRecipeCapability is now RecipeCapability<SizedFluidIngredient>.
        List<SizedFluidIngredient> left = new ArrayList<>(
                List.of(SizedFluidIngredient.of(Fluids.LAVA, LAVA_PER_TICK)));
        List<IRecipeHandler<?>> tanks = new ArrayList<>();
        tanks.addAll(getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP));
        tanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
        for (IRecipeHandler<?> tank : tanks) {
            left = (List<SizedFluidIngredient>) tank.handleRecipe(IO.IN, null, left, simulate);
            // 8.0.0 requires handleRecipe to return an empty list rather than null when nothing is left,
            // but the null check is kept so a third-party handler that still returns null cannot NPE us.
            if (left == null || left.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find a burnable solid fuel in the input buses and, unless {@code simulate}, consume one of it.
     *
     * @return the item's furnace burn time in ticks, or 0 if none is available.
     */
    private int findSolidFuel(boolean simulate) {
        for (IRecipeHandler<?> handler : getCapabilitiesFlat(IO.IN, ItemRecipeCapability.CAP)) {
            if (!(handler instanceof IItemHandlerModifiable items)) continue;
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack stack = items.getStackInSlot(slot);
                int burn = getSolidFuelBurnTime(stack);
                if (burn <= 0) continue;
                if (!simulate) {
                    items.extractItem(slot, 1, false);
                }
                return burn;
            }
        }
        return 0;
    }

    /** Burnable items count as fuel, except wood and planks (logs and plank blocks are rejected outright). */
    private static int getSolidFuelBurnTime(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS)) return 0;
        // ForgeHooks.getBurnTime is now an ItemStack extension method.
        return stack.getBurnTime(RecipeType.SMELTING);
    }
}
