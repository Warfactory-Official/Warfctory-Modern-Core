package com.norwood.wfcore.common.ballistics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

public final class BallisticsEvents {

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post e) {

        // LevelTickEvent.Post already pins the END phase; the ServerLevel check subsumes the old
        // LogicalSide.SERVER test.
        if (e.getLevel() instanceof ServerLevel sl) {
            BallisticsManager.get(sl).tick(sl.getServer().getTickCount());
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load e) {
        if (e.getLevel() instanceof ServerLevel sl && !e.getLevel().isClientSide()) {
            ChunkAccess chunk = e.getChunk();
            ChunkPos pos = chunk.getPos();
            BallisticsManager mgr = BallisticsManager.get(sl);

            mgr.terrain().invalidateChunk(pos.x, pos.z);

            mgr.drainDeferred(pos.x, pos.z);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel sl) {
            BallisticsManager.forgetLevel(sl);
        }
    }
}
