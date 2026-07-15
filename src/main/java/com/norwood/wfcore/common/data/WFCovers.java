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
    /**
     * Fan blade texture per tier, aligned index-for-index with {@link #FAN_TIERS}. Matches GregTech's
     * canonical hull material for that voltage: LV=Steel, MV=Aluminium, HV=Stainless Steel, EV=Titanium.
     */
    public static final String[] FAN_TEXTURES = { "steel_fan", "aluminium_fan", "stainless_fan", "titanium_fan" };
    public static final CoverDefinition[] COOLING_FANS = new CoverDefinition[GTValues.EV + 1];

    private WFCovers() {}

    public static void init() {
        if (COOLING_FANS[GTValues.LV] != null) return; // idempotent
        for (int i = 0; i < FAN_TIERS.length; i++) {
            final int t = FAN_TIERS[i];
            final String texture = FAN_TEXTURES[i];
            COOLING_FANS[t] = GTCovers.register(
                    WFCore.id("cooling_fan." + GTValues.VN[t].toLowerCase(Locale.ROOT)),
                    (def, coverable, side) -> new CoolingFanCover(def, coverable, side, t),
                    () -> () -> new SimpleCoverRenderer(WFCore.id("block/cover/" + texture)));
        }
    }
}
