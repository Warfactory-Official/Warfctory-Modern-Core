package com.norwood.wfcore.tessellate.mixin.umc;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.norwood.wfcore.tessellate.RegionThreads;
import com.norwood.wfcore.tessellate.umc.UmcLocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

/**
 * {@code patches/umc/0001} (container types: {@code UmcTransformer}) + {@code 0005}. Add claims the entity id under the
 * World monitor (second add of the same entity returns before join); readers stay lock-free over concurrent containers.
 */
@Mixin(targets = "cam72cam.mod.world.World", remap = false)
public abstract class WorldThreadSafetyMixin {

    @Shadow
    private Map<Integer, Object> entityByID;

    @WrapMethod(method = "onEntityAdded")
    private void wfcore$claimOnAdd(Entity entity, Operation<Void> original) {
        synchronized (this) {
            if (entityByID.containsKey(entity.getId())) {
                return;
            }
            original.call(entity);
        }
    }

    @WrapMethod(method = "onEntityRemoved")
    private void wfcore$removeUnderMonitor(Entity entity, Operation<Void> original) {
        synchronized (this) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "loadWorld")
    private static void wfcore$loadOnce(Level level, Operation<Void> original) {
        synchronized (UmcLocks.WORLDS) {
            original.call(level);
        }
    }

    @WrapOperation(method = "getRedstone", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"))
    private int wfcore$loadedNeighboursOnly(Level level, BlockPos pos, Direction direction, Operation<Integer> original) {
        return level.hasChunkAt(pos) ? original.call(level, pos, direction) : 0;
    }

    /**
     * Region worker: state read from the chunk held, absent => air. {@code isBlockLoaded} then {@code isBlock} on an
     * unloading chunk => {@code getBlockState} re-fetches, worker cannot load => Tessellate serial fallback.
     */
    @WrapOperation(method = "isBlock", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState wfcore$heldChunkOnly(Level level, BlockPos pos, Operation<BlockState> original) {
        if (!RegionThreads.isWorkerThread()) {
            return original.call(level, pos);
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }
}
