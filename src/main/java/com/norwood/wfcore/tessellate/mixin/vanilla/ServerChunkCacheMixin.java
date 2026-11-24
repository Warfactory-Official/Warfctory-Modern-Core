package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DistanceManager/TickingTracker mutation off the server thread => replayed on it. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    public abstract <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks);

    @Shadow
    public abstract <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks);

    @Shadow
    public abstract void updateChunkForced(ChunkPos pos, boolean add);

    @Inject(method = "addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V",
            at = @At("HEAD"), cancellable = true)
    private <T> void wfcore$addOnMain(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks, CallbackInfo ci) {
        if (Thread.currentThread() != mainThread) {
            RegionThreads.executeOnMain("addRegionTicket", () -> addRegionTicket(type, pos, distance, value, forceTicks));
            ci.cancel();
        }
    }

    @Inject(method = "removeRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V",
            at = @At("HEAD"), cancellable = true)
    private <T> void wfcore$removeOnMain(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks, CallbackInfo ci) {
        if (Thread.currentThread() != mainThread) {
            RegionThreads.executeOnMain("removeRegionTicket", () -> removeRegionTicket(type, pos, distance, value, forceTicks));
            ci.cancel();
        }
    }

    @Inject(method = "updateChunkForced", at = @At("HEAD"), cancellable = true)
    private void wfcore$forcedOnMain(ChunkPos pos, boolean add, CallbackInfo ci) {
        if (Thread.currentThread() != mainThread) {
            RegionThreads.executeOnMain("updateChunkForced", () -> updateChunkForced(pos, add));
            ci.cancel();
        }
    }
}
