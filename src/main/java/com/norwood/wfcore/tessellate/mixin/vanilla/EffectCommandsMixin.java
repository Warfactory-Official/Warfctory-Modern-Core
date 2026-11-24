package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.EffectCommands;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entity owned by another region => its owner applies/clears the effect; result flags assumed true. */
@Mixin(EffectCommands.class)
public abstract class EffectCommandsMixin {

    @WrapOperation(method = "giveEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean wfcore$give(LivingEntity entity, MobEffectInstance effect, Entity cause, Operation<Boolean> original) {
        return Routing.toOwner("effect give", entity, () -> entity.addEffect(effect, cause)) || original.call(entity, effect, cause);
    }

    @WrapOperation(method = "clearEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeAllEffects()Z"))
    private static boolean wfcore$clearAll(LivingEntity entity, Operation<Boolean> original) {
        return Routing.toOwner("effect clear", entity, entity::removeAllEffects) || original.call(entity);
    }

    @WrapOperation(method = "clearEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;removeEffect(Lnet/minecraft/core/Holder;)Z"))
    private static boolean wfcore$clearOne(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        return Routing.toOwner("effect clear", entity, () -> entity.removeEffect(effect)) || original.call(entity, effect);
    }
}
