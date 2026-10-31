package com.norwood.wfcore.integration.combinedassault;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VehicleOverridesTest {

    private static JsonObject info(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    private static JsonObject def(String energyType) {
        JsonObject def = new JsonObject();
        if (energyType != null) {
            def.add("energy_info", info("energy_type", energyType));
        }
        return def;
    }

    @Test
    void entriesAddedAfterConstructionPatchTheDef() {
        VehicleOverrides.add("combinedassault:humvee", info("fuel", "diesel"));
        JsonObject humvee = def("combustion");
        VehicleOverrides.apply(ResourceLocation.parse("combinedassault:humvee"), humvee);
        assertEquals("diesel", humvee.getAsJsonObject("energy_info").get("fuel").getAsString());
        JsonObject other = def("combustion");
        VehicleOverrides.apply(ResourceLocation.parse("combinedassault:m1a2"), other);
        assertFalse(other.getAsJsonObject("energy_info").has("fuel"));
    }

    @Test
    void allSkipsElectricAndRunsBeforePerVehicle() {
        VehicleOverrides.add(VehicleGate.ALL, info("tank", "all"));
        VehicleOverrides.add("combinedassault:mavic_t", info("tank", "own"));
        JsonObject electric = def("electric");
        VehicleOverrides.apply(ResourceLocation.parse("combinedassault:mavic"), electric);
        assertFalse(electric.getAsJsonObject("energy_info").has("tank"));
        JsonObject own = def("combustion");
        VehicleOverrides.apply(ResourceLocation.parse("combinedassault:mavic_t"), own);
        assertEquals("own", own.getAsJsonObject("energy_info").get("tank").getAsString());
        JsonObject plain = def(null);
        VehicleOverrides.apply(ResourceLocation.parse("combinedassault:humvee2"), plain);
        assertEquals("all", plain.getAsJsonObject("energy_info").get("tank").getAsString());
    }
}
