package com.norwood.wfcore.radar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import com.norwood.wfcore.radar.data.RadarRegistryData;

/**
 * Keeps the per-level {@link RadarRegistryData} machine map in sync with the world: whitelisted
 * blocks register their (x, z) + richness on placement and deregister on break.
 *
 * <p>
 * The 1.12.2 version mixed into vanilla {@code World#addTileEntity}/{@code removeTileEntity};
 * modern uses Forge's block place/break events, which is enough since {@link RadarRegistryData} is
 * persisted {@link net.minecraft.world.level.saveddata.SavedData} (positions survive chunk unloads).
 */
public class RadarRegistryHandler {

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getPlacedBlock().getBlock());
        if (id != null && RadarConfig.isWhitelisted(id)) {
            RadarRegistryData.get(level).addMachine(event.getPos().getX(), event.getPos().getZ(),
                    RadarConfig.getValue(id));
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
        if (id != null && RadarConfig.isWhitelisted(id)) {
            RadarRegistryData.get(level).removeMachine(event.getPos().getX(), event.getPos().getZ());
        }
    }
}
