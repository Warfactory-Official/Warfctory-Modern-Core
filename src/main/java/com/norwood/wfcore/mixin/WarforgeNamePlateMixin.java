package com.norwood.wfcore.mixin;

import net.minecraft.world.entity.Entity;

import com.flansmod.warforge.client.WarForgeClientEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(WarForgeClientEventHandler.class)
public class WarforgeNamePlateMixin {

    @Redirect(
              method = "onRenderNameTag",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isDiscrete()Z"))
    private static boolean wfcore$factionNameTagDepth(Entity entity) {
        return true;
    }
}
