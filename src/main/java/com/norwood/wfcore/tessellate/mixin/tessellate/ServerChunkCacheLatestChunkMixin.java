package com.norwood.wfcore.tessellate.mixin.tessellate;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@code patches/c2me/0001}: worker chunk read falls back to the holder's latest chunk when the ticket-gated status
 * read is null. Targets the method Tessellate's own {@code ServerChunkCacheMixin} merges: needs the later apply order
 * (priority 2000 > default 1000).
 */
@Mixin(value = ServerChunkCache.class, priority = 2000)
public abstract class ServerChunkCacheLatestChunkMixin {

    @WrapOperation(method = "tessellate$getChunkForWorker", remap = false, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ChunkHolder;getChunkIfPresent(Lnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess;"))
    private ChunkAccess wfcore$latestChunk(ChunkHolder holder, ChunkStatus status, Operation<ChunkAccess> original) {
        ChunkAccess chunk = original.call(holder, status);
        if (chunk != null) {
            return chunk;
        }
        ChunkAccess latest = holder.getLatestChunk();
        if (latest != null && latest.getPersistedStatus().isOrAfter(status)
                && (status != ChunkStatus.FULL || latest instanceof LevelChunk)) {
            return latest;
        }
        return null;
    }
}
