package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IFluidRenderMulti;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.norwood.wfcore.common.particle.WFParticles;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class StrandcasterMachine extends WorkableMultiblockMachine implements IFluidRenderMulti {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            StrandcasterMachine.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Water consumed per tick while cooling, which halves the cast duration. */
    public static final int WATER_PER_TICK = 20;

    @DescSynced
    @RequireRerender
    private @NotNull Set<BlockPos> fluidBlockOffsets = new HashSet<>();

    public StrandcasterMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }


    public static ModifierFunction modifyRecipe(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof StrandcasterMachine caster)) {
            return RecipeModifier.nullWrongType(StrandcasterMachine.class, machine);
        }
        if (!caster.hasWaterCoolant()) {
            return ModifierFunction.IDENTITY;
        }
        return r -> {
            GTRecipe copied = r.copy(ContentModifier.IDENTITY, false);
            copied.duration = Math.max(1, r.duration / 2);
            Content water = new Content(
                    FluidRecipeCapability.CAP.of(FluidIngredient.of(Fluids.WATER, WATER_PER_TICK)),
                    ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue(), 0);
            copied.tickInputs.computeIfAbsent(FluidRecipeCapability.CAP, c -> new ArrayList<>()).add(water);
            return copied;
        };
    }

    /** @return true if the fluid input hatches together hold at least {@link #WATER_PER_TICK} mb of water. */
    @SuppressWarnings("unchecked")
    public boolean hasWaterCoolant() {
        List<FluidIngredient> left = new ArrayList<>(List.of(FluidIngredient.of(Fluids.WATER, WATER_PER_TICK)));
        List<IRecipeHandler<?>> tanks = new ArrayList<>();
        tanks.addAll(getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP));
        tanks.addAll(getCapabilitiesFlat(IO.BOTH, FluidRecipeCapability.CAP));
        for (IRecipeHandler<?> tank : tanks) {
            left = (List<FluidIngredient>) tank.handleRecipe(IO.IN, null, left, true);
            if (left == null || left.isEmpty()) {
                return true;
            }
        }
        return left == null || left.isEmpty();
    }

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

    /** While casting, steam rises off the molten pool (the casting bed) behind the controller. */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientTick() {
        super.clientTick();
        if (!isFormed() || !isActive() || getLevel() == null) {
            return;
        }
        if (getOffsetTimer() % 2 != 0) {
            return;
        }
        BlockPos bed = getPos().offset(getFrontFacing().getOpposite().getNormal());
        double px = bed.getX() + 0.25D + GTValues.RNG.nextDouble() * 0.5D;
        double py = bed.getY() + 1.0D; // the molten-pool surface (top face of the bed block)
        double pz = bed.getZ() + 0.25D + GTValues.RNG.nextDouble() * 0.5D;
        getLevel().addParticle(WFParticles.STRANDCASTER_STEAM.get(), px, py, pz, 0.0D, 0.0D, 0.0D);
    }
}

