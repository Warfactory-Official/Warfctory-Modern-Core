package com.norwood.wfcore.tessellate.mixin.ldlib2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.wfcore.tessellate.ldlib2.AsyncThreads;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@code notifyPersistence}: post to main only from the LDLib async thread; else run inline on the owner thread. */
@Mixin(targets = "com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.IBlockEntityManaged", remap = false)
public interface IBlockEntityManagedMixin {

    @WrapOperation(method = "notifyPersistence", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;executeIfPossible(Ljava/lang/Runnable;)V"))
    private static void wfcore$setChangedInline(MinecraftServer server, Runnable task, Operation<Void> original) {
        if (AsyncThreads.onAsyncThread()) {
            original.call(server, task);
        } else {
            task.run();
        }
    }
}
