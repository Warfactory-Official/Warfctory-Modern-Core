package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.norwood.wfcore.tessellate.RegionThreads;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.function.Predicate;

/**
 * Box not wholly owned by the calling region => server thread (its {@code Level.setBlock} leases each target chunk);
 * a volume has no single owner. Worker-side result 0, feedback at replay.
 */
@Mixin(FillCommand.class)
public abstract class FillCommandMixin {

    @WrapMethod(method = "fillBlocks")
    private static int wfcore$toMain(CommandSourceStack source, BoundingBox area, BlockInput newBlock, @Coerce Object mode,
                                     @Nullable Predicate<BlockInWorld> replacingPredicate, Operation<Integer> original) {
        if (!Routing.ownsAll(source.getLevel(), area)) {
            RegionThreads.executeOnMain("fill", Routing.reporting(source, () -> original.call(source, area, newBlock, mode, replacingPredicate)));
            return 0;
        }
        return original.call(source, area, newBlock, mode, replacingPredicate);
    }
}
