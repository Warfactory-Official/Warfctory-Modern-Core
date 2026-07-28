package com.norwood.wfcore.handlers;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.api.research.ResearchAccessCache;
import com.norwood.wfcore.api.research.ResearchCategoryRegistry;
import com.norwood.wfcore.api.research.ResearchRegistry;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
public final class ResearchCacheEvents {

    private ResearchCacheEvents() {}

    /**
     * Registers a reload listener (ahead of KubeJS, via HIGH priority) that wipes both research
     * registries before server scripts re-populate them. Fires on every server start and /reload.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Object>() {
            @Override
            protected Object prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Object object, ResourceManager resourceManager, ProfilerFiller profiler) {
                WFCore.LOGGER.info("[WFResearch] clearing research registry");
                ResearchRegistry.clear();
                ResearchCategoryRegistry.clear();
            }
        });
    }

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
