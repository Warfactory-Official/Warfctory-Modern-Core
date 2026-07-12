package com.norwood.wfcore.mixin;

import net.minecraft.world.entity.Entity;

import com.flansmod.warforge.client.WarForgeClientEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stops Warforge faction nameplates from rendering through walls, mirroring {@link NamePlateMixin}'s
 * technique of hijacking the sneak/discrete flag that drives the see-through pass.
 *
 * <p>{@code WarForgeClientEventHandler#onRenderNameTag} forwards {@code entity.isDiscrete()} to
 * {@code FullColorNameplate#drawNameplate} as its {@code isSneaking} argument. When that flag is
 * {@code false} the nameplate is drawn with a {@code SEE_THROUGH} (no-depth-test) pass followed by a
 * {@code NORMAL} pass, so the faction tag bleeds through terrain; when it is {@code true} only the
 * depth-tested {@code NORMAL} pass runs, occluding the tag behind blocks. Redirecting the
 * {@code isDiscrete()} call to always return {@code true} forces that occluded path.
 *
 * <p>Note this returns the opposite constant to {@link NamePlateMixin} (which forces the see-through
 * path on for vanilla names) precisely because the desired outcome here is the reverse: faction tags
 * that respect walls.
 */
@Mixin(WarForgeClientEventHandler.class)
public class WarforgeNamePlateMixin {

    @Redirect(
              method = "onRenderNameTag",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isDiscrete()Z"))
    private static boolean wfcore$factionNameTagDepth(Entity entity) {
        return true;
    }
}
