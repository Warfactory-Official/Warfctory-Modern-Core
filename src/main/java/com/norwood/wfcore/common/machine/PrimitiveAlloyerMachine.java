package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IFluidRenderMulti;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrimitiveAlloyerMachine extends WorkableMultiblockMachine implements IFluidRenderMulti {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            PrimitiveAlloyerMachine.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Lava consumed per fuelled tick (per-tick consumption mode). */
    public static final int LAVA_PER_TICK = 10;

    /** Remaining burn ticks from the last-lit solid fuel. Lava is drawn per-tick and never buffered. */
    @Persisted
    private int fuelBurnTime;

    @DescSynced
    @RequireRerender
    private @NotNull Set<BlockPos> fluidBlockOffsets = new HashSet<>();

    public PrimitiveAlloyerMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new PrimitiveAlloyerRecipeLogic(this);
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
        List<FluidIngredient> left = new ArrayList<>(List.of(FluidIngredient.of(Fluids.LAVA, LAVA_PER_TICK)));
        List<IRecipeHandler<?>> tanks = new ArrayList<>();
        tanks.addAll(getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP));
        tanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
        for (IRecipeHandler<?> tank : tanks) {
            left = (List<FluidIngredient>) tank.handleRecipe(IO.IN, null, left, simulate);
            if (left == null || left.isEmpty()) {
                return true;
            }
        }
        return left == null || left.isEmpty();
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
        return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
    }

    // ------------------------------------------------------------------------------------------------------
    // Molten-pool render
    // ------------------------------------------------------------------------------------------------------

    @Override
    public @NotNull Set<BlockPos> getFluidBlockOffsets() {
        return fluidBlockOffsets;
    }

    @Override
    public void setFluidBlockOffsets(@NotNull Set<BlockPos> offsets) {
        this.fluidBlockOffsets = offsets;
    }

    @Override
    public @NotNull Set<BlockPos> saveOffsets() {
        // The interior chamber block directly behind the controller: the molten pool renders on its top face.
        return Collections.singleton(new BlockPos(getFrontFacing().getOpposite().getNormal()));
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        IFluidRenderMulti.super.onStructureFormed();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        IFluidRenderMulti.super.onStructureInvalid();
    }
}
