package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.item.behavior.CoverPlaceBehavior;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import java.util.Locale;

import static com.norwood.wfcore.integration.gt.GTBootstrap.WF_MACHINES;

/** The items that need GregTech: its covers. Registered only when GregTech is loaded ({@link WFItems#init}). */
final class WFItemsGT {
    private WFItemsGT() {
    }

    static void init() {
        for (int tier : WFCovers.FAN_TIERS) {
            final int t = tier;
            String vn = GTValues.VN[t].toLowerCase(Locale.ROOT);
            WFItems.COOLING_FAN_COVERS[t] = WF_MACHINES
                    .item("cooling_fan_cover_" + vn, ComponentItem::new)
                    .lang(GTValues.VN[t] + " Cooling Fan Cover")
                    .model(NonNullBiConsumer.noop())
                    .onRegister(item -> item.attachComponents(new CoverPlaceBehavior(WFCovers.COOLING_FANS[t])))
                    .register();
        }
    }
}
