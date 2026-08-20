package com.norwood.wfcore.integration.limitless;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.ywzj.vehicle.api.YwzjVehicleAPI;
import org.ywzj.vehicle.custom.vehicle.BaseVehicleData;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;

/**
 * The seam between WFCore's vehicle factories and Limitless Vehicle.
 *
 * <p>
 * Limitless vehicles are <em>not</em> entity types. They are data-driven {@link BaseVehicleData} entries
 * keyed by a vehicle id, all of which construct the same {@link AbstractVehicle} class, so the factories
 * resolve and size them through this class rather than through {@code BuiltInRegistries.ENTITY_TYPE}.
 */
public final class LimitlessVehicles {

    /** Fallback footprint edge, in blocks, for a vehicle whose model declares no size. */
    private static final double DEFAULT_SIZE = 3.0;

    private LimitlessVehicles() {}

    /** The vehicle data registered under {@code vehicleId}, or null if no such vehicle is loaded. */
    @Nullable
    public static BaseVehicleData<?> data(@Nullable ResourceLocation vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        return YwzjVehicleAPI.getVehicleDataManager().getVehicleData(vehicleId).orElse(null);
    }

    public static boolean isKnown(@Nullable ResourceLocation vehicleId) {
        return data(vehicleId) != null;
    }

    /** Every currently-loaded vehicle id, sorted. Empty before Limitless' reload listener has run. */
    public static List<String> roster() {
        return YwzjVehicleAPI.getVehicleDataManager().getVehicleData().keySet().stream()
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
     * The volume the vehicle would occupy if deployed at {@code pos}. Horizontally that is Limitless'
     * {@code structureLength} (the widest span of the model's collision boxes); the vertical extent is the
     * caller's, so the hard check can never exceed the "keep clear" box drawn for players.
     */
    public static AABB footprint(@Nullable BaseVehicleData<?> data, BlockPos pos, double height) {
        double size = data != null && data.getStructureLength() > 0 ? data.getStructureLength() : DEFAULT_SIZE;
        double half = size / 2.0;
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        return new AABB(x - half, pos.getY(), z - half, x + half, pos.getY() + height, z + half);
    }

    /**
     * Spawn {@code vehicleId} centred on {@code pos} facing {@code yRot}. The display defaults to the vehicle
     * id — {@code setDisplayId(null)} would broadcast a null-payload display packet.
     */
    @Nullable
    public static AbstractVehicle summon(Level level, BlockPos pos, float yRot, ResourceLocation vehicleId) {
        Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return YwzjVehicleAPI.summon(level, at, yRot, vehicleId, vehicleId);
    }
}
