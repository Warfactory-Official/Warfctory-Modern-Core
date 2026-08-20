package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.common.machine.trait.multiblock.MultiblockFluidRendererTrait;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Port of the 1.12.2 WFCore large (warfactory) blast furnace: a primitive, non-electric multiblock
 * that runs the {@code wfcore:large_blast_furnace} recipe type. All IO flows through item buses and
 * fluid hatches placed in the brick casing (no internal GUI), mirroring the original "you need 2
 * buses" design. While working it burns entities standing in front of it, melts snow, floods its
 * interior hearth with a lava plane and vents smoke out of its chimneys. The central chamber + tall
 * center chimney are mandatory; up to two optional side chimneys (Bronze Firebox base) each add
 * parallelism ("stages" 2 and 3) and their own smoke plume.
 */
public class LargeBlastFurnaceMachine extends WorkableMultiblockMachine {


    private TickableSubscription hurtSubscription;

    private int sideChambers;

    @SyncToClient
    private boolean leftChimney;
    @SyncToClient
    private boolean rightChimney;

    /**
     * The hearth's lava plane. GT 8.0.0 replaced {@code IFluidRenderMulti} with this trait, and GT's own
     * {@code FluidAreaRender} now gates visibility on {@code machine.isActive()} — so the offsets are
     * pure geometry and no longer have to be cleared when the furnace stops working.
     */
    private final MultiblockFluidRendererTrait fluidRenderer;

    public LargeBlastFurnaceMachine(BlockEntityCreationInfo info) {
        super(info);
        this.fluidRenderer = attachTrait(new MultiblockFluidRendererTrait(this::computeFloorOffsets));
    }

