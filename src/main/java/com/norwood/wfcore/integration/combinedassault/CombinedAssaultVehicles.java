package com.norwood.wfcore.integration.combinedassault;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import com.wf.combinedassault.api.CombinedAssaultAPI;
import com.wf.combinedassault.custom.vehicle.BaseVehicleData;

/** CA side of {@link VehicleGate}; reached only through it. CA vehicles = data-driven {@link BaseVehicleData}, not entity types. */
final class CombinedAssaultVehicles {

    private CombinedAssaultVehicles() {}

    @Nullable
    private static BaseVehicleData<?> data(@Nullable ResourceLocation vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        return CombinedAssaultAPI.getVehicleDataManager().getVehicleData(vehicleId).orElse(null);
    }

    static boolean isKnown(@Nullable ResourceLocation vehicleId) {
        return data(vehicleId) != null;
    }

    static List<String> roster() {
        return CombinedAssaultAPI.getVehicleDataManager().getVehicleData().keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    @Nullable
    static Component name(@Nullable ResourceLocation vehicleId) {
        BaseVehicleData<?> data = data(vehicleId);
        return data == null ? null : data.getName();
    }

    @Nullable
    static AABB footprint(@Nullable ResourceLocation vehicleId, Vec3 at, float yRot, double height) {
        BaseVehicleData<?> data = data(vehicleId);
        return data == null ? null : data.footprint(at, yRot, height);
    }

    /** Display id = vehicle id; null display => null-payload display packet. */
    static boolean summon(Level level, Vec3 at, float yRot, ResourceLocation vehicleId) {
        return CombinedAssaultAPI.summon(level, at, yRot, vehicleId, vehicleId) != null;
    }
}
