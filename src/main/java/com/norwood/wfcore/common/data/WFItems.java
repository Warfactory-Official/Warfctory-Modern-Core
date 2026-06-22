package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;

import com.norwood.wfcore.common.item.BoltToolItem;
import com.norwood.wfcore.common.item.PackagedVehicleItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import java.util.Locale;

import static com.norwood.wfcore.WFCore.EXAMPLE_REGISTRATE;

public class WFItems {

    public static ItemEntry<BoltToolItem> BOLT_TOOL;
    public static ItemEntry<PackagedVehicleItem> PACKAGED_VEHICLE;

    @SuppressWarnings("unchecked")
    public static final ItemEntry<ComponentItem>[] COOLING_FAN_COVERS = new ItemEntry[GTValues.EV + 1];

    public static void init() {
        BOLT_TOOL = EXAMPLE_REGISTRATE.item("bolt_tool", BoltToolItem::new)
                .lang("Bolt Tool")
                .register();

        PACKAGED_VEHICLE = EXAMPLE_REGISTRATE.item("packaged_vehicle", PackagedVehicleItem::new)
                .lang("Packaged Vehicle")
                .register();

        // Cooling-fan cover placer items (one per tier). WFCovers.init() must run first so the cover
        // definitions exist when CoverPlaceBehavior captures them.
        WFCovers.init();
        for (int tier : WFCovers.FAN_TIERS) {
            final int t = tier;
            String vn = GTValues.VN[t].toLowerCase(Locale.ROOT);
            COOLING_FAN_COVERS[t] = EXAMPLE_REGISTRATE
                    .item("cooling_fan_cover_" + vn, ComponentItem::create)
                    .lang(GTValues.VN[t] + " Cooling Fan Cover")
                    .model(NonNullBiConsumer.noop())
                    .onRegister(GTItems.attach(new CoverPlaceBehavior(WFCovers.COOLING_FANS[t])))
                    .register();
        }
    }
}
