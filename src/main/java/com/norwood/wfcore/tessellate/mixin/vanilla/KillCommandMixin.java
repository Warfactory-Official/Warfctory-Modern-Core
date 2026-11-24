package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entity owned by another region => its owner kills it; success count assumed. */
@Mixin(KillCommand.class)
public abstract class KillCommandMixin {

    @WrapOperation(method = "kill", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;kill()V"))
    private static void wfcore$kill(Entity entity, Operation<Void> original) {
        if (!Routing.toOwner("kill", entity, entity::kill)) {
            original.call(entity);
        }
    }
}
