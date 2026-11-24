package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TriggerCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;

/** {@code ScoreAccess.add} is get+set: same lock as the scoreboard command. */
@Mixin(TriggerCommand.class)
public abstract class TriggerCommandMixin {

    @WrapMethod(method = "addValue")
    private static int wfcore$add(CommandSourceStack source, ServerPlayer player, Objective objective, int value, Operation<Integer> original) {
        synchronized (Routing.SCORE_LOCK) {
            return original.call(source, player, objective, value);
        }
    }

    @WrapMethod(method = "simpleTrigger")
    private static int wfcore$simple(CommandSourceStack source, ServerPlayer player, Objective objective, Operation<Integer> original) {
        synchronized (Routing.SCORE_LOCK) {
            return original.call(source, player, objective);
        }
    }
}
