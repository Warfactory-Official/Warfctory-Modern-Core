package com.norwood.wfcore.tessellate.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code patches/lithium-tessellate/0002}: {@code Thread.currentThread() == this.thread} also true on region workers,
 * which answer loaded-chunk reads without blocking. Needs Lithium's merged method: priority 2000 > 1000.
 */
@Mixin(value = Level.class, priority = 2000)
public abstract class LevelBlockEntityRetrievalMixin {

    @Shadow
    @Final
    private Thread thread;

    @WrapOperation(method = "lithium$getLoadedExistingBlockEntity", remap = false, at = @At(value = "INVOKE", target =
            "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"))
    private Thread wfcore$regionWorkerIsOwner(Operation<Thread> original) {
        return RegionThreads.isWorkerThread() ? this.thread : original.call();
    }
}
