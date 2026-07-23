package com.norwood.wfcore.common.ballistics;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

public final class BallisticsEvents {

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent e) {

        if (e.side == LogicalSide.SERVER && e.phase == TickEvent.Phase.END
                && e.level instanceof ServerLevel sl) {
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
