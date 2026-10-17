package com.norwood.wfcore.integration.combinedassault;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import com.wf.combinedassault.api.CombinedAssaultAPI;
import com.wf.combinedassault.custom.vehicle.BaseVehicleData;
import com.wf.combinedassault.entity.vehicle.AbstractVehicle;

/**
 * The seam between WFCore's vehicle factories and CombinedAssault.
 *
 * <p>
 * CombinedAssault vehicles are <em>not</em> entity types. They are data-driven {@link BaseVehicleData} entries
 * keyed by a vehicle id, all of which construct the same {@link AbstractVehicle} class, so the factories
 * resolve and size them through this class rather than through {@code BuiltInRegistries.ENTITY_TYPE}.
 */
public final class CombinedAssaultVehicles {

    /** Footprint edge, in blocks, for a vehicle that isn't loaded. */
    private static final double DEFAULT_SIZE = 3.0;

    private CombinedAssaultVehicles() {}

    /** The vehicle data registered under {@code vehicleId}, or null if no such vehicle is loaded. */
    @Nullable
    public static BaseVehicleData<?> data(@Nullable ResourceLocation vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        return CombinedAssaultAPI.getVehicleDataManager().getVehicleData(vehicleId).orElse(null);
    }

    public static boolean isKnown(@Nullable ResourceLocation vehicleId) {
        return data(vehicleId) != null;
    }

    /** Every currently-loaded vehicle id, sorted. Empty before CombinedAssault's reload listener has run. */
    public static List<String> roster() {
        return CombinedAssaultAPI.getVehicleDataManager().getVehicleData().keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    /** The vehicle's display name, or null if it isn't loaded. */
    @Nullable
    public static net.minecraft.network.chat.Component name(@Nullable ResourceLocation vehicleId) {
        BaseVehicleData<?> data = data(vehicleId);
        return data == null ? null : data.getName();
    }

    /**
     * The volume the vehicle would occupy if deployed at {@code pos} facing {@code yRot}: its collider extents
     * horizontally; the vertical extent is the caller's, so the hard check can never exceed the "keep clear"
     * box drawn for players.
     */
    public static AABB footprint(@Nullable BaseVehicleData<?> data, BlockPos pos, float yRot, double height) {
        Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (data == null) {
            double half = DEFAULT_SIZE / 2.0;
            return new AABB(at.x - half, at.y, at.z - half, at.x + half, at.y + height, at.z + half);
        }
        return data.footprint(at, yRot, height);
    }

    /**
     * Spawn {@code vehicleId} centred on {@code pos} facing {@code yRot}. The display defaults to the vehicle
     * id — {@code setDisplayId(null)} would broadcast a null-payload display packet.
     */
    @Nullable
    public static AbstractVehicle summon(Level level, BlockPos pos, float yRot, ResourceLocation vehicleId) {
        Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return CombinedAssaultAPI.summon(level, at, yRot, vehicleId, vehicleId);
    }
}
