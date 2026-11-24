package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.server.commands.DamageCommand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entity owned by another region => its owner hurts it; {@code hurt} flag assumed true. */
@Mixin(DamageCommand.class)
public abstract class DamageCommandMixin {

    @WrapOperation(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private static boolean wfcore$hurt(Entity entity, DamageSource source, float amount, Operation<Boolean> original) {
        return Routing.toOwner("damage", entity, () -> entity.hurt(source, amount)) || original.call(entity, source, amount);
    }
}
