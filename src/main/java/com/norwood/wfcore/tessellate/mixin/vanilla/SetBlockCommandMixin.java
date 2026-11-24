package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/** Foreign position => owner region runs the command body; worker-side result 0, feedback at replay. */
@Mixin(SetBlockCommand.class)
public abstract class SetBlockCommandMixin {

    @Shadow
    private static int setBlock(CommandSourceStack source, BlockPos pos, BlockInput state, SetBlockCommand.Mode mode,
                                @Nullable Predicate<BlockInWorld> predicate) throws CommandSyntaxException {
        throw new AssertionError();
    }

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private static void wfcore$toOwner(CommandSourceStack source, BlockPos pos, BlockInput state, SetBlockCommand.Mode mode,
                                       @Nullable Predicate<BlockInWorld> predicate, CallbackInfoReturnable<Integer> cir) {
        if (Routing.toOwner("setblock", source.getLevel(), pos,
                Routing.reporting(source, () -> setBlock(source, pos, state, mode, predicate)))) {
            cir.setReturnValue(0);
        }
    }
}
