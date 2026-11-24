package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.norwood.wfcore.tessellate.RegionThreads;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.function.Predicate;

/** Two volumes in possibly two dimensions: region thread => server thread (leases each chunk). Worker-side result 0. */
@Mixin(CloneCommands.class)
public abstract class CloneCommandsMixin {

    @WrapMethod(method = "clone")
    private static int wfcore$toMain(CommandSourceStack source, @Coerce Object begin, @Coerce Object end, @Coerce Object target,
                                     Predicate<BlockInWorld> filter, @Coerce Object mode, Operation<Integer> original) {
        if (RegionThreads.isRegionThread()) {
            RegionThreads.executeOnMain("clone", Routing.reporting(source, () -> original.call(source, begin, end, target, filter, mode)));
            return 0;
        }
        return original.call(source, begin, end, target, filter, mode);
    }
}
