package com.norwood.wfcore.radar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.data.RadarRegistryData;

/**
 * Keeps the per-level {@link RadarRegistryData} machine map in sync with the world: whitelisted
 * blocks register their (x, z) + richness on placement and deregister on break.
 *
 * <p>
 * The 1.12.2 version mixed into vanilla {@code World#addTileEntity}/{@code removeTileEntity};
 * modern uses Forge's block place/break events, which is enough since {@link RadarRegistryData} is
 * persisted {@link net.minecraft.world.level.saveddata.SavedData} (positions survive chunk unloads).
 *
 * <p>
 * The registry update is wrapped so a failure here (radar is a non-critical feature) degrades to a logged
 * warning instead of propagating out of a block place/break and crashing the server tick loop.
 */
public class RadarRegistryHandler {

    private static boolean loggedFailure;

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock());
            if (id != null && RadarConfig.isWhitelisted(id)) {
                RadarRegistryData.get(level).addMachine(event.getPos().getX(), event.getPos().getZ(),
                        RadarConfig.getValue(id));
            }
        } catch (Exception | LinkageError t) {
            logRadarFailure(t);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
            if (id != null && RadarConfig.isWhitelisted(id)) {
                RadarRegistryData.get(level).removeMachine(event.getPos().getX(), event.getPos().getZ());
            }
        } catch (Exception | LinkageError t) {
            logRadarFailure(t);
        }
    }

    /** Log the first radar-registry failure (with the cause) and suppress the rest so it can't spam or crash. */
    private static void logRadarFailure(Throwable t) {
        if (!loggedFailure) {
            loggedFailure = true;
            WFCore.LOGGER.warn("[radar] block-registry update failed; radar tracking skipped for this block "
                    + "(further such errors suppressed this session)", t);
        }
    }
}
