package com.norwood.wfcore.integration.combinedassault;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.fml.ModList;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Only CA-free entry to CombinedAssault; CA classes load behind {@link #isLoaded()}. CA absent => no vehicle known. */
public final class VehicleGate {

    public static final String MOD_ID = "combinedassault";
    /** {@link #override} key: every vehicle not electric after its own def, applied before per-id overrides. */
    public static final String ALL = "*";
    /** Footprint edge, blocks, for an unknown vehicle. */
    private static final double DEFAULT_SIZE = 3.0;

    private static Boolean loaded;

    private VehicleGate() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    public static boolean isKnown(@Nullable ResourceLocation vehicleId) {
        return isLoaded() && CombinedAssaultVehicles.isKnown(vehicleId);
    }

    /** Sorted; empty before CA's reload listener has run. */
    public static List<String> roster() {
        return isLoaded() ? CombinedAssaultVehicles.roster() : List.of();
    }

    @Nullable
    public static Component name(@Nullable ResourceLocation vehicleId) {
        return isLoaded() ? CombinedAssaultVehicles.name(vehicleId) : null;
    }

    /** Volume {@code vehicleId} occupies at {@code pos} facing {@code yRot}; vertical extent = caller's {@code height}. */
    public static AABB footprint(@Nullable ResourceLocation vehicleId, BlockPos pos, float yRot, double height) {
        Vec3 at = Vec3.atBottomCenterOf(pos);
        AABB box = isLoaded() ? CombinedAssaultVehicles.footprint(vehicleId, at, yRot, height) : null;
        if (box != null) {
            return box;
        }
        double half = DEFAULT_SIZE / 2.0;
        return new AABB(at.x - half, at.y, at.z - half, at.x + half, at.y + height, at.z + half);
    }

    /** Spawn centred on {@code pos}; false = not spawned. */
    public static boolean summon(Level level, BlockPos pos, float yRot, ResourceLocation vehicleId) {
        return isLoaded() && CombinedAssaultVehicles.summon(level, Vec3.atBottomCenterOf(pos), yRot, vehicleId);
    }

    /** Pack {@code energy_info} merge, per content load. CA absent => dropped. */
    public static void override(String vehicle, JsonObject energyInfo) {
        if (isLoaded()) {
            VehicleOverrides.add(vehicle, energyInfo);
        }
    }
}
