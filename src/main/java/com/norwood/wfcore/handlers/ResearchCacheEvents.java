package com.norwood.wfcore.handlers;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.api.research.ResearchAccessCache;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
public final class ResearchCacheEvents {

    private ResearchCacheEvents() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        invalidate(event);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        invalidate(event);
    }

    private static void invalidate(ChunkEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        ResearchAccessCache.invalidateChunk(level.dimension(), pos.toLong());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ResearchAccessCache.clear();
    }
}
