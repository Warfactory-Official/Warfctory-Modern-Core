package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.Set;

/**
 * Entity owned by another region => its owner runs the teleport (post-move fix-ups touch its motion/navigation);
 * the owner's {@code teleportTo} is then deferred to the server thread by Tessellate / {@link ServerPlayerMixin}.
 */
@Mixin(TeleportCommand.class)
public abstract class TeleportCommandMixin {

    @WrapMethod(method = "performTeleport")
    private static void wfcore$toOwner(CommandSourceStack source, Entity entity, ServerLevel level, double x, double y, double z,
                                       Set<RelativeMovement> relative, float yaw, float pitch, @Coerce Object facing,
                                       Operation<Void> original) {
        if (!Routing.toOwner("tp", entity,
                Routing.reporting(source, () -> original.call(source, entity, level, x, y, z, relative, yaw, pitch, facing)))) {
            original.call(source, entity, level, x, y, z, relative, yaw, pitch, facing);
        }
    }
}
