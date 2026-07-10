package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.norwood.wfcore.api.research.ResearchAccessCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelBlockEntityChangedMixin {

    @Inject(method = "blockEntityChanged", at = @At("HEAD"))
    private void wfcore$invalidateResearchCache(BlockPos pos, CallbackInfo ci) {
        Level self = (Level) (Object) this;
        if (self.isClientSide) {
            return;
        }
        ResearchAccessCache.invalidateChunk(self.dimension(), ChunkPos.asLong(pos));
    }
}
