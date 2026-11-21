package com.norwood.wfcore.common.data;


import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.integration.gt.GTIntegration;
import com.norwood.wfcore.common.item.BoltToolItem;
import com.norwood.wfcore.common.item.DetonatorItem;
import com.norwood.wfcore.common.item.PackagedVehicleItem;
import com.norwood.wfcore.common.item.SiegeDefenseTesterItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import java.util.Locale;


public class WFItems {

    public static ItemEntry<BoltToolItem> BOLT_TOOL;
    public static ItemEntry<DetonatorItem> DETONATOR;
    public static ItemEntry<PackagedVehicleItem> PACKAGED_VEHICLE;
    public static ItemEntry<SiegeDefenseTesterItem> SIEGE_DEFENSE_TESTER;

    /** GregTech only (null entries without it): the cooling fan covers, by GT voltage tier (GTValues has 15 tiers; 16 leaves room). */
    @SuppressWarnings("unchecked")
    public static final ItemEntry<com.gregtechceu.gtceu.api.item.ComponentItem>[] COOLING_FAN_COVERS = new ItemEntry[16];

    public static void init() {
        BOLT_TOOL = WFCore.REGISTRATE.item("bolt_tool", BoltToolItem::new)
                .lang("Bolt Tool")
                .model((ctx, prov) -> prov.generated(ctx, WFCore.id("item/bolt_tool")))
                .register();

        DETONATOR = WFCore.REGISTRATE.item("detonator", DetonatorItem::new)
                .lang("Detonator")
                .properties(p -> p.stacksTo(1))
                .model((ctx, prov) -> prov.generated(ctx, WFCore.id("item/detonator")))
                .register();

        PACKAGED_VEHICLE = WFCore.REGISTRATE.item("packaged_vehicle", PackagedVehicleItem::new)
                .lang("Packaged Vehicle")
                .model((ctx, prov) -> prov.generated(ctx, ResourceLocation.fromNamespaceAndPath("minecraft", "item/minecart")))
                .register();

        SIEGE_DEFENSE_TESTER = WFCore.REGISTRATE.item("siege_defense_tester", SiegeDefenseTesterItem::new)
                .lang("Siege Difficulty Probe")
                .model(NonNullBiConsumer.noop())
                .register();

        if (GTIntegration.isLoaded()) {
            WFItemsGT.init();
        }
    }

}