    public @NotNull Set<BlockPos> getFluidBlockOffsets() {
        return fluidRenderer.getFluidOffsets();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        unsubscribe(hurtSubscription);
        hurtSubscription = null;
    }

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        detectSideChimneys();
    }

    /**
     * Work out which optional side chimneys are built by finding their Bronze Firebox bases in the
     * structure cache and classifying each by its position along the RIGHT axis (left = negative,
     * right = positive) relative to the controller. Rotation/flip agnostic.
     */
    private void detectSideChimneys() {
        this.leftChimney = false;
        this.rightChimney = false;
        var level = getLevel();
        var state = getDefaultPatternState();
        if (level != null && state != null) {
            Direction right = RelativeDirection.RIGHT.getRelativeFacing(getFrontFacing(), getUpwardsFacing(), isFlipped());
            BlockPos origin = getBlockPos();
            // 8.0.0's PatternState keys its cache by packed long instead of holding BlockPos objects.
            for (long key : state.getCache().keySet()) {
                BlockPos pos = BlockPos.of(key);
                if (!level.getBlockState(pos).is(GTBlocks.FIREBOX_BRONZE.get())) continue;
                int dRight = (pos.getX() - origin.getX()) * right.getStepX()
                        + (pos.getY() - origin.getY()) * right.getStepY()
                        + (pos.getZ() - origin.getZ()) * right.getStepZ();
                if (dRight < 0) this.leftChimney = true;
                else if (dRight > 0) this.rightChimney = true;
            }
        }
        this.sideChambers = (leftChimney ? 1 : 0) + (rightChimney ? 1 : 0);
    }

    public int getSideChambers() {
        return sideChambers;
    }

    /**
     * Relative offset from the controller, converted to a world-space {@link BlockPos} delta:
     * {@code dFront} steps along the front facing, {@code dUp} up, {@code dRight} to the right.
     */
    private BlockPos relOffset(Direction front, Direction up, Direction right, int dFront, int dUp, int dRight) {
        return new BlockPos(
                front.getStepX() * dFront + up.getStepX() * dUp + right.getStepX() * dRight,
                front.getStepY() * dFront + up.getStepY() * dUp + right.getStepY() * dRight,
                front.getStepZ() * dFront + up.getStepZ() * dUp + right.getStepZ() * dRight);
    }

    /** Floor blocks the lava plane is drawn on: the sealed central 3x3 hearth (the flues are
     * separate shafts behind the chamber walls, so lava stays inside the chamber). */
    private Set<BlockPos> computeFloorOffsets() {
        Direction front = getFrontFacing();
        Direction up = RelativeDirection.UP.getRelativeFacing(front, getUpwardsFacing(), isFlipped());
        Direction right = RelativeDirection.RIGHT.getRelativeFacing(front, getUpwardsFacing(), isFlipped());
        Set<BlockPos> set = new HashSet<>();
        for (int dRight = -1; dRight <= 1; dRight++) {
            for (int dFront = -3; dFront <= -1; dFront++) {
                set.add(relOffset(front, up, right, dFront, -2, dRight));
            }
        }
        return set;
    }

    /** Chimney mouths that emit smoke: the tall center chimney (always) plus each built side chimney. */
    private List<BlockPos> computeChimneyMouths() {
        Direction front = getFrontFacing();
        Direction up = RelativeDirection.UP.getRelativeFacing(front, getUpwardsFacing(), isFlipped());
        Direction right = RelativeDirection.RIGHT.getRelativeFacing(front, getUpwardsFacing(), isFlipped());
        List<BlockPos> mouths = new ArrayList<>();
        mouths.add(relOffset(front, up, right, -2, 7, 0));
        if (leftChimney) mouths.add(relOffset(front, up, right, -2, 4, -3));
        if (rightChimney) mouths.add(relOffset(front, up, right, -2, 4, 3));
        return mouths;
    }

    public static ModifierFunction modifyRecipe(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof LargeBlastFurnaceMachine furnace)) {
            return RecipeModifier.nullWrongType(LargeBlastFurnaceMachine.class, machine);
        }
        int chambers = furnace.getSideChambers();
        int parallels = Math.min(8, 4 + 2 * chambers);
        double durationMultiplier = 1.0 - 0.05 * chambers;
        return ModifierFunction.builder()
                .modifyAllContents(ContentModifier.multiplier(parallels))
                .parallels(parallels)
                .durationMultiplier(durationMultiplier)
                .build();
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);
    }

    @Override
    public void recipeLogicStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        super.recipeLogicStatusChanged(oldStatus, newStatus);
        if (newStatus == RecipeLogic.Status.WORKING) {
            this.hurtSubscription = subscribeServerTick(this.hurtSubscription, this::hurtEntitiesAndBreakSnow);
        } else if (oldStatus == RecipeLogic.Status.WORKING) {
            if (hurtSubscription != null) {
                unsubscribe(hurtSubscription);
                hurtSubscription = null;
            }
        }
    }

    public @NotNull Set<BlockPos> saveOffsets() {
        return isActive() ? computeFloorOffsets() : Collections.emptySet();
    }

    private void hurtEntitiesAndBreakSnow() {
        BlockPos middlePos = self().getBlockPos().offset(getFrontFacing().getOpposite().getNormal());
        getLevel().getEntities(null, new AABB(middlePos)).forEach(e -> e.hurt(e.damageSources().lava(), 3.0f));

        if (getOffsetTimer() % 10 == 0) {
            BlockState state = getLevel().getBlockState(middlePos);
            GTUtil.tryBreakSnow(getLevel(), middlePos, state, true);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void clientTick() {
        super.clientTick();
        if (!isFormed || !isActive()) return;
        var level = getLevel();
        if (level == null || getOffsetTimer() % 2 != 0) return;
        BlockPos pos = getBlockPos();
        for (BlockPos mouth : computeChimneyMouths()) {
            double x = pos.getX() + mouth.getX() + 0.5 + (GTValues.RNG.nextFloat() - 0.5) * 0.3;
            double y = pos.getY() + mouth.getY() + 0.4;
            double z = pos.getZ() + mouth.getZ() + 0.5 + (GTValues.RNG.nextFloat() - 0.5) * 0.3;
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z,
                    0, 0.06 + 0.04 * GTValues.RNG.nextFloat(), 0);
        }
    }

    @Override
    public void animateTick(RandomSource random) {
        if (!isActive()) return;
        var level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        List<BlockPos> mouths = computeChimneyMouths();
        BlockPos mouth = mouths.get(random.nextInt(mouths.size()));
        float x = pos.getX() + mouth.getX() + 0.5F + (random.nextFloat() - 0.5F) * 0.3F;
        float y = pos.getY() + mouth.getY() + 0.3F;
        float z = pos.getZ() + mouth.getZ() + 0.5F + (random.nextFloat() - 0.5F) * 0.3F;
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0, 0.02, 0);
        if (ConfigHolder.INSTANCE.machines.machineSounds && random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
    }
}
