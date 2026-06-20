package com.norwood.wfcore.common.fluid;

import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of fluids usable as coolant by the Large Transformer and computation mainframe. Cooler coolants
 * carry more EU per millibucket; {@code hotVariant} (optional) is the fluid produced when actively cooling.
 */
public final class CoolantRegistry {

    private static final Reference2ObjectOpenHashMap<Fluid, CoolantSettings> COOLANTS = new Reference2ObjectOpenHashMap<>();

    public record CoolantSettings(@Nullable Fluid hotVariant, double heatCapacity) {}

    private CoolantRegistry() {}

    public static void register() {
        register(Fluids.WATER, null, 1.0);
        register(GTMaterials.Oxygen.getFluid(), null, 3.0);
        register(GTMaterials.Helium.getFluid(), null, 6.0);
        register(GTMaterials.Nitrogen.getFluid(), null, 10.0);
    }

    public static void register(Fluid cold, @Nullable Fluid hot, double capacity) {
        if (cold == null) return;
        COOLANTS.put(cold, new CoolantSettings(hot, capacity));
    }

    @Nullable
    public static CoolantSettings get(Fluid fluid) {
        if (fluid == null) return null;
        return COOLANTS.get(fluid);
    }

    public static boolean isCoolant(Fluid fluid) {
        return COOLANTS.containsKey(fluid);
    }
}
