package com.norwood.wfcore.integration.combinedassault;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import com.wf.combinedassault.vehicle.api.VehicleCatalogue;

/** CA side of {@link VehicleGate}; reached only through it. CA vehicles = pack data, not entity types. */
final class CombinedAssaultVehicles {

    private CombinedAssaultVehicles() {}

    static boolean isKnown(@Nullable ResourceLocation vehicleId) {
        return vehicleId != null && VehicleCatalogue.isKnown(vehicleId);
    }

    static List<String> roster() {
        return VehicleCatalogue.ids().stream().map(ResourceLocation::toString).toList();
    }

    @Nullable
    static Component name(@Nullable ResourceLocation vehicleId) {
        return vehicleId == null ? null : VehicleCatalogue.name(vehicleId).orElse(null);
    }

    @Nullable
    static AABB footprint(@Nullable ResourceLocation vehicleId, Vec3 at, float yRot, double height) {
        return vehicleId == null ? null : VehicleCatalogue.footprint(vehicleId, at, yRot, height).orElse(null);
    }

    static boolean summon(Level level, Vec3 at, float yRot, ResourceLocation vehicleId) {
        return VehicleCatalogue.summon(level, at, yRot, vehicleId);
    }
}
