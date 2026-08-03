package com.norwood.wfcore.integration.superbwarfare;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.SuperbOverrides;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Prevents items matching {@link SuperbOverrides#VEHICLE_STORAGE_BLACKLIST} from being auto-absorbed
 * into a vehicle's inventory when the vehicle kills a mob.
 *
 * <p>SBW's {@code vehicleCollectDrops} runs at {@link EventPriority#NORMAL} and inserts mob drops
 * directly into {@code vehicle.getItemStacks()}, bypassing {@code canPlaceItem}. We bracket it:
 * the HIGHEST-priority handler pulls blacklisted drops off the list before SBW sees them; the
 * LOWEST-priority handler puts them back so they still fall on the ground.
 *
 * <p>Both handlers run on the server thread synchronously, so {@link #held} needs no concurrency guard.
 * The list is cleared at the top of each HIGHEST call to handle any edge-case leftover from a prior cycle.
 */
public final class VehicleStorageBlacklistFilter {

    public static final VehicleStorageBlacklistFilter INSTANCE = new VehicleStorageBlacklistFilter();

    private VehicleStorageBlacklistFilter() {}

    private final List<ItemEntity> held = new ArrayList<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void holdBlacklistedDrops(LivingDropsEvent event) {
        held.clear();
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!(player.getVehicle() instanceof VehicleEntity)) return;

        var it = event.getDrops().iterator();
        while (it.hasNext()) {
            var ie = it.next();
            if (ie.getItem().is(SuperbOverrides.VEHICLE_STORAGE_BLACKLIST)) {
                it.remove();
                held.add(ie);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void restoreBlacklistedDrops(LivingDropsEvent event) {
        if (!held.isEmpty()) {
            event.getDrops().addAll(held);
            held.clear();
        }
    }
}
