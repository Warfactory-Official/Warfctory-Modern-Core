package com.norwood.wfcore.antistall;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;



public final class AircraftAntiStall {

    /** Aircraft currently being held, keyed by vehicle UUID. Server thread only. */
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();

    /** Remaining crash-damage grace ticks per vehicle UUID. Server thread only. */
    private static final Map<UUID, Integer> GRACE = new HashMap<>();

    /** Scratch set reused each tick to expire holds for vehicles that are gone. */
    private static final Set<UUID> TOUCHED = new HashSet<>();

    private AircraftAntiStall() {
    }

    private static final class Hold {
        double targetAltitude;
        int engagedTicks;
    }

    /** Called once per server tick from {@link AntiStallHandler}. */
    public static void tick(MinecraftServer server) {
        ageGrace();
        TOUCHED.clear();

        final int staleTicks = WFCoreConfig.getAntiStallStaleTicks();
        final int graceTicks = WFCoreConfig.getAntiStallGraceTicks();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Poll every player, not just pilots — this is what drains PilotLink's concurrent set.
            int silent = PilotLink.pollSilentTicks(player.getUUID());

            VehicleEntity aircraft = pilotedAircraft(player);
            if (aircraft == null) {
                continue;
            }

            UUID key = aircraft.getUUID();
            TOUCHED.add(key);

            if (silent < staleTicks) {
                if (HOLDS.remove(key) != null) {
                    WFCore.LOGGER.debug("Anti-stall released {} — {} regained link",
                            key, player.getGameProfile().getName());
                }
                continue;
            }

            Hold hold = HOLDS.get(key);
            if (hold == null) {
                hold = new Hold();
                hold.targetAltitude = aircraft.getY();
                HOLDS.put(key, hold);
                WFCore.LOGGER.debug("Anti-stall engaged for {} ({} silent {}t) holding y={}",
                        key, player.getGameProfile().getName(), silent, hold.targetAltitude);
            }
            hold.engagedTicks++;
            GRACE.put(key, graceTicks);
            apply(aircraft, hold);
        }

        // Drop holds for aircraft that no longer have a stalled pilot aboard (destroyed, dismounted,
        // disconnected). GRACE expires on its own timer so a rejoining pilot keeps the damage shield.
        HOLDS.keySet().retainAll(TOUCHED);
    }

    /**
     * @return the aircraft this player is piloting and that is eligible for holding, or {@code null}
     */
    private static VehicleEntity pilotedAircraft(ServerPlayer player) {
        Entity ridden = player.getVehicle();
        if (!(ridden instanceof VehicleEntity vehicle)) {
            return null;
        }
        if (vehicle.getVehicleType() != VehicleType.AIRPLANE) {
            return null;
        }
        // Only the pilot's link matters; a gunner going silent must not take the aircraft over.
        if (vehicle.getFirstPassenger() != player) {
            return null;
        }
        if (vehicle.isWreck() || vehicle.onGround()) {
            return null;
        }
        return vehicle;
    }

    /** Drives the two inputs {@code aircraftEngine} reads, plus a throttle floor. */
    private static void apply(VehicleEntity aircraft, Hold hold) {
        double target = hold.targetAltitude;

        // Never hold an altitude that flies us into rising terrain.
        double ground = groundHeight(aircraft);
        if (!Double.isNaN(ground)) {
            target = Math.max(target, ground + WFCoreConfig.getAntiStallMinClearance());
        }

        double altitudeError = target - aircraft.getY();       // > 0 => below target => want nose up
        double verticalRate = aircraft.getDeltaMovement().y;   // > 0 => climbing => damp with nose down

        float pitch = (float) (-altitudeError * WFCoreConfig.getAntiStallPitchGain()
                + verticalRate * WFCoreConfig.getAntiStallVerticalDamping());

        aircraft.setMouseMoveSpeedY(Mth.clamp(pitch, -1.0f, 1.0f));
        aircraft.setMouseMoveSpeedX(0.0f);

        float powerFloor = (float) WFCoreConfig.getAntiStallPowerFloor();
        if (aircraft.getPower() < powerFloor) {
            aircraft.setPower(Mth.lerp(0.05f, aircraft.getPower(), powerFloor));
        }
    }


    private static double groundHeight(VehicleEntity aircraft) {
        Level level = aircraft.level();
        int blockX = aircraft.getBlockX();
        int blockZ = aircraft.getBlockZ();
        if (!level.hasChunk(blockX >> 4, blockZ >> 4)) {
            return Double.NaN;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
    }

    private static void ageGrace() {
        Iterator<Map.Entry<UUID, Integer>> it = GRACE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            if (left <= 0) {
                it.remove();
            } else {
                entry.setValue(left);
            }
        }
    }


    public static boolean inCrashGrace(VehicleEntity vehicle) {
        return WFCoreConfig.isAntiStallEnabled() && GRACE.containsKey(vehicle.getUUID());
    }

    /** True while the autopilot is actively flying this aircraft. */
    public static boolean isHolding(VehicleEntity vehicle) {
        return HOLDS.containsKey(vehicle.getUUID());
    }

    public static void reset() {
        HOLDS.clear();
        GRACE.clear();
        TOUCHED.clear();
    }
}
