package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.wfcore.tessellate.RegionThreads;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Probes arbitrary columns with synchronous chunk loads: on a worker that is a serial fallback => server thread. Worker-side result 0. */
@Mixin(SpreadPlayersCommand.class)
public abstract class SpreadPlayersCommandMixin {

    @Shadow
    private static int spreadPlayers(CommandSourceStack source, Vec2 center, float spreadDistance, float maxRange, int maxHeight,
                                     boolean respectTeams, Collection<? extends Entity> targets) throws CommandSyntaxException {
        throw new AssertionError();
    }

    @Inject(method = "spreadPlayers", at = @At("HEAD"), cancellable = true)
    private static void wfcore$toMain(CommandSourceStack source, Vec2 center, float spreadDistance, float maxRange, int maxHeight,
                                      boolean respectTeams, Collection<? extends Entity> targets, CallbackInfoReturnable<Integer> cir) {
        if (!source.getServer().isSameThread()) {
            RegionThreads.executeOnMain("spreadplayers", Routing.reporting(source,
                    () -> spreadPlayers(source, center, spreadDistance, maxRange, maxHeight, respectTeams, targets)));
            cir.setReturnValue(0);
        }
    }
}
