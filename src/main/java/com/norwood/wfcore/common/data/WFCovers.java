package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.client.renderer.cover.SimpleCoverRenderer;
import com.gregtechceu.gtceu.common.data.GTCovers;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.cover.CoolingFanCover;

import java.util.Locale;

/**
 * WFCore covers. The cooling-fan cover is registered one definition per voltage tier (LV..EV), mirroring how
 * GregTech registers conveyors/pumps; the tier is baked into each definition.
 *
 * <p>
 * Registered at class-load (triggered from {@link WFItems} during mod construction, before
 * {@code GTCovers.init()} freezes the cover registry at common setup).
 */
public final class WFCovers {

    public static final int[] FAN_TIERS = { GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV };
    public static final CoverDefinition[] COOLING_FANS = new CoverDefinition[GTValues.EV + 1];

    private WFCovers() {}

    public static void init() {
        if (COOLING_FANS[GTValues.LV] != null) return; // idempotent
        for (int tier : FAN_TIERS) {
            final int t = tier;
            COOLING_FANS[t] = GTCovers.register(
                    WFCore.id("cooling_fan." + GTValues.VN[t].toLowerCase(Locale.ROOT)),
                    (def, coverable, side) -> new CoolingFanCover(def, coverable, side, t),
                    () -> () -> new SimpleCoverRenderer(WFCore.id("block/cover/cooling_fan")));
        }
    }
}
