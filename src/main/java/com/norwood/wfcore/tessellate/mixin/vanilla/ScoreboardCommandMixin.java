package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.OperationArgument;
import net.minecraft.server.commands.ScoreboardCommand;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;

/** {@code score.set(score.get() + n)} from two regions loses updates; whole body under {@link Routing#SCORE_LOCK}. */
@Mixin(ScoreboardCommand.class)
public abstract class ScoreboardCommandMixin {

    @WrapMethod(method = "addScore")
    private static int wfcore$add(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int amount,
                                  Operation<Integer> original) {
        synchronized (Routing.SCORE_LOCK) {
            return original.call(source, targets, objective, amount);
        }
    }

    @WrapMethod(method = "removeScore")
    private static int wfcore$remove(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int amount,
                                     Operation<Integer> original) {
        synchronized (Routing.SCORE_LOCK) {
            return original.call(source, targets, objective, amount);
        }
    }

    @WrapMethod(method = "performOperation")
    private static int wfcore$operation(CommandSourceStack source, Collection<ScoreHolder> targets, Objective targetObjective,
                                        OperationArgument.Operation operation, Collection<ScoreHolder> sources,
                                        Objective sourceObjective, Operation<Integer> original) {
        synchronized (Routing.SCORE_LOCK) {
            return original.call(source, targets, targetObjective, operation, sources, sourceObjective);
        }
    }
}
