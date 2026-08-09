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

import com.gregtechceu.gtceu.utils.FormattingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SolarisFurnaceMachine extends CoilWorkableElectricMultiblockMachine {

    private static final int DEFAULT_SEA_LEVEL = 63;
    private static final int HIGH_ALTITUDE_Y = 75;
    private static final int CAP_RADIUS = 2;
    private static final int TOP_CAP_UP = 5;

    private static final int PARALLEL_DAY_SURFACE = 6;
    private static final int PARALLEL_UNDERGROUND = 2;
    private static final int PARALLEL_NIGHT = 4;

    public SolarisFurnaceMachine(IMachineBlockEntity holder) {
        super(holder);
    }


    public boolean isSkyExposed() {
        Level level = getLevel();
        if (level == null || !isFormed()) return false;
        if (!level.dimensionType().hasSkyLight()) return false;

        Direction front = getFrontFacing();
        Direction up = RelativeDirection.UP.getRelative(front, getUpwardsFacing(), isFlipped());
        Direction right = RelativeDirection.RIGHT.getRelative(front, getUpwardsFacing(), isFlipped());
        BlockPos origin = getPos();

        for (int dRight = -CAP_RADIUS; dRight <= CAP_RADIUS; dRight++) {
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

    private boolean isSealed() {
        return !isSkyExposed();
    }

    private int maxParallel() {
        if (isSealed()) return 1;

        if (!isAboveSeaLevel()) return PARALLEL_UNDERGROUND;
        return isDaytime() ? PARALLEL_DAY_SURFACE : PARALLEL_NIGHT;
    }

    public static ModifierFunction modifyRecipe(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof SolarisFurnaceMachine furnace)) {
            return RecipeModifier.nullWrongType(SolarisFurnaceMachine.class, machine);
        }
        ModifierFunction ebf = GTRecipeModifiers.ebfOverclock(machine, recipe);
        GTRecipe overclocked = ebf.apply(recipe);
        if (overclocked == null) {
            return ebf;
        }
        ModifierFunction solar = furnace.buildSolarModifier(overclocked);
        return ebf.andThen(solar);
    }

    private ModifierFunction buildSolarModifier(GTRecipe overclocked) {

        int parallels = Math.max(1, ParallelLogic.getParallelAmountWithoutEU(this, overclocked, maxParallel()));

        double durationMultiplier = isSealed() ? 2.0 : 1.0;
        double eutMultiplier = isHighAltitude() ? 0.7 : 1.0;

        ModifierFunction.FunctionBuilder builder = ModifierFunction.builder()
                .parallels(parallels)
                .durationMultiplier(durationMultiplier)
                .eutMultiplier(eutMultiplier);
        if (parallels > 1) {
            builder.modifyAllContents(ContentModifier.multiplier(parallels));
        }
        return builder.build();
    }



    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        super.addDisplayText(textList);
        textList.add(Component.translatable(
                "gtceu.multiblock.blast_furnace.max_temperature",
                Component.translatable(FormattingUtil.formatNumbers(getCoilType().getCoilTemperature()) + "K")
                        .withStyle(ChatFormatting.RED)));
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
