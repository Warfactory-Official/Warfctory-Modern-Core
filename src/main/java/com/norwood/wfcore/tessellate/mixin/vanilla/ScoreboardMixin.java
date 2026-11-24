package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Scoreboard structure from region workers: maps synchronized/concurrent, check-then-act mutators under the
 * instance monitor. Score value read-modify-write is locked per command ({@link ScoreboardCommandMixin}).
 */
@Mixin(Scoreboard.class)
public abstract class ScoreboardMixin {

    @Shadow
    @Mutable
    @Final
    private Object2ObjectMap<String, Objective> objectivesByName;

    @Shadow
    @Mutable
    @Final
    private Reference2ObjectMap<ObjectiveCriteria, List<Objective>> objectivesByCriteria;

    @Shadow
    @Mutable
    @Final
    @SuppressWarnings("rawtypes")
    private Map playerScores;

    @Shadow
    @Mutable
    @Final
    private Object2ObjectMap<String, PlayerTeam> teamsByName;

    @Shadow
    @Mutable
    @Final
    private Object2ObjectMap<String, PlayerTeam> teamsByPlayer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$concurrentMaps(CallbackInfo ci) {
        objectivesByName = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>(16, 0.5F));
        objectivesByCriteria = Reference2ObjectMaps.synchronize(new Reference2ObjectOpenHashMap<>());
        playerScores = new ConcurrentHashMap<>();
        teamsByName = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
        teamsByPlayer = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>());
    }

    @WrapMethod(method = "addObjective")
    private Objective wfcore$addObjective(String name, ObjectiveCriteria criteria, Component displayName,
                                          ObjectiveCriteria.RenderType renderType, boolean displayAutoUpdate,
                                          @Nullable NumberFormat numberFormat, Operation<Objective> original) {
        synchronized (this) {
            return original.call(name, criteria, displayName, renderType, displayAutoUpdate, numberFormat);
        }
    }

    @WrapMethod(method = "removeObjective")
    private void wfcore$removeObjective(Objective objective, Operation<Void> original) {
        synchronized (this) {
            original.call(objective);
        }
    }

    @WrapMethod(method = "forAllObjectives")
    private void wfcore$forAll(ObjectiveCriteria criteria, ScoreHolder holder, Consumer<ScoreAccess> action, Operation<Void> original) {
        synchronized (this) {
            original.call(criteria, holder, action);
        }
    }

    @WrapMethod(method = "getOrCreatePlayerScore(Lnet/minecraft/world/scores/ScoreHolder;Lnet/minecraft/world/scores/Objective;Z)Lnet/minecraft/world/scores/ScoreAccess;")
    private ScoreAccess wfcore$getOrCreate(ScoreHolder holder, Objective objective, boolean readOnly, Operation<ScoreAccess> original) {
        synchronized (this) {
            return original.call(holder, objective, readOnly);
        }
    }

    @WrapMethod(method = "resetAllPlayerScores")
    private void wfcore$resetAll(ScoreHolder holder, Operation<Void> original) {
        synchronized (this) {
            original.call(holder);
        }
    }

    @WrapMethod(method = "resetSinglePlayerScore")
    private void wfcore$resetSingle(ScoreHolder holder, Objective objective, Operation<Void> original) {
        synchronized (this) {
            original.call(holder, objective);
        }
    }

    @WrapMethod(method = "setDisplayObjective")
    private void wfcore$display(DisplaySlot slot, @Nullable Objective objective, Operation<Void> original) {
        synchronized (this) {
            original.call(slot, objective);
        }
    }

    @WrapMethod(method = "addPlayerTeam")
    private PlayerTeam wfcore$addTeam(String name, Operation<PlayerTeam> original) {
        synchronized (this) {
            return original.call(name);
        }
    }

    @WrapMethod(method = "removePlayerTeam")
    private void wfcore$removeTeam(PlayerTeam team, Operation<Void> original) {
        synchronized (this) {
            original.call(team);
        }
    }

    @WrapMethod(method = "addPlayerToTeam")
    private boolean wfcore$join(String player, PlayerTeam team, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(player, team);
        }
    }

    @WrapMethod(method = "removePlayerFromTeam(Ljava/lang/String;)Z")
    private boolean wfcore$leave(String player, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(player);
        }
    }

    @WrapMethod(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V")
    private void wfcore$leaveTeam(String player, PlayerTeam team, Operation<Void> original) {
        synchronized (this) {
            original.call(player, team);
        }
    }
}
