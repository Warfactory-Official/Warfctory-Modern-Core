package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;

import net.minecraft.resources.ResourceLocation;

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
        // No bespoke art yet: reuse existing textures as placeholders so datagen produces a valid model
        // (a wrench for the casing bolt tool, a minecart for the packaged vehicle).
        BOLT_TOOL = EXAMPLE_REGISTRATE.item("bolt_tool", BoltToolItem::new)
                .lang("Bolt Tool")
                .model((ctx, prov) -> prov.generated(ctx, GTCEu.id("item/tools/wrench")))
                .register();

        PACKAGED_VEHICLE = EXAMPLE_REGISTRATE.item("packaged_vehicle", PackagedVehicleItem::new)
                .lang("Packaged Vehicle")
                .model((ctx, prov) -> prov.generated(ctx, new ResourceLocation("minecraft", "item/minecart")))
                .register();

        // Cooling-fan cover placer items (one per tier). The cover definitions are registered earlier
        // via WfCoreAddon.registerCovers() (during gtceu's constructor, while the cover registry is
        // unfrozen), so WFCovers.COOLING_FANS is already populated here for CoverPlaceBehavior.
        for (int tier : WFCovers.FAN_TIERS) {
            final int t = tier;
            String vn = GTValues.VN[t].toLowerCase(Locale.ROOT);
            COOLING_FAN_COVERS[t] = EXAMPLE_REGISTRATE
                    .item("cooling_fan_cover_" + vn, ComponentItem::create)
                    .lang(GTValues.VN[t] + " Cooling Fan Cover")
                    .model(NonNullBiConsumer.noop())
                    // Inline GTItems.attach(...) so we don't reference GTItems here: touching that class
                    // during construction triggers its <clinit>, which builds material-backed items before
                    // materials are registered (NPE). The lambda runs at the item RegisterEvent instead.
                    .onRegister(item -> item.attachComponents(new CoverPlaceBehavior(WFCovers.COOLING_FANS[t])))
                    .register();
        }
    }
}
