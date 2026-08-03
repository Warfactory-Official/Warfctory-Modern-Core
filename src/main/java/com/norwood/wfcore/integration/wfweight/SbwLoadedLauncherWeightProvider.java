package com.norwood.wfcore.integration.wfweight;

import com.warfactory.ultimateweight.api.IWeightCompatProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalDouble;

/**
 * Reports the combined (launcher + missile) weight for the IGLA-9K38 and FGM-148 Javelin
 * when they have a missile loaded. SBW stores the loaded missile count in the item NBT at
 * {@code GunData.Ammo}; 0 means empty, ≥1 means loaded.
 *
 * <p>Empty-launcher weights live in weight_config_modern.yaml (igla_9k38=7.1, javelin=6.4).
 * This provider only fires for the loaded case, returning the combined real-world weight.</p>
 */
public final class SbwLoadedLauncherWeightProvider implements IWeightCompatProvider {

    private static final String IGLA_ID    = "superbwarfare:igla_9k38";
    private static final String JAVELIN_ID = "superbwarfare:javelin";

    /** Loaded IGLA: 7.1 kg (launcher) + 10.8 kg (9M39 missile) = 17.9 kg */
    private static final double IGLA_LOADED_KG    = 17.9;
    /** Loaded Javelin: 6.4 kg (CLU+tube) + 15.9 kg (missile) = 22.3 kg */
    private static final double JAVELIN_LOADED_KG = 22.3;

    /** NBT sub-compound key written by SBW's GunData for all gun state. */
    private static final String GUN_DATA_KEY = "GunData";
    /** Integer key within GunData for the current chamber/barrel ammo count. */
    private static final String AMMO_KEY     = "Ammo";

    @Override
    public OptionalDouble getUnitWeight(Object rawStack) {
        if (!(rawStack instanceof ItemStack stack) || stack.isEmpty()) {
            return OptionalDouble.empty();
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return OptionalDouble.empty();
        }

        String idStr = id.toString();
        if (!IGLA_ID.equals(idStr) && !JAVELIN_ID.equals(idStr)) {
            return OptionalDouble.empty();
        }

        CompoundTag rootTag = stack.getTag();
        if (rootTag == null) {
            return OptionalDouble.empty();
        }

        int ammo = rootTag.getCompound(GUN_DATA_KEY).getInt(AMMO_KEY);
        if (ammo <= 0) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(IGLA_ID.equals(idStr) ? IGLA_LOADED_KG : JAVELIN_LOADED_KG);
    }

    @Override
    public int getPriority() {
        return 200;
    }
}
