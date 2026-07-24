package com.norwood.wfcore.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(EntityRenderer.class)
public class NamePlateMixin {

    @Redirect(
              method = "renderNameTag",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isDiscrete()Z"))
    private boolean wfcore$nameTagDepth(Entity entity) {
        return true;
    }
}
