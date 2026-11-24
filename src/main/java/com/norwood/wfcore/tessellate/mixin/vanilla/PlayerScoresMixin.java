package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import java.util.function.Consumer;

/** Per-holder objective map ({@code Reference2ObjectOpenHashMap}): every access under the instance monitor. */
@Mixin(targets = "net.minecraft.world.scores.PlayerScores")
public abstract class PlayerScoresMixin {

    @WrapMethod(method = "get")
    private Score wfcore$get(Objective objective, Operation<Score> original) {
        synchronized (this) {
            return original.call(objective);
        }
    }

    @WrapMethod(method = "getOrCreate")
    private Score wfcore$getOrCreate(Objective objective, Consumer<Score> creator, Operation<Score> original) {
        synchronized (this) {
            return original.call(objective, creator);
        }
    }

    @WrapMethod(method = "remove")
    private boolean wfcore$remove(Objective objective, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(objective);
        }
    }

    @WrapMethod(method = "hasScores")
    private boolean wfcore$hasScores(Operation<Boolean> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "listScores")
    private Object2IntMap<Objective> wfcore$list(Operation<Object2IntMap<Objective>> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "setScore")
    private void wfcore$set(Objective objective, Score score, Operation<Void> original) {
        synchronized (this) {
            original.call(objective, score);
        }
    }

    @WrapMethod(method = "listRawScores")
    private Map<Objective, Score> wfcore$raw(Operation<Map<Objective, Score>> original) {
        synchronized (this) {
            return original.call();
        }
    }
}
