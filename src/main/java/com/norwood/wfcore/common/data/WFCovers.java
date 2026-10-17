package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.client.renderer.cover.SimpleCoverRenderer;

import net.minecraft.core.Holder;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.cover.CoolingFanCover;

import java.util.Locale;

/**
 * WFCore covers. The cooling-fan cover is registered one definition per voltage tier (LV..EV), mirroring how
 * GregTech registers conveyors/pumps; the tier is baked into each definition.
 */
public final class WFCovers {

    public static final int[] FAN_TIERS = { GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV };
    /**
     * Fan blade texture per tier, aligned index-for-index with {@link #FAN_TIERS}. Matches GregTech's
     * canonical hull material for that voltage: LV=Steel, MV=Aluminium, HV=Stainless Steel, EV=Titanium.
     */
    public static final String[] FAN_TEXTURES = { "steel_fan", "aluminium_fan", "stainless_fan", "titanium_fan" };
    @SuppressWarnings("unchecked")
    public static final Holder<CoverDefinition>[] COOLING_FANS = new Holder[GTValues.EV + 1];

    private WFCovers() {}

    public static void init() {
        if (COOLING_FANS[GTValues.LV] != null) return; // idempotent
        for (int i = 0; i < FAN_TIERS.length; i++) {
            final int t = FAN_TIERS[i];
            final String texture = FAN_TEXTURES[i];
            COOLING_FANS[t] = WFCore.WF_MACHINES.cover(
                    "cooling_fan." + GTValues.VN[t].toLowerCase(Locale.ROOT),
                    (def, coverable, side) -> new CoolingFanCover(def, coverable, side, t),
                    () -> () -> new SimpleCoverRenderer(WFCore.id("block/cover/" + texture)));
        }
    }
}
