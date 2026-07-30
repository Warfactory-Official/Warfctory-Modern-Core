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

    // NOTE: we intentionally do NOT clear ResearchRegistry / ResearchCategoryRegistry on
    // AddReloadListenerEvent anymore. That listener fired on every /reload and wiped the
    // registries, but the KubeJS registration lives in server scripts that re-run on /reload
    // (ServerEvents.recipes) — and reload listeners run *after* KubeJS's recipe pass, so the
    // wipe happened last and left the research tree blank until a full restart. The registries
    // are keyed by id (put-replace), so re-running the scripts on /reload updates nodes in place
    // without needing a wipe. (Nodes deleted from a script linger until restart — acceptable.)

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
