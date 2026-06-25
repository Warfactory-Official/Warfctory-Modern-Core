package com.norwood.wfcore.common.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;

import java.util.function.Supplier;

/**
 * Empties every chest/fishing loot table by default ({@link WFCoreConfig#isClearStructureLoot()}); a
 * {@link WFLootRegistry} override replaces a table outright, and a keep-entry spares it from the wipe.
 */
public final class WFLootEvents {

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation id = event.getName();

        Supplier<LootTable> override = WFLootRegistry.override(id);
        if (override != null) {
            try {
                event.setTable(override.get());
            } catch (RuntimeException e) {
                WFCore.LOGGER.error("WFLoot: failed to build loot override for {}", id, e);
            }
            return;
        }
        if (WFLootRegistry.isKept(id)) return;

        LootContextParamSet paramSet = event.getTable().getParamSet();
        if (WFCoreConfig.isClearStructureLoot() && isManaged(paramSet)) {
            event.setTable(LootTable.lootTable().setParamSet(paramSet).build());
        }
    }

    private static boolean isManaged(LootContextParamSet paramSet) {
        return paramSet == LootContextParamSets.CHEST || paramSet == LootContextParamSets.FISHING;
    }
}
