package com.norwood.wfcore.integration.combinedassault;

import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonObject;
import com.wf.combinedassault.api.CombinedAssaultAPI;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pack edits to CombinedAssault vehicle {@code energy_info}, applied to the def JSON on every content load.
 * {@code "*"} = every vehicle not electric after its own def, applied before per-id overrides.
 */
public final class VehicleOverrides {

    public static final String ALL = "*";

    private record Entry(String vehicle, JsonObject energyInfo) {}

    private static final List<Entry> OVERRIDES = new CopyOnWriteArrayList<>();

    static {
        CombinedAssaultAPI.patchVehicleDefs(VehicleOverrides::apply);
    }

    private VehicleOverrides() {}

    public static void add(String vehicle, JsonObject energyInfo) {
        OVERRIDES.add(new Entry(vehicle, energyInfo));
    }

    private static void apply(ResourceLocation id, JsonObject def) {
        String key = id.toString();
        for (Entry o : OVERRIDES) {
            if (o.vehicle.equals(ALL) && !isElectric(def)) {
                merge(def, o.energyInfo);
            }
        }
        for (Entry o : OVERRIDES) {
            if (o.vehicle.equals(key)) {
                merge(def, o.energyInfo);
            }
        }
    }

    private static boolean isElectric(JsonObject def) {
        JsonObject info = def.getAsJsonObject("energy_info");
        return info != null && info.has("energy_type") && "electric".equals(info.get("energy_type").getAsString());
    }

    private static void merge(JsonObject def, JsonObject patch) {
        JsonObject info = def.getAsJsonObject("energy_info");
        if (info == null) {
            info = new JsonObject();
            def.add("energy_info", info);
        }
        for (var e : patch.entrySet()) {
            if (e.getValue().isJsonNull()) {
                info.remove(e.getKey());
            } else {
                info.add(e.getKey(), e.getValue().deepCopy());
            }
        }
    }
}
