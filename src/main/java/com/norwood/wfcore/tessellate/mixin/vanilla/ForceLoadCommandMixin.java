package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.commands.ForceLoadCommand;
import net.minecraft.server.level.ColumnPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Command-block {@code /forceload} runs on a region worker. Whole add/remove/remove-all body replays on the
 * server thread so feedback and failures stay exact; worker-side result is 0.
 */
@Mixin(ForceLoadCommand.class)
public abstract class ForceLoadCommandMixin {

    @Shadow
    private static int changeForceLoad(CommandSourceStack source, ColumnPos from, ColumnPos to, boolean add) throws CommandSyntaxException {
        throw new AssertionError();
    }

    @Shadow
    private static int removeAll(CommandSourceStack source) {
        throw new AssertionError();
    }

    @Inject(method = "changeForceLoad", at = @At("HEAD"), cancellable = true)
    private static void wfcore$changeOnMain(CommandSourceStack source, ColumnPos from, ColumnPos to, boolean add,
                                            CallbackInfoReturnable<Integer> cir) {
        if (!source.getServer().isSameThread()) {
            RegionThreads.executeOnMain("forceload " + (add ? "add" : "remove"), () -> {
                try {
                    changeForceLoad(source, from, to, add);
                } catch (CommandSyntaxException e) {
                    source.sendFailure(ComponentUtils.fromMessage(e.getRawMessage()));
                }
            });
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "removeAll", at = @At("HEAD"), cancellable = true)
    private static void wfcore$removeAllOnMain(CommandSourceStack source, CallbackInfoReturnable<Integer> cir) {
        if (!source.getServer().isSameThread()) {
            RegionThreads.executeOnMain("forceload remove all", () -> removeAll(source));
            cir.setReturnValue(0);
        }
    }
}
