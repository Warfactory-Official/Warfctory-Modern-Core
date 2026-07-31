package com.norwood.wfcore.integration.superbwarfare;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.SuperbOverrides;
import com.norwood.wfcore.WFCore;

import java.util.Map;

/**
 * Gives the Drone Warfare add-on's FPV and LUCAS drones a small upgrade bay, reusing Superb Warfare's vehicle
 * storage infrastructure. Both drones extend {@code VehicleEntity} and don't override its storage methods, so a
 * WFCore storage override ({@link SuperbOverrides}) gives them a {@value #SLOTS}-slot {@code VehicleContainerHandler}
 * plus the WFCore ModularUI for free (see {@link com.norwood.wfcore.mixin.SuperbWarfareInvMixin}). The
 * {@link #DRONE_UPGRADES} item tag restricts those slots to drone upgrade items only.
 *
 * <p>
 * Drones handle their own {@code interact} (monitor-link / crowbar-pickup / refuel) and never open a container,
 * so {@link #onEntityInteract} opens the bay on a sneak + empty-hand interaction — a gesture the drones don't
 * otherwise use, so their normal interactions are left untouched.
 */
public final class DroneUpgradeBay {

    public static final DroneUpgradeBay INSTANCE = new DroneUpgradeBay();

    /** The Drone Warfare add-on's mod id (shipped as a runtime dependency of the pack; compileOnly here). */
    private static final String DRONE_MOD_ID = "sbwdroneconfig";
    private static final String FPV_DRONE = DRONE_MOD_ID + ":cubed_fpv_drone";
    private static final String LUCAS_DRONE = DRONE_MOD_ID + ":lucas_drone";
    private static final int SLOTS = 6;


    private static final int LUCAS_FUEL_CAPACITY = 4000;
    private static final Map<ResourceLocation, Float> LUCAS_FUELS = Map.of(
            new ResourceLocation("gtceu", "gasoline"), 1.5f,
            new ResourceLocation("gtceu", "high_octane_gasoline"), 2.0f,
            new ResourceLocation("gtceu", "diesel"), 1.0f,
            new ResourceLocation("gtceu", "bio_diesel"), 1.0f,
            new ResourceLocation("gtceu", "light_fuel"), 0.8f);

    /** Items a drone upgrade bay accepts (see {@code data/wfcore/tags/items/drone_upgrades.json}). */
    public static final TagKey<Item> DRONE_UPGRADES = TagKey.create(Registries.ITEM, WFCore.id("drone_upgrades"));

    private DroneUpgradeBay() {}

    /**
     * Register the {@value #SLOTS}-slot, upgrades-only storage override for both drones. No-op if the Drone
     * Warfare add-on is not loaded (the entity types then don't exist, and the tag entries are optional).
     */
    public static void registerOverrides() {
        if (!ModList.get().isLoaded(DRONE_MOD_ID)) {
            return;
        }
        SuperbOverrides.registerOverride(FPV_DRONE,
                new SuperbOverrides.OverrideData(0, Map.of(), SLOTS, SLOTS, DRONE_UPGRADES));
        SuperbOverrides.registerOverride(LUCAS_DRONE,
                new SuperbOverrides.OverrideData(LUCAS_FUEL_CAPACITY, LUCAS_FUELS, SLOTS, SLOTS, null));
        WFCore.LOGGER.info("Registered drone bays: FPV (energy, upgrades-only), LUCAS (fuel override, open bay)");
    }

    /**
     * Open the upgrade bay when a player sneak + empty-hand right-clicks a drone. Fires before the entity's own
     * {@code interact}; cancelling only the sneak+empty-hand gesture keeps every other drone interaction intact.
     * The actual screen open is delegated to {@code VehicleEntity.openMenu}, which the WFCore mixin redirects to
     * the ModularUI storage screen for configured vehicles.
     */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        if (!player.isShiftKeyDown() || !event.getItemStack().isEmpty()) {
            return;
        }
        Entity target = event.getTarget();
        if (!(target instanceof VehicleEntity vehicle)) {
            return;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        String id = key == null ? null : key.toString();
        if (!FPV_DRONE.equals(id) && !LUCAS_DRONE.equals(id)) {
            return;
        }
        if (!player.level().isClientSide) {
            vehicle.openMenu(player);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
    }
}
