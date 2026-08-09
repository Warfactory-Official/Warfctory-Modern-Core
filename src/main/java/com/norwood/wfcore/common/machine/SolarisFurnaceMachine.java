package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Solaris Furnace: a sunlight-powered blast furnace. It runs the vanilla GTCEu {@code blast_furnace}
 * recipe type and keeps all of the usual EBF behaviour (coil-temperature gating, the coil EU discount and
 * non-perfect overclocking, via {@link GTRecipeModifiers#ebfOverclock}). Stacked <em>on top</em> of that
 * baseline are solar upgrades decided from how the machine is built and the time of day:
 * <ul>
 *     <li><b>Sealed off</b> (any top-cap block can't see the sky): half speed, no parallel.</li>
 *     <li><b>Sky-exposed at night</b>: up to <b>4 recipes at once</b>.</li>
 *     <li><b>Sky-exposed in daylight, deep underground</b> (below sea level): up to <b>2 recipes at once</b>.</li>
 *     <li><b>Sky-exposed in daylight, above sea level</b>: up to <b>6 recipes at once</b>.</li>
 *     <li><b>Above y=75</b>: <b>30% less power</b>, independent of the sunlight state.</li>
 * </ul>
 *
 * <p>Structurally: a 4-tall 3x3 coil pillar (hollow core) between a 5x5 bottom cap and a 5x5 Solar Panel
 * Casing top cap with the muffler hatch in its centre.
 */
public class SolarisFurnaceMachine extends CoilWorkableElectricMultiblockMachine {

    /** Sea-level fallback used only if the level cannot report one (never expected in practice). */
    private static final int DEFAULT_SEA_LEVEL = 63;
    /** Controller height above which the power discount kicks in. */
    private static final int HIGH_ALTITUDE_Y = 75;
    /** Footprint radius of the top cap; the top layer we test for sky access is {@code 2*RADIUS+1} square. */
    private static final int CAP_RADIUS = 2;
    /** Vertical offset from the controller (bottom-front-centre) up to the top-cap layer. */
    private static final int TOP_CAP_UP = 5;

    private static final int PARALLEL_DAY_SURFACE = 6;
    private static final int PARALLEL_DAY_UNDERGROUND = 2;
    private static final int PARALLEL_NIGHT = 4;

    public SolarisFurnaceMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    // ------------------------------------------------------------------------------------------------
    // Solar state
    // ------------------------------------------------------------------------------------------------

    /**
     * True when every block of the 5x5 top cap has an unobstructed column of sky above it. Computed
     * from the controller's facing rather than the structure cache so it is safe to call on either
     * side (the client evaluates it for the UI, the server for recipe modification).
     */
    public boolean isSkyExposed() {
        Level level = getLevel();
        if (level == null || !isFormed()) return false;
        if (!level.dimensionType().hasSkyLight()) return false;

        Direction front = getFrontFacing();
        Direction up = RelativeDirection.UP.getRelative(front, getUpwardsFacing(), isFlipped());
        Direction right = RelativeDirection.RIGHT.getRelative(front, getUpwardsFacing(), isFlipped());
        BlockPos origin = getPos();

        for (int dRight = -CAP_RADIUS; dRight <= CAP_RADIUS; dRight++) {
            // The cap extends from the controller row (dFront 0) back to dFront -(2*RADIUS).
            for (int dBack = 0; dBack <= CAP_RADIUS * 2; dBack++) {
                int dx = front.getStepX() * -dBack + up.getStepX() * TOP_CAP_UP + right.getStepX() * dRight;
                int dy = front.getStepY() * -dBack + up.getStepY() * TOP_CAP_UP + right.getStepY() * dRight;
                int dz = front.getStepZ() * -dBack + up.getStepZ() * TOP_CAP_UP + right.getStepZ() * dRight;
                if (!level.canSeeSky(origin.offset(dx, dy, dz).above())) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isDaytime() {
        Level level = getLevel();
        return level != null && level.isDay();
    }

    private boolean isAboveSeaLevel() {
        Level level = getLevel();
        int seaLevel = level != null ? level.getSeaLevel() : DEFAULT_SEA_LEVEL;
        return getPos().getY() >= seaLevel;
    }

    private boolean isHighAltitude() {
        return getPos().getY() > HIGH_ALTITUDE_Y;
    }

    /** Sealed off from the sky: the machine crawls at half speed with no parallel. */
    private boolean isSealed() {
        return !isSkyExposed();
    }

    /** Maximum parallel this build is allowed right now (before capping by available inputs/power). */
    private int maxParallel() {
        if (isSealed()) return 1;
        if (!isDaytime()) return PARALLEL_NIGHT;
        return isAboveSeaLevel() ? PARALLEL_DAY_SURFACE : PARALLEL_DAY_UNDERGROUND;
    }

    // ------------------------------------------------------------------------------------------------
    // Recipe modification
    // ------------------------------------------------------------------------------------------------

    public static ModifierFunction modifyRecipe(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof SolarisFurnaceMachine furnace)) {
            return RecipeModifier.nullWrongType(SolarisFurnaceMachine.class, machine);
        }
        // Typical EBF behaviour first: coil-temperature gate, coil EU discount and non-perfect overclock.
        ModifierFunction ebf = GTRecipeModifiers.ebfOverclock(machine, recipe);
        GTRecipe overclocked = ebf.apply(recipe);
        if (overclocked == null) {
            // Coil temperature too low / insufficient voltage: propagate the cancel + its fail reason.
            return ebf;
        }
        // Solar upgrades are applied on top of the already-overclocked recipe.
        ModifierFunction solar = furnace.buildSolarModifier(overclocked);
        return ebf.andThen(solar);
    }

    private ModifierFunction buildSolarModifier(GTRecipe overclocked) {
        int parallels = Math.max(1, ParallelLogic.getParallelAmount(this, overclocked, maxParallel()));

        double durationMultiplier = isSealed() ? 2.0 : 1.0;    // sealed off -> half speed
        double eutMultiplier = parallels * (isHighAltitude() ? 0.7 : 1.0); // above y75 -> 30% less power

        ModifierFunction.FunctionBuilder builder = ModifierFunction.builder()
                .parallels(parallels)
                .durationMultiplier(durationMultiplier)
                .eutMultiplier(eutMultiplier);
        if (parallels > 1) {
            builder.modifyAllContents(ContentModifier.multiplier(parallels));
        }
        return builder.build();
    }

    // ------------------------------------------------------------------------------------------------
    // UI
    // ------------------------------------------------------------------------------------------------

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        super.addDisplayText(textList);
        if (!isFormed()) return;

        if (isSealed()) {
            textList.add(Component.translatable("wfcore.machine.solaris_furnace.sky_covered")
                    .withStyle(ChatFormatting.GRAY));
            textList.add(Component.translatable("wfcore.machine.solaris_furnace.slow")
                    .withStyle(ChatFormatting.RED));
        } else {
            textList.add(Component.translatable("wfcore.machine.solaris_furnace.sky_exposed")
                    .withStyle(ChatFormatting.GREEN));
            textList.add(Component.translatable(isDaytime()
                            ? "wfcore.machine.solaris_furnace.time_day"
                            : "wfcore.machine.solaris_furnace.time_night")
                    .withStyle(isDaytime() ? ChatFormatting.GOLD : ChatFormatting.BLUE));
            textList.add(Component.translatable("wfcore.machine.solaris_furnace.parallel", maxParallel())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (isHighAltitude()) {
            textList.add(Component.translatable("wfcore.machine.solaris_furnace.power_save")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
